package com.cadentia.feedback;

import com.cadentia.feedback.FeedbackModels.FeedbackEventRecord;
import com.cadentia.feedback.FeedbackModels.FeedbackResetResult;
import com.cadentia.feedback.FeedbackModels.FeedbackScopeAggregate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class FeedbackService {
    private final FeedbackRepository feedbackRepository;
    private final FeedbackObservabilityRecorder observabilityRecorder;

    public FeedbackService(FeedbackRepository feedbackRepository, FeedbackObservabilityRecorder observabilityRecorder) {
        this.feedbackRepository = feedbackRepository;
        this.observabilityRecorder = observabilityRecorder;
    }

    public FeedbackEventRecord createEvent(FeedbackEventRecord eventRecord) {
        FeedbackEventRecord saved = feedbackRepository.createEvent(eventRecord);
        observabilityRecorder.recordEventIngested(saved);
        return saved;
    }

    public List<FeedbackEventRecord> listEvents(String scopeLayer, UUID scopeId, UUID arrangementId) {
        return feedbackRepository.listEvents(scopeLayer, scopeId, arrangementId);
    }

    public FeedbackScopeAggregate getScopeStateWithFallback(String scopeLayer, UUID scopeId) {
        Optional<FeedbackScopeAggregate> current = feedbackRepository.getScopeAggregate(scopeLayer, scopeId);
        if (current.isPresent()) {
            observabilityRecorder.recordScopeStateRead(scopeLayer, scopeId, false);
            return current.get();
        }
        observabilityRecorder.recordScopeStateRead(scopeLayer, scopeId, true);
        return new FeedbackScopeAggregate(scopeLayer, scopeId, 0, 0, 0, 0, Map.of(), null);
    }

    public FeedbackResetResult resetScope(String scopeLayer, UUID scopeId, String actorId) {
        FeedbackResetResult result = feedbackRepository.resetScope(scopeLayer, scopeId, actorId);
        observabilityRecorder.recordScopeReset(result);
        return result;
    }
}
