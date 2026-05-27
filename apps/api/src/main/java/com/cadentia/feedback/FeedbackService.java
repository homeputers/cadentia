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

    public FeedbackService(FeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    public FeedbackEventRecord createEvent(FeedbackEventRecord eventRecord) {
        return feedbackRepository.createEvent(eventRecord);
    }

    public List<FeedbackEventRecord> listEvents(String scopeLayer, UUID scopeId, UUID arrangementId) {
        return feedbackRepository.listEvents(scopeLayer, scopeId, arrangementId);
    }

    public FeedbackScopeAggregate getScopeStateWithFallback(String scopeLayer, UUID scopeId) {
        Optional<FeedbackScopeAggregate> current = feedbackRepository.getScopeAggregate(scopeLayer, scopeId);
        if (current.isPresent()) {
            return current.get();
        }
        return new FeedbackScopeAggregate(scopeLayer, scopeId, 0, 0, 0, 0, Map.of(), null);
    }

    public FeedbackResetResult resetScope(String scopeLayer, UUID scopeId, String actorId) {
        return feedbackRepository.resetScope(scopeLayer, scopeId, actorId);
    }
}
