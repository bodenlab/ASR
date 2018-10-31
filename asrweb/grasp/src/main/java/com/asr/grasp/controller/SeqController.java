package com.asr.grasp.controller;

import api.PartialOrderGraph;
import com.asr.grasp.model.InferenceModel;
import com.asr.grasp.model.SeqModel;
import com.asr.grasp.utils.Defines;
import dat.POGraph;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import json.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reconstruction.ASRPOG;
import vis.POAGJson;

//import org.biojava.nbio.alignment.Alignments.PairwiseSequenceAlignerType;
//import org.biojava.nbio.alignment.template.SequencePair;
//import org.biojava.nbio.alignment.template.SubstitutionMatrix;
//import org.biojava.nbio.core.sequence.ProteinSequence;
//import org.biojava.nbio.core.sequence.compound.AminoAcidCompound;
//import org.biojava.nbio.core.sequence.io.FastaReaderHelper;
/**
 * Class that keeps track of the consensus sequences stored in the database.
 * Currently all joint reconstructions are saved.
 * ToDo: Save marginal reconstructions
 * Note: All the queries here use the assumption that the reconstruction ID has been checked
 * for authenticity & security (i.e. user access) previously to this class.
 * --> Done using ReconController in GraspApplication.
 *
 *
 * Created by ariane on 14/10/18.
 */
@Service
public class SeqController {

    @Autowired
    private SeqModel seqModel;

    @Autowired
    private InferenceModel infModel;

    private String logFileName;

    /**
     * Helper function that prints the memory usage to a file
     */
    private long[] printStats(FileWriter fr, String label, double time, long prevTotal, long prevFree) {
        Runtime rt = Runtime.getRuntime();
        long total = rt.totalMemory();
        long free = rt.freeMemory();
        long used = total - free;
        if (prevTotal != 0) {
            try {
                fr.write(label + ",consensus," + time +
                        "," + total +
                        "," + used +
                        "," + free + "\n");
                System.out.println(label + " saved");
            } catch (Exception e) {
                System.out.println(label + "," + time +
                        "," + total +
                        "," + used +
                        "," + free + "\n");
            }
        }
        long[] vals = {total, free};
        return vals;
    }


    /**
     * Inserts all the joint reconstructions into the database.
     * Returns the list of insterted node labels.
     * ToDo: Do we want to auto delete any they don't want? Currently keeping all.
     *
     * @param reconId
     * @param asrInstance
     * @return
     */
    public List<String> insertAllJointsToDb (int reconId, ASRPOG asrInstance, boolean gappy) {
        List<String> labels = asrInstance.getAncestralSeqLabels();
        List<String> insertedLabels = new ArrayList<>();
        FileWriter fr = null;
        long[] vals = {0, 0};
        long startTime = System.nanoTime();
        if (logFileName != null) {
            File file = new File("/var/www/GRASP/data/stats_" + logFileName + ".csv");
            try {
                fr = new FileWriter(file);
                fr.write("nodeId,test,time,total_mem,used_mem,free_mem\n");
            } catch (Exception e) {
                System.out.println("Couldn't open file...");
            }
        }
        for (String label: labels) {

            PartialOrderGraph ancestor = asrInstance.getGraph(label);
            // Insert it into the database
            // What we want to do here is perform two inserts -> one for the sequence so we can do
            // motif searching
            POAGJson ancsJson = new POAGJson(ancestor, gappy);
            String ancsStr = ancsJson.toJSON().toString();

            boolean inserted = infModel.insertIntoDb(reconId, label, ancsStr);
            if (! inserted) {
                return null;
            }
            inserted = seqModel.insertIntoDb(reconId, label, ancsJson.getConsensusSeq(), Defines.JOINT, gappy);
            if (inserted) {
                insertedLabels.add(label);
            }
            System.out.println("Time to make insert twice:" + ((System.nanoTime() - startTime) / 1000000000.0));

            System.out.print(label + ", ");
            if (this.logFileName != null) {
                vals = printStats(fr, label, (System.nanoTime() - startTime) / 1000000000.0, vals[0],
                        vals[1]);
            }
        }
        System.out.println("\n Finished Inserting Joint recons.");
        return insertedLabels;
    }

    public void setFileName(String filename) {
        logFileName = filename;
    }
    /**
     * Insert a single joint instance into the database.
     *
     * @param reconId
     * @param label
     * @param asrInstance
     * @return
     */
    public String insertJointToDb(int reconId, String label, ASRPOG asrInstance, boolean gappy) {
        List<String> labels = asrInstance.getAncestralSeqLabels();
        POGraph ancestor = asrInstance.getAncestor(label);
        // Insert it into the database
        String insertedAncs = ancestor.getSupportedSequence(gappy);
        boolean inserted = seqModel.insertIntoDb(reconId, label, insertedAncs, Defines.JOINT, gappy);
        if (inserted) {
            return insertedAncs;
        }
        return null;
    }

    /**
     * Inserts all the extent sequences into the DB as well.
     * Returns the list of insterted node labels.
     * ToDo: Do we want to auto delete any they don't want? Currently keeping all.
     *
     * @param reconId
     * @param extantSeqs
     * @return
     */
    public String insertAllExtantsToDb (int reconId, HashMap<String, String> extantSeqs, boolean gappy) {
        if (! seqModel.insertListIntoDb(reconId, extantSeqs, gappy)) {
            return "unable to insert all extents.";
        }
        return null;
    }

    /**
     * Deletes all from a database that are for a particular reconstruction.
     * @param reconId
     * @return
     */
    public String deleteAllSeqsForRecon (int reconId) {
        return seqModel.deleteFromDb(reconId);
    }


    /**
     * Returns all the nodeLabels that have a consensus sequence that match a particular motif.
     *
     * @param reconId
     * @param motif
     * @return
     */
    public ArrayList<String> findAllWithMotif (int reconId, String motif) {
        return seqModel.findNodesWithMotif(reconId, motif);
    }

    /**
     * Returns the nodes with motifs in JSON format so that these can be updated on the front end.
     * @param reconId
     * @param motif
     * @return
     */
    public JSONArray findAllWithMotifJSON (int userAccess, int reconId, String motif) {
        if (userAccess == Defines.NO_ACCESS) {
            return new JSONArray().put("NO ACCESS");
        }
        ArrayList<String> ancestorLabelsWithMotif = findAllWithMotif(reconId, motif);
        JSONArray ancestorLabelsWithMotifJSON = new JSONArray();
        for (String label: ancestorLabelsWithMotif) {
            ancestorLabelsWithMotifJSON.put(label);
        }
        return ancestorLabelsWithMotifJSON;
    }

    /**
     * Gets all the sequences for a given reconstruction.
     * @param reconId
     * @param method
     * @return
     */
    public HashMap<String, String> getAllSeqs (int reconId, int method) {
        return seqModel.getAllSeqs(reconId, method);
    }


    /**
     * Returns whether a user has saved the reconstruction in the new format or not.
     *
     * @param reconId
     * @return
     */
    public boolean hasReconsAncestorsBeenSaved (int reconId) {
        return seqModel.hasReconsAncestorsBeenSaved(reconId);
    }

    /**
     * Gets all the labels so they can be displayed for download.
     * @param reconId
     * @param method
     * @return
     */
    public ArrayList<String> getAllSeqLabels(int reconId, int method) {
       return seqModel.getAllSeqLabels(reconId, method);
    }

    /**
     * Saves an ancestor node to a file.
     * @param fileWriter
     * @param label
     * @param reconId
     * @param reconMethod
     * @throws IOException
     */
    public void saveAncestorToFile(BufferedWriter fileWriter, String label, int reconId, int reconMethod, String extraLabelInfo) throws IOException {

        String seq = seqModel.getSeqByLabel(label, reconId, reconMethod);
        if (seq != null) {
            fileWriter.write(">" + label + extraLabelInfo);
            fileWriter.newLine();
            fileWriter.write(seq);
            fileWriter.newLine();
        }
    }


    /**
     * Gets a sequence and returns a JSON formatted version. This enables us to use it on
     * the front end.
     *
     * @param reconId
     * @param label
     */
    public String getInfAsJson(int reconId, String label) {
        return infModel.getInferenceForLabel(reconId, label);
    }


    /**
     * Gets a sequence and returns a JSON formatted version. This enables us to use it on
     * the front end.
     *
     * @param reconId
     * @param label
     * @param reconMethod
     */
    public JSONArray getSeqAsJson(int reconId, String label, int reconMethod) {
        String seq = seqModel.getSeqByLabel(label, reconId, reconMethod);
        if (seq != null) {
            JSONArray seqJSON = new JSONArray();
            for (int x = 0; x < seq.length(); x ++) {
                JSONArray position = new JSONArray();
                if (seq.charAt(x) != '-') {
                    position.put(Defines.G_LABEL, seq.charAt(x));
                    position.put(Defines.G_X, x);
                    position.put(Defines.G_ID, x);
                    position.put(Defines.G_CONSENSUS, true);
                    seqJSON.put(position);
                }
            }
            return seqJSON;
        }
        return null;
    }


//    private void alignPairGlobal(String seq1, String seq2) throws Exception {
//        ProteinSequence s1 = new ProteinSequence(seq1);
//        ProteinSequence s2 = new ProteinSequence(seq2);
//        SubstitutionMatrix<AminoAcidCompound> matrix = new SimpleSubstitutionMatrix<AminoAcidCompound>();
//        SequencePair<ProteinSequence, AminoAcidCompound> pair = Alignments.getPairwiseAlignment(s1, s2,
//                PairwiseSequenceAlignerType.GLOBAL, new SimpleGapPenalty(), matrix);
//        System.out.printf("%n%s vs %s%n%s", pair.getQuery().getAccession(), pair.getTarget().getAccession(), pair);
//    }

    private int getHammingDistance(String seq1, String seq2) {
        if (seq1.length() != seq2.length())
            return -1;

        int counter = 0;
        for (int i = 0; i < seq1.length(); i++) {
            if (seq1.charAt(i) != seq2.charAt(i)) {
                counter++;
            }
        }
        return counter;
    }

    /**
     * ------------------------------------------------------------------------
     *          The following are to set the test env.
     * ------------------------------------------------------------------------
     */
    public void setSeqModel(SeqModel seqModel) {
        this.seqModel = seqModel;
    }
    public void setInfModel(InferenceModel infModel) {
        this.infModel = infModel;
    }
}
