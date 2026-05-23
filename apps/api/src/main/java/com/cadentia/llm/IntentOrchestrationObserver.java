package com.cadentia.llm;

import com.cadentia.intent.IntentType;
import com.cadentia.intent.IntentValidationError;
import java.util.List;

public interface IntentOrchestrationObserver {

    void recordFirstPassFailure(String promptVersion, String schemaVersion, List<IntentValidationError> errors);

    void recordRetryAttempt(String promptVersion, String schemaVersion, List<IntentValidationError> firstPassErrors);

    void recordRetryOutcome(String promptVersion, String schemaVersion, boolean success, List<IntentValidationError> retryErrors);

    void recordTerminalOutcome(IntentParseStatus status, IntentType intentType, boolean retryAttempted);
}
