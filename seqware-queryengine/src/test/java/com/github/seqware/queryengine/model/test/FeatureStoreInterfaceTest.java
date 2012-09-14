package com.github.seqware.queryengine.model.test;

import com.github.seqware.queryengine.factory.CreateUpdateManager;
import com.github.seqware.queryengine.factory.SWQEFactory;
import com.github.seqware.queryengine.kernel.Compression;
import com.github.seqware.queryengine.model.Feature;
import com.github.seqware.queryengine.model.FeatureSet;
import com.github.seqware.queryengine.model.Tag;
import java.util.Random;
import java.util.UUID;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests that storing FeatureSets and retrieving FeatureSets results in
 * consistent results.
 *
 * @author dyuen
 * @author jbaran
 */
public class FeatureStoreInterfaceTest {

    protected static FeatureSet aSet;
    protected static FeatureSet bSet;
    protected static Feature a1, a2, a3;

    @BeforeClass
    public static void setupTests() {
        UUID testID = UUID.randomUUID();
        //System.out.println("starting beforeClass in testID: " + testID.toString());
        CreateUpdateManager mManager = SWQEFactory.getModelManager();
        aSet = mManager.buildFeatureSet().setReference(mManager.buildReference().setName("Dummy_ref").build()).build();
        // create and store some features
        a1 = mManager.buildFeature().setSeqid("chr16").setStart(1000000).setStop(1000100).build();
        a2 = mManager.buildFeature().setSeqid("chr16").setStart(1000200).setStop(1000300).build();
        a3 = mManager.buildFeature().setSeqid("chr16").setStart(1000400).setStop(1000500).build();
        aSet.add(a1);
        aSet.add(a2);
        aSet.add(a3);
        mManager.flush();
        //System.out.println("ending beforeClass in testID: " + testID.toString());
        bSet = diverseBSet(mManager);
        mManager.flush();
    }

    /**
     * Build a test set that is a bit diverse in its data values.
     *
     * @param mManager Entity manager for persisting atoms.
     * @return A feature set with somewhat diverse data values.
     */
    public static FeatureSet diverseBSet(CreateUpdateManager mManager) {
        FeatureSet set = mManager.buildFeatureSet().setReference(mManager.buildReference().setName("Diverse_Set").build()).build();

        //
        // The following uses the Sequence Ontology (SO), http://www.sequenceontology.org/
        //

        // Named tags, where the values convey actual information:
        Tag termTag = mManager.buildTag().setKey("SO_term").build();
        Tag idTag = mManager.buildTag().setKey("SO_id").build();
        Tag contigTag = mManager.buildTag().setKey(Compression.getSequenceOntologyAccessionSurrogate("SO:0000149")).build();

        // Heavy tags, where the tag key itself represents information:
        Tag transcriptProcessingVariant = mManager.buildTag().setKey("SO::sequence_variant::functional_variant::transcript_function_variant::transcript_processing_variant").build();
        Tag transcriptStabilityVariant = mManager.buildTag().setKey("SO::sequence_variant::functional_variant::transcript_function_variant::transcript_stability_variant").build();
        Tag copyNumberChange = mManager.buildTag().setKey("SO::sequence_variant::structural_variant::copy_number_change").build();

        // Features for location based tagging, which tests tag querying capabilities:
        Feature a = mManager.buildFeature().setSeqid("chr16").setStart(1000000).setStop(1000100).setStrand(Feature.Strand.POSITIVE).build();
        Feature b = mManager.buildFeature().setSeqid("chr16").setStart(1000000).setStop(1000101).setStrand(Feature.Strand.POSITIVE).build();
        Feature c = mManager.buildFeature().setSeqid("chr16").setStart(2000000).setStop(2000102).setStrand(Feature.Strand.POSITIVE).build();

        // Features for variant based tagging, which tests inheritance based querying capabilities:
        Feature d = mManager.buildFeature().setSeqid("chr16").setStart(2000000).setStop(2000101).setStrand(Feature.Strand.NEGATIVE).build();
        Feature e = mManager.buildFeature().setSeqid("chr16").setStart(2000000).setStop(2000102).setStrand(Feature.Strand.POSITIVE).build();
        Feature f = mManager.buildFeature().setSeqid("chr16").setStart(2000000).setStop(2000101).setStrand(Feature.Strand.POSITIVE).build();
        Feature g = mManager.buildFeature().setSeqid("chr16").setStart(3000000).setStop(3000102).setStrand(Feature.Strand.POSITIVE).build();

        // Loci tags:
        a.associateTag(termTag.toBuilder().setValue("region").build());
        a.associateTag(idTag.toBuilder().setValue(Compression.getSequenceOntologyAccessionSurrogate("SO:0000001")).build());
        b.associateTag(termTag.toBuilder().setValue("region").build());
        b.associateTag(idTag.toBuilder().setValue(Compression.getSequenceOntologyAccessionSurrogate("SO:0000001")).build());
        b.associateTag(termTag.toBuilder().setValue("contig").build());
        b.associateTag(idTag.toBuilder().setValue(Compression.getSequenceOntologyAccessionSurrogate("SO:0000149")).build());
        c.associateTag(termTag.toBuilder().setValue("contig").build());
        c.associateTag(idTag.toBuilder().setValue(Compression.getSequenceOntologyAccessionSurrogate("SO:0000149")).build());

        // Alternative tag formatting:
        b.associateTag(contigTag.toBuilder().setValue("1").build());
        c.associateTag(contigTag.toBuilder().setValue("2").build());

        // Variant tags:
        d.associateTag(transcriptProcessingVariant);
        e.associateTag(transcriptProcessingVariant);
        e.associateTag(transcriptStabilityVariant);
        f.associateTag(transcriptStabilityVariant);
        g.associateTag(copyNumberChange);

        set.add(a);
        set.add(b);
        set.add(c);
        set.add(d);
        set.add(e);
        set.add(f);
        set.add(g);
        set.add(mManager.buildFeature().setSeqid("chr16").setStart(3000000).setStop(3000102).setStrand(Feature.Strand.POSITIVE).build());
        set.add(mManager.buildFeature().setSeqid("chr16").setStart(3000000).setStop(3000101).setStrand(Feature.Strand.NEGATIVE).build());
        set.add(mManager.buildFeature().setSeqid("chr16").setStart(3000000).setStop(3000102).setStrand(Feature.Strand.POSITIVE).build());
        set.add(mManager.buildFeature().setSeqid("chr17").setStart(3000000).setStop(3000102).setStrand(Feature.Strand.POSITIVE).build());
        set.add(mManager.buildFeature().setSeqid("chr17").setStart(3000000).setStop(3000101).setStrand(Feature.Strand.POSITIVE).build());
        set.add(mManager.buildFeature().setSeqid("chr17").setStart(4000000).setStop(4000101).setStrand(Feature.Strand.POSITIVE).build());
        set.add(mManager.buildFeature().setSeqid("chr17").setStart(4000000).setStop(4000101).setStrand(Feature.Strand.NEGATIVE).build());
        set.add(mManager.buildFeature().setSeqid("chr17").setStart(4000000).setStop(4000102).setStrand(Feature.Strand.POSITIVE).build());
        set.add(mManager.buildFeature().setSeqid("chr17").setStart(4000000).setStop(4000101).setStrand(Feature.Strand.POSITIVE).build());
        set.add(mManager.buildFeature().setSeqid("chr17").setStart(4000000).setStop(4000102).setStrand(Feature.Strand.POSITIVE).build());

        return set;
    }

    /**
     * Build a large test set.
     *
     * @param mManager Entity manager for persisting atoms.
     * @return A large feature set populated with random features.
     */
    public static FeatureSet largeTestSet(CreateUpdateManager mManager, int size) {
        FeatureSet set = mManager.buildFeatureSet().setReference(mManager.buildReference().setName("Large_Set_" + size).build()).build();

        // Keep seed fixed for reproducibility of results:
        Random generator = new Random(923464);

        for (int i = 0; i < size; i++) {
            // Modulos are for preventing overflows when summing up for the stop coordinate. So, start is always smaller (or equal) than stop.
            int start = generator.nextInt() % 10000000;
            int stop = start + (generator.nextInt() % 10000);
            Feature.Strand strand = generator.nextBoolean() ? Feature.Strand.POSITIVE : Feature.Strand.NEGATIVE;

            set.add(mManager.buildFeature().setSeqid("chr" + ((generator.nextInt() % 23) + 1)).setStart(start).setStop(stop).setStrand(strand).build());
        }

        return set;
    }

        @Test
    public void testFeatureCreationAndIterate() {
        UUID testID = UUID.randomUUID();
        //System.out.println("running base test in testID: " + testID.toString());
        // get FeatureSets from the back-end
        boolean b1 = false;
        boolean b2 = false;
        boolean b3 = false;
        for (FeatureSet fSet : SWQEFactory.getQueryInterface().getFeatureSets()) {
            for (Feature f : fSet) {
                // sadly, Features no longer will be exactly the same after a query, we need a "contents" equals that does not examine the 
                // fsgid's featureset id?
                if (f.getStart() == a1.getStart() && f.getStop() == a1.getStop()) {
                    b1 = true;
                } else if (f.getStart() == a2.getStart() && f.getStop() == a2.getStop()) {
                    b2 = true;
                } else if (f.getStart() == a3.getStart() && f.getStop() == a3.getStop()) {
                    b3 = true;
                }
            }
        }

        Assert.assertTrue(b1 && b2 && b3);
        // System.out.println("ending base test in testID: " + testID.toString());
    }
}
