package com.cadentia.llm;

import com.cadentia.intent.IntentValidationError;
import com.cadentia.intent.IntentValidationResult;
import com.cadentia.intent.IntentValidationService;
import com.cadentia.intent.UnsupportedRequestIntent;
import com.cadentia.intent.ValidatedIntent;
import com.cadentia.llm.prompt.IntentPromptRegistry;
import com.cadentia.llm.prompt.IntentPromptTemplate;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DefaultIntentService implements IntentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultIntentService.class);
    private static final String SAFE_FAILURE_REASON_CODE = "UNSUPPORTED_INTENT";
    private static final String SAFE_FAILURE_MESSAGE = "I could not safely understand that request. "
            + "Please rephrase it with a scripture, theme, or setlist constraint.";

    private final LlmClient llmClient;
    private final IntentValidationService validationService;
    private final IntentPromptRegistry promptRegistry;
    private final IntentOrchestrationObserver orchestrationObserver;

    public DefaultIntentService(
            LlmClient llmClient,
            IntentValidationService validationService,
            IntentPromptRegistry promptRegistry,
            IntentOrchestrationObserver orchestrationObserver) {
        this.llmClient = llmClient;
        this.validationService = validationService;
        this.promptRegistry = promptRegistry;
        this.orchestrationObserver = orchestrationObserver;
    }

    @Override
    public IntentParseResult parse(String input) {
        IntentPromptTemplate template = promptRegistry.get(IntentPromptRegistry.INTENT_V1_PROMPT_VERSION);
        String firstResponse = llmClient.complete(buildInitialPrompt(template, input));
        IntentValidationResult firstValidation = validationService.validate(firstResponse);
        if (firstValidation.isAccepted()) {
            IntentParseResult result = IntentParseResult.accepted(firstValidation.intent(), false);
            orchestrationObserver.recordTerminalOutcome(result.status(), result.intent().intentType(), result.retryAttempted());
            return result;
        }

        orchestrationObserver.recordFirstPassFailure(template.promptVersion(), template.schemaVersion(), firstValidation.errors());
        orchestrationObserver.recordRetryAttempt(template.promptVersion(), template.schemaVersion(), firstValidation.errors());
        logRetry(template, firstValidation.errors());
        String repairResponse = llmClient.complete(buildRepairPrompt(template, input, firstValidation.errors()));
        IntentValidationResult repairValidation = validationService.validate(repairResponse);
        if (repairValidation.isAccepted()) {
            orchestrationObserver.recordRetryOutcome(template.promptVersion(), template.schemaVersion(), true, List.of());
            IntentParseResult result = IntentParseResult.accepted(repairValidation.intent(), true);
            orchestrationObserver.recordTerminalOutcome(result.status(), result.intent().intentType(), result.retryAttempted());
            return result;
        }

        orchestrationObserver.recordRetryOutcome(template.promptVersion(), template.schemaVersion(), false, repairValidation.errors());
        logSafeFailure(template, repairValidation.errors());
        UnsupportedRequestIntent safeFailureIntent = new UnsupportedRequestIntent(
                IntentValidationService.CONTRACT_VERSION,
                SAFE_FAILURE_REASON_CODE,
                SAFE_FAILURE_MESSAGE);
        IntentParseResult result = IntentParseResult.safeFailure(safeFailureIntent, repairValidation.errors());
        orchestrationObserver.recordTerminalOutcome(result.status(), result.intent().intentType(), result.retryAttempted());
        return result;
    }

    private String buildInitialPrompt(IntentPromptTemplate template, String input) {
        return template.systemPrompt()
                + "\n\nUser request to extract into JSON:\n"
                + input;
    }

    private String buildRepairPrompt(
            IntentPromptTemplate template,
            String input,
            List<IntentValidationError> errors) {
        return template.systemPrompt()
                + "\n\nStrict repair retry. The previous response failed backend validation with error codes: "
                + summarizeErrorCodes(errors)
                + ". Return one valid JSON object only. Do not include prose, Markdown, code fences, or unsupported fields. "
                + "Do not select songs, invent songs, or make catalog claims.\n\n"
                + "User request to extract into JSON:\n"
                + input;
    }

    private void logRetry(IntentPromptTemplate template, List<IntentValidationError> errors) {
        LOGGER.warn(
                "Intent extraction retry requested promptVersion={} schemaVersion={} errorCodes={}",
                template.promptVersion(),
                template.schemaVersion(),
                summarizeErrorCodes(errors));
    }

    private void logSafeFailure(IntentPromptTemplate template, List<IntentValidationError> errors) {
        LOGGER.warn(
                "Intent extraction safe failure promptVersion={} schemaVersion={} errorCodes={}",
                template.promptVersion(),
                template.schemaVersion(),
                summarizeErrorCodes(errors));
    }

    private String summarizeErrorCodes(List<IntentValidationError> errors) {
        return errors.stream()
                .map(error -> error.code().name())
                .distinct()
                .collect(Collectors.joining(","));
    }
}
