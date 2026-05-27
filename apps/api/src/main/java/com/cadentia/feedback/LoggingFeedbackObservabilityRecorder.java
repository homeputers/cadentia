package com.cadentia.feedback;

import com.cadentia.feedback.FeedbackModels.FeedbackEventRecord;
import com.cadentia.feedback.FeedbackModels.FeedbackResetResult;
import java.util.DoubleSummaryStatistics;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingFeedbackObservabilityRecorder implements FeedbackObservabilityRecorder {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingFeedbackObservabilityRecorder.class);

    @Override
    public void recordEventIngested(FeedbackEventRecord eventRecord) {
        LOGGER.info(
                "feedback_observability event=feedback_ingested outcome={} scopeLayer={} actorId={} replacementReason={} familiarityScore={}",
                eventRecord.outcome(),
                eventRecord.scopeLayer(),
                eventRecord.actorId(),
                eventRecord.replacementReason(),
                eventRecord.familiarityScore());
    }

    @Override
    public void recordScopeStateRead(String scopeLayer, UUID scopeId, boolean fallbackReturned) {
        LOGGER.info(
                "feedback_observability event=scope_state_read scopeLayer={} scopeId={} fallbackReturned={}",
                scopeLayer,
                scopeId,
                fallbackReturned);
    }

    @Override
    public void recordScopeReset(FeedbackResetResult resetResult) {
        LOGGER.info(
                "feedback_observability event=scope_reset scopeLayer={} scopeId={} actorId={} auditReference={}",
                resetResult.scopeLayer(),
                resetResult.scopeId(),
                resetResult.actorId(),
                resetResult.auditReference());
    }

    @Override
    public void recordRankingImpactDistribution(Map<UUID, Double> feedbackContributions) {
        DoubleSummaryStatistics stats = feedbackContributions.values().stream().mapToDouble(Double::doubleValue).summaryStatistics();
        LOGGER.info(
                "feedback_observability event=ranking_impact_distribution candidates={} min={} max={} avg={} sum={}",
                feedbackContributions.size(),
                stats.getCount() == 0 ? 0.0d : stats.getMin(),
                stats.getCount() == 0 ? 0.0d : stats.getMax(),
                stats.getCount() == 0 ? 0.0d : stats.getAverage(),
                stats.getSum());
    }
}
