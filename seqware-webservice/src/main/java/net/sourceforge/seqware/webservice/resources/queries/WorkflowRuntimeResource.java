/*
 * Copyright (C) 2012 SeqWare
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.sourceforge.seqware.webservice.resources.queries;

import static net.sourceforge.seqware.webservice.resources.BasicResource.parseClientInt;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import net.sourceforge.seqware.common.factory.DBAccess;
import net.sourceforge.seqware.common.util.Log;
import net.sourceforge.seqware.webservice.resources.BasicRestlet;

import org.apache.commons.dbutils.ResultSetHandler;
import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Status;
import org.restlet.resource.ResourceException;

/**
 * <p>WorkflowRuntimeResource class.</p>
 *
 * @author boconnor
 * @version $Id: $Id
 */
public class WorkflowRuntimeResource extends BasicRestlet {
    public static final String RUNTIME = "runtime";
    public static final String COUNTS = "counts";
    public static final String WORKFLOW_RUN_ID = "workflowRunId";
    public static final String WORKFLOW_NAME = "workflowName";
    public static final String TAB = "\t";
    public static final String NEWLINE = "\n";

    /**
     * <p>Constructor for WorkflowRuntimeResource.</p>
     *
     * @param context a {@link org.restlet.Context} object.
     */
    public WorkflowRuntimeResource(Context context) {
        super(context);
    }

    /** {@inheritDoc} */
    @Override
    public void handle(Request request, Response response) {
        authenticate(request.getChallengeResponse().getIdentifier());
        init(request);
        if (request.getMethod().compareTo(Method.GET) == 0) {
            // main data structure
            // workflow name -> algorithm -> workflow run id -> runtime -> int (this is a total runtime across all runs)
            //                                               -> counts  -> int
            HashMap<String, HashMap<String, HashMap<String, HashMap<String, Integer>>>> d = new HashMap<String, HashMap<String, HashMap<String, HashMap<String, Integer>>>>();

            String workflowAccession = null;
            final String workflowId = "workflowId";
            if (queryValues.containsKey(workflowId)) {
                Log.debug("Processing attributes for: " + request.getEntityAsText());
                String localWorkflowAccession = queryValues.get(workflowId);
                if (localWorkflowAccession != null) {
                    workflowAccession = localWorkflowAccession;
                }
            }

            StringBuilder m = new StringBuilder();

            try {

                // now iterate over each workflow (or just the one specified) and find average runtime per step
                String query = "select p.processing_id, p.algorithm, p.status, p.create_tstmp, w.name, wr.workflow_run_id "
                        + "from workflow_run as wr, workflow as w, processing as p "
                        + "where w.workflow_id = wr.workflow_id and p.workflow_run_id = wr.workflow_run_id and p.status = 'success' "
                        + "and wr.status = 'completed' ";

                if (workflowAccession != null) {
                    query = query + " and w.sw_accession = " + workflowAccession;
                }
                Log.debug("Running query: " + query);

                query = query + " order by p.create_tstmp";

                // ends up storing processing id -> {processing id, workflow run id, workflow name}
                Map<Integer, Map<String, String>> currentProcIds = DBAccess.get().executeQuery(query, new ResultSetHandler<Map<Integer, Map<String, String>>>(){
                  @Override
                  public Map<Integer, Map<String, String>> handle(ResultSet rs) throws SQLException {
                    Map<Integer, Map<String, String>> currentProcIds = new HashMap<Integer, Map<String, String>>();
                    while (rs.next()) {
                      Integer processingId = rs.getInt(1);
                      //String algorithm = rs.getString(2);
                      //String status = rs.getString(3);
                      //Timestamp createTstmp = rs.getTimestamp(4);
                      String workflowName = rs.getString(5);
                      Integer workflowRunId = rs.getInt(6);
                      HashMap<String, String> currentProcHash = new HashMap<String, String>();
                      // currentProcHash.put("procId", processingId.toString());
                      currentProcHash.put(WORKFLOW_RUN_ID, workflowRunId.toString());
                      currentProcHash.put(WORKFLOW_NAME, workflowName);
                      currentProcIds.put(processingId, currentProcHash);
                    }
                    return currentProcIds;
                  }
                });
                
                triggerFindProcessings(currentProcIds, d);                

                // at this point the whole hash should be populated
                // workflow name -> algorithm -> workflow run id -> runtime -> int
                //                                               -> counts  -> int
                for (String workflow : d.keySet()) {
                    m.append(workflow).append(TAB);
                    String lastAlgo = "";
                    for (String algo : d.get(workflow).keySet()) {
                        m.append(algo).append(TAB);
                        lastAlgo = algo;
                    }
                    m.append("Total"+NEWLINE);
                    for (String wrId : d.get(workflow).get(lastAlgo).keySet()) {
                        m.append(wrId).append(TAB);
                        int totalWRuntime = 0;
                        for (String algo : d.get(workflow).keySet()) {
                            if (d.get(workflow) != null && d.get(workflow).get(algo) != null && d.get(workflow).get(algo).get(wrId) != null && d.get(workflow).get(algo).get(wrId).get(RUNTIME) != null) {
                                Integer runtime = d.get(workflow).get(algo).get(wrId).get(RUNTIME);
                                totalWRuntime += runtime;
                                m.append(runtime).append(TAB);
                            }
                        }
                        m.append(totalWRuntime).append(NEWLINE);
                    }
                }
                

            } catch (Exception e) {
                System.err.print(e.getMessage());
                throw new RuntimeException(e);
            } finally {
                DBAccess.close();
            }
            if (m.toString().isEmpty()){
                response.setStatus(Status.CLIENT_ERROR_NOT_FOUND);
            }
            response.setEntity(m.toString(), MediaType.TEXT_PLAIN);
        } else {
            response.setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
        }
    }

    private void triggerFindProcessings(Map<Integer, Map<String, String>> currentProcIds, HashMap<String, HashMap<String, HashMap<String, HashMap<String, Integer>>>> d) throws ResourceException {
        // processing IDs, allows us to skip processing IDs that have already been processed
        HashMap<Integer, Boolean> seen = new HashMap<Integer, Boolean>();
        // algos
        // HashMap<String, Boolean> algos = new HashMap<String, Boolean>();
        // workflow run IDs
        // HashMap<Integer, Boolean> wrIds = new HashMap<Integer, Boolean>();
        for (Integer currentProcId : currentProcIds.keySet()) {
            //String procId = currentProcIds.get(currentProcId).get("procId");
            String workflowRunId = currentProcIds.get(currentProcId).get(WORKFLOW_RUN_ID);
            String workflowName = currentProcIds.get(currentProcId).get(WORKFLOW_NAME);
            recursiveFindProcessings(currentProcId, parseClientInt(workflowRunId), workflowName, seen, d);
        }
    }
    
    /**
     * 
     * @param processingId
     * @param workflowRunId
     * @param workflowName
     * @param seen
     * @param d
     * @param algos
     * @param wrIds 
     */
    private void recursiveFindProcessings(Integer processingId, Integer workflowRunId, String workflowName, HashMap<Integer, Boolean> seen, 
            HashMap<String, HashMap<String, HashMap<String, HashMap<String, Integer>>>> d) {
        if (seen.get(processingId) != null && seen.get(processingId)) {
            return;
        }
        seen.put(processingId, true);

        try {
            Object[] tuple = DBAccess.get().executeQuery("select p.processing_id, p.algorithm, p.status, p.create_tstmp, EXTRACT(EPOCH from p.run_stop_tstmp - p.run_start_tstmp) as length from processing as p where p.processing_id = " + processingId + " and p.status = 'success' order by p.create_tstmp", new ResultSetHandler<Object[]>(){
              @Override
              public Object[] handle(ResultSet rs) throws SQLException {
                if (rs.next()) {
                  return new Object[]{rs.getString(2), rs.getString(3), rs.getTimestamp(4), rs.getInt(5)};
                } else {
                  return null;                  
                }
              }
            });

            Map<Integer, Boolean> childProcessingHash = new HashMap<Integer, Boolean>();
            if (tuple != null) {

                String algorithm = (String)tuple[0];
                //String status = (String)tuple[1];
                //Timestamp create = (Timestamp)tuple[2];
                Integer runtime = (Integer)tuple[3];

                //HashMap<String, HashMap<String, HashMap<String, HashMap<String, Integer>>>> d = new HashMap<String, HashMap<String, HashMap<String, HashMap<String, Integer>>>>();

                // workflow name -> algorithm -> workflow run id -> runtime -> int
                //                                               -> counts  -> int
                HashMap<String, HashMap<String, HashMap<String, Integer>>> algorithmsHash = d.get(workflowName);
                if (algorithmsHash == null) {
                    algorithmsHash = new HashMap<String, HashMap<String, HashMap<String, Integer>>>();
                    d.put(workflowName, algorithmsHash);
                }

                HashMap<String, HashMap<String, Integer>> workflowRunHash = algorithmsHash.get(algorithm);
                if (workflowRunHash == null) {
                    workflowRunHash = new HashMap<String, HashMap<String, Integer>>();
                    algorithmsHash.put(algorithm, workflowRunHash);
                }

                HashMap<String, Integer> runtimeHash = workflowRunHash.get(workflowRunId.toString());
                if (runtimeHash == null) {
                    runtimeHash = new HashMap<String, Integer>();
                    workflowRunHash.put(workflowRunId.toString(), runtimeHash);
                }

                Integer runtimes = runtimeHash.get(RUNTIME);
                if (runtimes == null) {
                    runtimes = runtime;
                } else {
                    runtimes += runtime;
                }
                runtimeHash.put(RUNTIME, runtimes);

                Integer counts = runtimeHash.get(COUNTS);
                if (counts == null) {
                    counts = 1;
                } else {
                    counts++;
                }
                runtimeHash.put(COUNTS, counts);

                childProcessingHash = DBAccess.get().executeQuery("select p.processing_id, p.algorithm, p.status, p.create_tstmp from processing as p, processing_relationship as pr where pr.parent_id = " + processingId + " and pr.child_id = p.processing_id and p.ancestor_workflow_run_id = " + workflowRunId, new ResultSetHandler<Map<Integer, Boolean>>(){
                  @Override
                  public Map<Integer, Boolean> handle(ResultSet rs) throws SQLException {
                    Map<Integer, Boolean> childProcessingHash = new HashMap<Integer, Boolean>();
                    while (rs.next()) {
                      childProcessingHash.put(rs.getInt(1), true);
                    }
                    return childProcessingHash;
                  }
                });


            }

            // now recursively call
            for (Integer childProcId : childProcessingHash.keySet()) {
                recursiveFindProcessings(childProcId, workflowRunId, workflowName, seen, d);
            }

        } catch (SQLException e) {
            System.err.println(e.getMessage());
            throw new RuntimeException(e);
        } finally{
            DBAccess.close();
        }
    }
}
