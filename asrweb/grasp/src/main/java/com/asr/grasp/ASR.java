package com.asr.grasp;

import api.PartialOrderGraph;
import com.asr.grasp.validator.File;
import json.JSONObject;
import org.springframework.web.multipart.MultipartFile;
import reconstruction.ASRPOG;
import vis.POAGJson;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * ASR API for integration in the Swing web form
 *
 * Created by marnie on 11/4/17.
 */
public class ASR {
    // ASR object to store joint reconstruction for showing resulting graphs of different nodes without performing the
    // reconstruction with each node view query
    private ASRPOG asrJoint;
    // ASR object to store marginal reconstruction of current node (if given)
    private ASRPOG asrMarginal;

    private String sessionDir;

    private String label = "Grasp";

    @File(type="aln", message="File must be an alignment (*.aln)")
    private MultipartFile alnFile;

    private String alnFilepath;

    @File(type="nwk", message="File must be in newick format (*.nwk)")
    private MultipartFile treeFile;

    private String treeFilepath;

    //@File(type="seq", message="File must be in fasta or clustal format (*.fa, *.fasta or *.aln)")
    private MultipartFile seqFile = null;

    private String inferenceType = "joint";

    private String nodeLabel = null;

    private boolean performAlignment = false;

    public ASR() {}

    /*******************************************************************************************************************
     ****** Setters and getters for ASR attributes (forms, etc, automatically call these)
     ******************************************************************************************************************/

    public String getLabel() {
        return this.label;
    }
    public void setLabel(String label) {
        this.label = label.replace(" ", "").trim();
    }
    public MultipartFile getAlnFile() { return this.alnFile; }
    public void setAlnFile(MultipartFile alnFile) {
        this.alnFile = alnFile;
    }
    public MultipartFile getTreeFile() {
        return this.treeFile;
    }
    public void setTreeFile(MultipartFile treeFile) {
        this.treeFile = treeFile;
    }
    public MultipartFile getSeqFile() {
        return this.seqFile;
    }
    public void setSeqFile(MultipartFile seqFile) {
        this.seqFile = seqFile;
    }
    public String getAlnFilepath() { return this.alnFilepath; }
    public void setAlnFilepath(String alnFilepath) { this.alnFilepath = alnFilepath; }
    public String getTreeFilepath() { return this.treeFilepath; }
    public void setTreeFilepath(String treeFilepath) { this.treeFilepath = treeFilepath; }
    public String getInferenceType() { return this.inferenceType; }
    public void setInferenceType(String infType) { this.inferenceType = infType; }
    public boolean getPerformAlignment() { return this.performAlignment; }
    public void setPerformAlignment(boolean performAlignment) { this.performAlignment = performAlignment; }
    public void setSessionDir(String dir) { this.sessionDir = dir; }
    public String getSessionDir() { return this.sessionDir; }
    public void setMarginalNodeLabel(String node) { this.nodeLabel = node; }
    public String getMarginalNodeLabel() { return this.nodeLabel; }

    /*******************************************************************************************************************
     ****** ASR functional methods
     ******************************************************************************************************************/

    /**
     * Run reconstruction using uploaded files and specified options
     */
    public void runReconstruction() throws Exception {
        if (inferenceType.equalsIgnoreCase("joint"))
            runReconstructionJoint();
        else
            runReconstructionMarginal();
    }

    /**
     * Run joint reconstruction using uploaded files and specified options
     */
    private void runReconstructionJoint() throws Exception {
        System.out.println(nodeLabel);
        asrJoint = new ASRPOG(alnFilepath, treeFilepath, true,true);
        asrJoint.saveTree(sessionDir + label + "_recon.nwk");
    }

    /**
     * Run marginal reconstruction using uploaded files and specified options
     */
    private void runReconstructionMarginal() throws Exception {
        if (nodeLabel != null && nodeLabel.equalsIgnoreCase("root"))
            nodeLabel = null;
        if (nodeLabel != null)
            asrMarginal = new ASRPOG(null, treeFilepath, alnFilepath, nodeLabel, true);
        else
            asrMarginal = new ASRPOG(alnFilepath, treeFilepath, false, true);
        asrMarginal.saveTree(sessionDir + label + "_recon.nwk");
    }

    /**
     * Get the newick string of the reconstructed tree
     * @return  newick representation of reconstructed tree
     */
    public String getReconstructedNewickString() {
        try {
            BufferedReader r = new BufferedReader(new FileReader(sessionDir + getReconstructedTreeFileName()));
            String tree = "";
            String line;
            while((line = r.readLine()) != null)
                tree += line;
            r.close();
            return tree;
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Get the filename of the reconstructed phylogenetic tree
     *
     * @return  filename
     */
    public String getReconstructedTreeFileName() {
        return label + "_recon.nwk";
    }

    /**
     * Save MSA graph
     *
     * @param filepath  filepath of where to save graph
     */
    public void saveMSA(String filepath) {
        if (inferenceType.equalsIgnoreCase("joint"))
            asrJoint.saveMSAGraph(filepath);
        else
            asrMarginal.saveMSAGraph(filepath);
    }

    /**
     * Save ancestor graph
     *
     * @param label     label of ancestor
     * @param filepath  filepath of where to save graph
     */
    public void saveAncestorGraph(String label, String filepath) {
        if (inferenceType.equalsIgnoreCase("joint"))
            asrJoint.saveGraph(filepath, label);
        else
            asrMarginal.saveGraph(filepath, label);
    }

    /**
     * Save graphs of all ancestors (joint)
     *
     * @param filepath  filepath of where to save ancestor graphs
     */
    public void saveAncestors(String filepath) {
        if (asrJoint != null)
            asrJoint.saveGraph(filepath);
    }

    /**
     * Save consensus sequence of marginal node
     *
     * @param filepath  filepath of where to save consensus sequence
     * @throws IOException
     */
    public void saveConsensusMarginal(String filepath) throws IOException {
        if (asrMarginal != null)
            asrMarginal.saveSupportedAncestors(filepath);

    }

    /**
     * Save marginal distribution matrix of marginal node
     *
     * @param filepath  filepath of where to save distribution
     * @param node      node label or MSA for sequence alignment
     * @throws IOException
     */
    public void saveMarginalDistribution(String filepath, String node) throws IOException {
        if (asrMarginal != null && !node.equalsIgnoreCase("msa"))
            asrMarginal.saveDistrib(filepath + "/" + node);
        else if (node.equalsIgnoreCase("msa"))
            asrJoint.saveMSADistrib(filepath + "/msa");
    }
/*
    public void saveMSAImage(String filepath) throws IOException {
        DrawGraph dg;
        if (inferenceType.equalsIgnoreCase("joint"))
            dg = new DrawGraph((new POAGJson(asrJoint.getMSAGraph())).toJSON());
        else
            dg = new DrawGraph((new POAGJson(asrMarginal.getMSAGraph())).toJSON());
        dg.drawImage(filepath);
    }*/

    /**
     * Save consensus sequence of marginal node
     *
     * @param filepath  filepath of where to save consensus sequence
     * @throws IOException
     */
    public void saveConsensusJoint(String filepath) throws IOException {
        if (asrJoint != null)
            asrJoint.saveSupportedAncestors(filepath);
    }

    /**
     * Get the JSON representation of the sequence alignment graph
     * @return  graph JSON object
     */
    public JSONObject getMSAGraphJSON() {
        PartialOrderGraph msa;
        if (inferenceType.equalsIgnoreCase("joint"))
            msa = asrJoint.getMSAGraph();
        else
            msa = asrMarginal.getMSAGraph();
        POAGJson json = new POAGJson(msa);
        return json.toJSON();
    }

    /**
     * Get tje JSON representation of the inferred graph at the given tree node
     *
     * @param reconType reconstruction type to query ("joint" or "marginal")
     * @param nodeLabel label of tree node to get graph representation of
     * @return  graph JSON object
     */
    public JSONObject getAncestralGraphJSON(String reconType, String nodeLabel) {
        PartialOrderGraph graph;
        if (reconType.equalsIgnoreCase("joint"))
            graph = asrJoint.getGraph(nodeLabel);
        else
            graph = asrMarginal.getGraph(nodeLabel);
        POAGJson json = new POAGJson(graph);
        return json.toJSON();
    }

    /**
     * Formats the MSA and inferred objects into JSON representation of two graphs, used for javascript visualisation.
     * This format is sent through to the website for visualisation.
     *
     * @param graphMSA          JSON object of MSA graph
     * @param graphInferred     JSON object of inferred graph
     * @return                  String of JSON
     */
    public String catGraphJSONBuilder(JSONObject graphMSA, JSONObject graphInferred) {
        // Create metadata objects to add to the POAGS
        JSONObject metadataInferred = new JSONObject();
        JSONObject metadataMSA = new JSONObject();

        // Add metadata information (for example titles, could be anything)
        metadataInferred.put("title", "Inferred");
        metadataMSA.put("title", "MSA");

        // What type of reconstruction it is, if it is a marginal reconstruction
        // pie charts will be drawn if it is a joint reconstruction then only the inferred node will be drawn
        metadataInferred.put("type", inferenceType);
        metadataMSA.put("type", "marginal");

        // Add the metadata to their respective graphs
        graphInferred.put("metadata", metadataInferred);
        graphMSA.put("metadata", metadataMSA);

        // Add the metadata to an array
        JSONObject combinedPoags = new JSONObject();
        // Where the graph is put in relation to each other
        combinedPoags.put("top", graphMSA);
        combinedPoags.put("bottom", graphInferred);

        // Return a string representation of this
        return combinedPoags.toString();
    }
}