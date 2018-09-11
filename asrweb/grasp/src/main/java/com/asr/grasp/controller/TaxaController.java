package com.asr.grasp.controller;

import com.asr.grasp.model.TaxaModel;
import java.util.HashMap;
import json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.asr.grasp.utils.Defines;
import java.util.ArrayList;

@Service
public class TaxaController {

    @Autowired
    TaxaModel taxaModel;

    /**
     * Gets the NCBI taxanomic information for a list of protein identifiers.
     * The identifiers can be either:
     *      1. NCBI
     *      2. PDB
     *      3. UNIPROT
     * @param ids
     * @return
     */
    public JSONObject getTaxaInfoFromProtIds(HashMap<String, ArrayList<String>> ids) {
        JSONObject taxaInfo = new JSONObject();
        for (String key: ids.keySet()) {
            taxaInfo.put(key, taxaModel.getTaxaInfoFromProtId(ids.get(key), key));
        }
        return taxaInfo;
    }

    /**
     * Gets the NCBI taxanomic IDs for a list of protein identifiers.
     *
     * We also store the mappings that exist. This allows us to add to it on the front end.
     * Also ensures we aren't unnecesarily doing inserts or queries for information we already have.
     * The identifiers can be either:
     *      1. NCBI
     *      2. PDB
     *      3. UNIPROT
     * @param ids
     * @return
     */
    public JSONObject getNonExistIdsFromProtId(HashMap<String, ArrayList<String>> ids) {
        JSONObject taxaInfo = new JSONObject();

        for (String key: ids.keySet()) {
            HashMap<String, Integer> taxaIds = taxaModel.getTaxaIdsFromProtIds(ids.get(key), key);
            ArrayList<String> oldProtList = new ArrayList<>(ids.get(key));
            if (taxaIds.size() == oldProtList.size()) {
                taxaInfo.put(key + "_mapping", taxaIds);
                taxaInfo.put(key, false);
            } else {
                oldProtList.remove(taxaIds.keySet());
                taxaInfo.put(key + "_mapping", taxaIds);
                taxaInfo.put(key, oldProtList);
            }
        }
        return taxaInfo;
    }

    /**
     * Gets the NCBI taxanomic IDs for a list of protein identifiers.
     * The identifiers can be either:
     *      1. NCBI
     *      2. PDB
     *      3. UNIPROT
     * @param ids
     * @return
     */
    public JSONObject getIdsFromProtId(HashMap<String, ArrayList<String>> ids) {
        JSONObject taxaInfo = new JSONObject();
        for (String key: ids.keySet()) {
            taxaInfo.put(key, taxaModel.getTaxaIdsFromProtIds(ids.get(key), key));
        }
        return taxaInfo;
    }

    /**
     * Gets the taxonomic information for a list of the taxonomic identifiers.
     * @param ids
     * @return
     */
    public String getTaxaInfo(ArrayList<Integer> ids) {
        return taxaModel.getTaxa(ids);
    }

    /**
     * Inserts
     * @param ids
     * @return
     */
    public String insertTaxaIds(JSONObject ids) {
        for (String type: Defines.SUPPORTED_PROT) {
            // We only want to get the mappings that we want to actually save
            String err = taxaModel.insertTaxaIdToProtId((JSONObject)ids.get(type), type);
            if (err != null) {
                return err;
            }
        }
        return null;
    }

    /**
     * ------------------------------------------------------------------------
     *          The following are to set the test env.
     * ------------------------------------------------------------------------
     */
    public void setTaxaModel(TaxaModel taxaModel) {
        this.taxaModel = taxaModel;
    }
}
