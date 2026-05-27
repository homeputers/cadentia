package com.cadentia.feedback;

import com.cadentia.feedback.FeedbackModels.FeedbackEventRecord;
import com.cadentia.feedback.FeedbackModels.FeedbackResetResult;
import java.util.Map;
import java.util.UUID;

public interface FeedbackObservabilityRecorder {

    void recordEventIngested(FeedbackEventRecord eventRecord);

    void recordScopeStateRead(String scopeLayer, UUID scopeId, boolean fallbackReturned);

    void recordScopeReset(FeedbackResetResult resetResult);

    void recordRankingImpactDistribution(Map<UUID, Double> feedbackContributions);
}
