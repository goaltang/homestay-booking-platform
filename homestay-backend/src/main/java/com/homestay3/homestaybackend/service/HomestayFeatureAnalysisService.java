package com.homestay3.homestaybackend.service;

import com.homestay3.homestaybackend.dto.SuggestedFeatureDTO;
import com.homestay3.homestaybackend.entity.Homestay;

import java.util.List;

public interface HomestayFeatureAnalysisService {

    /**
     * Analyzes a homestay entity to extract key features.
     *
     * @param homestay The Homestay entity to analyze.
     * @param referringSearchCriteria A list of search criteria strings that led to this homestay, used to boost matching features.
     * @return A list of SuggestedFeatureDTO representing the most prominent features.
     */
    List<SuggestedFeatureDTO> analyzeFeatures(Homestay homestay, List<String> referringSearchCriteria);
}
