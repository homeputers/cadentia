package com.cadentia.llm;

import com.cadentia.intent.IntentType;
import com.cadentia.intent.IntentValidationError;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingIntentOrchestrationObserver implements IntentOrchestrationObserver {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingIntentOrchestrationObserver.class);

    @Override
    public void recordFirstPassFailure(String promptVersion, String schemaVersion, List<IntentValidationError> errors) {
        LOGGER.info(
                "intent_orchestration_event event=first_pass_failure promptVersion={} schemaVersion={} errorCodes={}",
                promptVersion,
                schemaVersion,
                summarizeErrorCodes(errors));
    }

    @Override
    public void recordRetryAttempt(String promptVersion, String schemaVersion, List<IntentValidationError> firstPassErrors) {
        LOGGER.info(
                "intent_orchestration_event event=retry_attempt promptVersion={} schemaVersion={} errorCodes={}",
                promptVersion,
                schemaVersion,
                summarizeErrorCodes(firstPassErrors));
    }

    @Override
    public void recordRetryOutcome(String promptVersion, String schemaVersion, boolean success, List<IntentValidationError> retryErrors) {
        LOGGER.info(
                "intent_orchestration_event event=retry_outcome promptVersion={} schemaVersion={} success={} errorCodes={}",
                promptVersion,
                schemaVersion,
                success,
                summarizeErrorCodes(retryErrors));
    }

    @Override
    public void recordTerminalOutcome(IntentParseStatus status, IntentType intentType, boolean retryAttempted) {
        LOGGER.info(
                "intent_orchestration_event event=terminal_outcome status={} intentType={} retryAttempted={}",
                status,
                intentType,
                retryAttempted);
    }

    private String summarizeErrorCodes(List<IntentValidationError> errors) {
        return errors.stream().map(error -> error.code().name()).distinct().collect(Collectors.joining(","));
    }
}
