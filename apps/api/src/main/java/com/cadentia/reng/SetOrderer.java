package com.cadentia.reng;

import com.cadentia.reng.scoring.CandidateFeatureScorer;
import com.cadentia.reng.scoring.OrderedSetResponse;
import com.cadentia.reng.scoring.ScoringProfile;
import com.cadentia.reng.scoring.ScoringRequest;
import java.util.List;

public interface SetOrderer {

    OrderedSetResponse order(
            List<CandidateFeatureScorer.CandidateFeatureScore> candidateScores,
            ScoringRequest request,
            ScoringProfile profile,
            String candidateSnapshotVersion);
}
