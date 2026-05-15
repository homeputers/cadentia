package com.cadentia.reng;

import java.util.List;

public interface CandidateRetriever {

    List<RecommendableArrangement> findCandidates(CandidateSearchCriteria criteria);
}
