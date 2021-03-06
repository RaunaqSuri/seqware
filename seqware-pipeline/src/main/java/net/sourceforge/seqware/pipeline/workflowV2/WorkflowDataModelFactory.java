package net.sourceforge.seqware.pipeline.workflowV2;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.SortedSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import joptsimple.OptionSet;
import net.sourceforge.seqware.common.metadata.Metadata;
import net.sourceforge.seqware.common.model.WorkflowParam;
import net.sourceforge.seqware.common.model.WorkflowRun;
import net.sourceforge.seqware.common.module.ReturnValue;
import net.sourceforge.seqware.common.util.Log;
import net.sourceforge.seqware.common.util.Rethrow;
import net.sourceforge.seqware.common.util.maptools.MapTools;
import net.sourceforge.seqware.common.util.workflowtools.WorkflowInfo;
import net.sourceforge.seqware.pipeline.bundle.Bundle;
import net.sourceforge.seqware.pipeline.workflow.BasicWorkflow;
import net.sourceforge.seqware.pipeline.workflowV2.engine.pegasus.StringUtils;
import net.sourceforge.seqware.pipeline.workflowV2.model.XmlWorkflowDataModel;

/**
 * a utils class for creating the AbstractWorkflowDataModel, by reading the
 * metadata.xml file, will load a Java based objectModel or XML based
 * ObjectModel
 *
 * @author yliang
 *
 */
public class WorkflowDataModelFactory {

    private Map<String, String> config;
    private OptionSet options;
    private String[] params;
    private Metadata metadata;

    public WorkflowDataModelFactory(OptionSet options, Map<String, String> config, String[] params, Metadata metadata) {
        this.options = options;
        this.config = config;
        this.params = params;
        this.metadata = metadata;

        // need to do options ret
    }

    /**
     * a simple method to replace the ${workflow_bundle_dir} variable (copied
     * from BasicWorkflow)
     *
     * @param input
     * @param wbd
     * @return
     */
    private String replaceWBD(String input, String wbd) {
      if (input != null){
        return (input.replaceAll("\\$\\{"+MapTools.VAR_BUNDLE_DIR+"\\}", wbd)
                     .replaceAll("\\$\\{"+MapTools.LEGACY_VAR_BUNDLE_DIR+"\\}", wbd));
      } else {
        return null;
      }
    }

    /**
     * load metadata.xml, if FTL, parse the FTL to XML, and translate it to Java
     * based Object if Java, load the class.
     *
     * @param workflowAccession if this is present, we grab metadata information
     * from the database, not the options
     * @return
     */
    public AbstractWorkflowDataModel getWorkflowDataModel(Integer workflowAccession, Integer workflowRunAccession) {
        String bundlePath = null;
        Map<String, String> metaInfo = null;
        Log.info("factory attempting to find bundle");
        if (workflowAccession != null) {
            Log.info("factory attempting to find bundle from DB");
            // this execution path is hacked in for running from the database and can be refactored into BasicWorkflow
            metaInfo = this.metadata.get_workflow_info(workflowAccession);
            //we've found out the bundle location as of this point
            //we need to grab the current_working_dir out
            //use it to follow the same method determining a bundle path like below, the WorkflowV2Utility.parseMetaInfo does the substitution instead of BasicWorkflow in
            //Yong's code
            bundlePath = metaInfo.get("current_working_dir");
        } else {
            Log.info("factory attempting to find bundle from options");
            bundlePath = WorkflowV2Utility.determineRelativeBundlePath(options);
        }
        File bundle = new File(bundlePath);
        //change to absolute path
        bundlePath = bundle.getAbsolutePath();
        Log.info("Bundle Path: " + bundlePath);
        if (bundle == null || !bundle.exists()) {

            // then first try to see if we can get it from it's permenant location instead
            if (metaInfo.get("permanent_bundle_location") != null) {
                bundle = new File(getAndProvisionBundle(metaInfo.get("permanent_bundle_location")));
            }
            // if we still can't get the bundle then error out
            if (bundle == null || !bundle.exists()) {
                Log.error("ERROR: Bundle is null or doesn't exist! The bundle must be either a zip file or a directory structure.");
                return null;
            }
        }

        metaInfo = WorkflowV2Utility.parseMetaInfo(bundle);
        if (metaInfo == null) {
            Log.error("ERROR: Bundle structure is incorrect, unable to parse metadata.");
            return null;
        }
        Log.info("bundle for workflowdatamodel found");

        //check FTL exist?
        boolean workflow_java = true;
        if (metaInfo.get("workflow_template") != null && !metaInfo.get("workflow_template").toString().isEmpty()) {
            workflow_java = false;
        }


        //Java object or FTL
        AbstractWorkflowDataModel dataModel = null;
        Class<?> clazz = null;
        if (workflow_java) {
//            String clazzPath = metaInfo.get("classes");
//            Log.stdout("looking for classes at " + clazzPath);
//            Log.info("CLASSPATH: " + clazzPath);
//            // get user defined classes
            String classpath = metaInfo.get("workflow_class");
            Log.debug("Attempting to instantiate " + classpath);
            WorkflowClassFinder finder = new WorkflowClassFinder();
            clazz = finder.findFirstWorkflowClass(classpath);

            if (null != clazz) {
                Log.debug("using java object");
                try {
                    Object object = clazz.newInstance();
                    dataModel = (AbstractWorkflowDataModel) object;
                } catch (InstantiationException ex) {
                    Log.error(ex, ex);
                } catch (IllegalAccessException ex) {
                    Log.error(ex, ex);
                } catch (SecurityException ex) {
                    Log.error(ex, ex);
                } catch (IllegalArgumentException ex) {
                    Log.error(ex, ex);
                }
            } else {
                Log.stdout("failed looking for classes at " + classpath);
            }
        } else {
            dataModel = new XmlWorkflowDataModel();
        }
        Log.info("datamodel generated");
        //load metadata.xml
        dataModel.setTags(metaInfo);
        //set name, version in workflow
        dataModel.setName(metaInfo.get("name"));
        dataModel.setVersion(metaInfo.get("workflow_version"));
        dataModel.setBundle_version(metaInfo.get("bundle_version"));
        dataModel.setSeqware_version(metaInfo.get("seqware_version"));
        dataModel.setWorkflow_directory_name(metaInfo.get("workflow_directory_name"));
        dataModel.setWorkflowBundleDir(bundlePath);
        dataModel.setWorkflowBasedir(metaInfo.get("basedir"));
        //set memory, network, compute to environment
        dataModel.getEnv().setCompute(metaInfo.get("compute"));
        dataModel.getEnv().setNetwork(metaInfo.get("network"));
        dataModel.getEnv().setMemory(metaInfo.get("memory"));

        Log.info("loading ini files");
        //load ini config
        Map<String, String> configs = this.loadIniConfigs(workflowAccession, workflowRunAccession, bundlePath);
        dataModel.setConfigs(configs);

        // 0.13.6.5 : The Java workflow launcher was not originally designed to schedule, hence it is not properly getting 
        // parent accessions from saved ini files (as opposed to on the command line) 
        ArrayList<String> parseParentAccessions = BasicWorkflow.parseParentAccessions(configs);
        dataModel.setParentAccessions(parseParentAccessions);

        //merge command line option with configs, command-line options should override parent accession set above if present
        this.mergeCmdOptions(dataModel);
        //merge version, and name ??? TODO 

        //set random, date, wait
        // magic variables always set
        Date date = new Date();
        dataModel.setDate(date.toString());

        //set random
        Random rand = new Random(System.currentTimeMillis());
        int randInt = rand.nextInt(100000000);
        dataModel.setRandom("" + randInt);
        //copy some properties from .settings to configs
        dataModel.getEnv().setPegasusConfigDir(config.get("SW_PEGASUS_CONFIG_DIR"));
        dataModel.getEnv().setDaxDir(config.get("SW_DAX_DIR"));
        dataModel.getEnv().setSwCluster(config.get("SW_CLUSTER"));
        dataModel.getEnv().setOOZIE_URL(config.get("OOZIE_URL"));
        dataModel.getEnv().setOOZIE_APP_ROOT(config.get("OOZIE_APP_ROOT"));
        dataModel.getEnv().setOOZIE_JOBTRACKER(config.get("OOZIE_JOBTRACKER"));
        dataModel.getEnv().setOOZIE_NAMENODE(config.get("OOZIE_NAMENODE"));
        dataModel.getEnv().setOOZIE_QUEUENAME(config.get("OOZIE_QUEUENAME"));
        dataModel.getEnv().setHbase_master(config.get("HBASE.MASTER"));
        dataModel.getEnv().setHbase_zookeeper_quorum(config.get("HBASE.ZOOKEEPER.QUORUM"));
        dataModel.getEnv().setHbase_zookeeper_property_clientPort(config.get("HBASE.ZOOKEEPER.PROPERTY.CLIENTPORT"));
        dataModel.getEnv().setMapred_job_tracker(config.get("MAPRED.JOB.TRACKER"));
        dataModel.getEnv().setFs_default_name(config.get("FS.DEFAULT.NAME"));
        dataModel.getEnv().setFs_defaultFS(config.get("FS.DEFAULTFS"));
        dataModel.getEnv().setFs_hdfs_impl(config.get("FS.HDFS.IMPL"));
        dataModel.getEnv().setOOZIE_WORK_DIR(config.get("OOZIE_WORK_DIR"));
        dataModel.getEnv().setOOZIE_APP_PATH(config.get("OOZIE_APP_PATH"));

        //get workflow-run-accession
        if (options.has("status") == false && dataModel.isMetadataWriteBack()) {
            if (workflowAccession != null && workflowRunAccession == null) {
                int workflowrunid = this.metadata.add_workflow_run(workflowAccession);
                int workflowrunaccession = this.metadata.get_workflow_run_accession(workflowrunid);
                dataModel.setWorkflow_accession(Integer.toString(workflowAccession));
                dataModel.setWorkflow_run_accession(String.valueOf(workflowrunaccession));
            } else if (workflowAccession != null && workflowRunAccession != null) {
                dataModel.setWorkflow_accession(Integer.toString(workflowAccession));
                dataModel.setWorkflow_run_accession(String.valueOf(workflowRunAccession));
            } else {
                assert (false);
                Log.error("This condition should never be reached");
                throw new UnsupportedOperationException();
            }
        }

        //parse XML or Java Object for
        if (workflow_java) {
            try {
                Method m = null;
                m = clazz.getMethod("setupDirectory");
                m.invoke(dataModel);
                m = clazz.getMethod("setupFiles");
                m.invoke(dataModel);
                //handle the provisionedPath
                //this.setupProvisionedPath(dataModel.getFiles());
                m = clazz.getMethod("setupWorkflow");
                m.invoke(dataModel);
                m = clazz.getMethod("setupEnvironment");
                m.invoke(dataModel);
                m = clazz.getMethod("buildWorkflow");
                m.invoke(dataModel);
            } catch (SecurityException e) {
                Log.error("SecurityException",e);
                Rethrow.rethrow(e);
            } catch (NoSuchMethodException e) {
                Log.error("NoSuchMethodException",e);
                Rethrow.rethrow(e);
            } catch (IllegalArgumentException e) {
                Log.error("IllegalArgumentException",e);
                Rethrow.rethrow(e);
            } catch (IllegalAccessException e) {
                Log.error("IllegalAccessException",e);
                Rethrow.rethrow(e);
            } catch (InvocationTargetException e) {
                Log.error("InvocationTargetException",e);
                Rethrow.rethrow(e);
            }
        } else {
            WorkflowXmlParser xmlParser = new WorkflowXmlParser();
            xmlParser.parseXml(dataModel, metaInfo.get("workflow_template"));
        }
        AbstractWorkflowDataModel.prepare(dataModel);
        //set wait
        dataModel.setWait(this.options.has("wait"));
        Log.info("returning datamodel");
        return dataModel;
    }

    /**
     * I'm copying this from BasicWorkflow since I don't know if the package
     * net.sourceforge.seqware.pipeline.workflow will be removed or if all the
     * workflowV2 will be merged.
     *
     * This code will either copy or download from S3, unzip, and return unzip
     * location.
     *
     * It's used when the local workflow bundle dir is null or doesn't exist
     * which is a sign that the workflow bundle should be retrieved from the
     * permanent location
     *
     * @param permLoc
     * @return
     */
    private String getAndProvisionBundle(String permLoc) {
        String result = null;
        Bundle bundle = new Bundle(this.metadata, this.config);
        ReturnValue ret = null;
        if (permLoc.startsWith("s3://")) {
            ret = bundle.unpackageBundleFromS3(permLoc);
        } else {
            ret = bundle.unpackageBundle(new File(permLoc));
        }
        if (ret != null) {
            return (ret.getAttribute("outputDir"));
        }
        return (result);
    }

    private Map<String, String> loadIniConfigs(Integer workflowAccession, Integer workflowRunAccession, String bundlePath) {
        // the map
        HashMap<String, String> map = new HashMap<String, String>();
        if (workflowRunAccession != null) {
            Log.info("loading ini files from DB");
            // TODO: this code is from BasicWorkflow, make a notice of that when refactoring

            // get the workflow run
            WorkflowRun wr = this.metadata.getWorkflowRunWithWorkflow(workflowRunAccession.toString());
            // iterate over all the generic default params
            // these params are created when a workflow is installed
            SortedSet<WorkflowParam> workflowParams = this.metadata
                    .getWorkflowParams(workflowAccession.toString());
            for (WorkflowParam param : workflowParams) {
                map.put(param.getKey(), param.getDefaultValue());
            }

            // FIXME: this needs to be implemented otherwise portal submitted won't
            // work!
            // now iterate over the params specific for this workflow run
            // this is where the SeqWare Portal will populate parameters for
            // a scheduled workflow
	/*
             * workflowParams =
             * this.metadata.getWorkflowRunParams(workflowRunAccession);
             * for(WorkflowParam param : workflowParams) { map.put(param.getKey(),
             * param.getValue()); }
             */

            // Workflow Runs that are scheduled by the web service don't populate
            // their
            // params into the workflow_run_params table but, instead, directly
            // write
            // to the ini field.
            // FIXME: the web service should just use the same approach as the
            // Portal
            // and this will make it more robust to pass in the
            // parent_processing_accession
            // via the DB rather than ini_file field
            map.putAll(MapTools.iniString2Map(wr.getIniFile()));
        } else {
            Log.info("loading ini files from options");

            Map<String, String> ret = new HashMap<String, String>();
            //set conifg, pass the config files to Map<String,String>, also put the .settings to Map<String,String>
            // ini-files
            ArrayList<String> iniFiles = new ArrayList<String>();
            if (options.has("ini-files")) {
                List opts = options.valuesOf("ini-files");
                for (Object opt : opts) {
                    String[] tokens = ((String) opt).split(",");
                    for (String t : tokens) {
                        iniFiles.add(t);
                    }
                }
            }
            for (String ini : iniFiles) {
                //the ini file path might actually have ${workflow_bundle_dir} in the name
                String newIni = replaceWBD(ini, bundlePath);
                Log.debug("  INI FILE: " + ini);
                if ((new File(ini)).exists()) {
                    MapTools.ini2Map(ini, map);
                }
            }
        }
        // allow the command line options to override options in the map
        // Parse command line options for additional configuration. Note that we
        // do it last so it takes precedence over the INI
        MapTools.cli2Map(this.params, map);
        return MapTools.expandVariables(map, MapTools.providedMap(bundlePath));
    }

    //FIXME should iterate all options automatically
    private void mergeCmdOptions(AbstractWorkflowDataModel model) {
        Map<String, String> map = model.getConfigs();
        //merge parent-accessions
        if (options.has("parent-accessions")) {
            // parent accessions
            ArrayList<String> parentAccessions = new ArrayList<String>();
            if (options.has("parent-accessions")) {
                List opts = options.valuesOf("parent-accessions");
                for (Object opt : opts) {
                    String[] tokens = ((String) opt).split(",");
                    for (String t : tokens) {
                        parentAccessions.add(t);
                    }
                }
            }
            model.setParentAccessions(parentAccessions);
        }
        //merge 
        // link-workflow-run-to-parents
/*    	if (options.has("link-workflow-run-to-parents")) {
         ArrayList<String> parentsLinkedToWR = new ArrayList<String>();
         List opts = options.valuesOf("link-workflow-run-to-parents");
         for (Object opt : opts) {
         String[] tokens = ((String) opt).split(",");
         for (String t : tokens) {
         parentsLinkedToWR.add(t);
         }
         }
         map.put("link-workflow-run-to-parents", org.apache.commons.lang.StringUtils.join(parentsLinkedToWR,","));
         }*/
        //merge workflow-accession
        if (options.has("workflow-accession")) {
            model.setWorkflow_accession((String) options.valueOf("workflow-accession"));
        }
        //merge "workflow-run-accession"
        if (options.has("workflow-run-accession")) {
            model.setWorkflow_run_accession((String) options.valueOf("workflow-run-accession"));
        }
        //merge schedule
        if (options.has("schedule")) {
            map.put("schedule", "true");
        }
        //merge bundle
        if (options.has("bundle")) {
            map.put("bundle", (String) options.valueOf("bundle"));
        }
        //bundle "provisioned-bundle-dir"
        if (options.has("provisioned-bundle-dir")) {
            map.put("provisioned-bundle-dir", (String) options.valueOf("provisioned-bundle-dir"));
        }
        //launch-scheduled
        if (options.has("launch-scheduled")) {
            List<String> scheduledAccessions = (List<String>) options
                    .valuesOf("launch-scheduled");
            map.put("launch-scheduled", org.apache.commons.lang.StringUtils.join(scheduledAccessions, ","));
        }
        //host
        if (options.has("host")) {
            map.put("host", (String) options.valueOf("host"));
        }
        //metadatawriteback
        boolean metadataWriteback = true;
        if (options.has("no-metadata") || options.has("no-meta-db") || options.has("status")) {
            metadataWriteback = false;
        }
        map.put("metadata", Boolean.toString(metadataWriteback));
        model.setMetadataWriteBack(metadataWriteback);
        //metadata-output-file-prefix
        if (options.has("metadata-output-file-prefix")) {
            model.setMetadata_output_file_prefix((String) options.valueOf("metadata-output-file-prefix"));
        } else if (model.hasPropertyAndNotNull("output_prefix")) {
            try {
                model.setMetadata_output_file_prefix(model.getProperty("output_prefix"));
            } catch (Exception ex) {
                Logger.getLogger(WorkflowDataModelFactory.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else {
            Log.error("You need to specify the output prefix for your workflow using either --metadata-output-file-prefix as a WorkflowLauncher param or in your workflow INI file as output_prefix!");
        }
        //metadata-output-dir
        if (options.has("metadata-output-dir")) {
            model.setMetadata_output_dir((String) options.valueOf("metadata-output-dir"));
        } else if (model.hasPropertyAndNotNull("output_dir")) {
            try {
                model.setMetadata_output_dir(model.getProperty("output_dir"));
            } catch (Exception ex) {
                Logger.getLogger(WorkflowDataModelFactory.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else {
            Log.error("You need to specify the output dir for your workflow using either --metadata-output-dir as a WorkflowLauncher param or in your workflow INI file as output_dir!");
        }
        //workflow_engine
        if (options.has("workflow-engine")) {
            model.setWorkflow_engine((String) options.valueOf("workflow-engine"));
        }
    }
}
