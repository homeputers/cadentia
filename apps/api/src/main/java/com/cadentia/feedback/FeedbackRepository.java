package com.cadentia.feedback;

import com.cadentia.feedback.FeedbackModels.FeedbackEventRecord;
import com.cadentia.feedback.FeedbackModels.FeedbackScopeAggregate;
import com.cadentia.feedback.FeedbackModels.FeedbackResetResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeedbackRepository {
    FeedbackEventRecord createEvent(FeedbackEventRecord event);

    List<FeedbackEventRecord> listEvents(String scopeLayer, UUID scopeId, UUID arrangementId);

    Optional<FeedbackScopeAggregate> getScopeAggregate(String scopeLayer, UUID scopeId);

    FeedbackResetResult resetScope(String scopeLayer, UUID scopeId, String actorId);
}
