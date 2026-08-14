package com.cadentia.llm;

import com.cadentia.intent.IntentValidationError;
import com.cadentia.intent.IntentValidationResult;
import com.cadentia.intent.IntentValidationService;
import com.cadentia.intent.UnsupportedRequestIntent;
import com.cadentia.intent.ValidatedIntent;
import com.cadentia.llm.prompt.IntentPromptRegistry;
import com.cadentia.llm.prompt.IntentPromptTemplate;
import java.util.List;
import java.util.UUID;
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
    private static final String PROVIDER_FAILURE_REASON_CODE = "LLM_PROVIDER_UNAVAILABLE";
    private static final String PROVIDER_FAILURE_MESSAGE = "Natural-language interpretation is unavailable right now. "
            + "Please try again later or use structured setlist constraints.";

    private final LlmClient llmClient;
    private final LlmProperties llmProperties;
    private final IntentValidationService validationService;
    private final IntentPromptRegistry promptRegistry;
    private final IntentOrchestrationObserver orchestrationObserver;

    public DefaultIntentService(
            LlmClient llmClient,
            LlmProperties llmProperties,
            IntentValidationService validationService,
            IntentPromptRegistry promptRegistry,
            IntentOrchestrationObserver orchestrationObserver) {
        this.llmClient = llmClient;
        this.llmProperties = llmProperties;
        this.validationService = validationService;
        this.promptRegistry = promptRegistry;
        this.orchestrationObserver = orchestrationObserver;
    }

    @Override
    public IntentParseResult parse(String input) {
        IntentPromptTemplate template = promptRegistry.get(IntentPromptRegistry.INTENT_V1_PROMPT_VERSION);
        String correlationId = UUID.randomUUID().toString();
        String firstResponse;
        try {
            firstResponse = llmClient.complete(buildInitialRequest(template, input, correlationId)).content();
        } catch (LlmProviderException exception) {
            return providerFailure(exception, false);
        }
        IntentValidationResult firstValidation = validationService.validate(firstResponse);
        if (firstValidation.isAccepted()) {
            IntentParseResult result = IntentParseResult.accepted(firstValidation.intent(), false);
            orchestrationObserver.recordTerminalOutcome(result.status(), result.intent().intentType(), result.retryAttempted());
            return result;
        }

        orchestrationObserver.recordFirstPassFailure(template.promptVersion(), template.schemaVersion(), firstValidation.errors());
        orchestrationObserver.recordRetryAttempt(template.promptVersion(), template.schemaVersion(), firstValidation.errors());
        logRetry(template, firstValidation.errors());
        String repairResponse;
        try {
            repairResponse = llmClient.complete(buildRepairRequest(template, input, correlationId, firstValidation.errors())).content();
        } catch (LlmProviderException exception) {
            return providerFailure(exception, true);
        }
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

    private LlmRequest buildInitialRequest(IntentPromptTemplate template, String input, String correlationId) {
        return new LlmRequest(
                template.promptVersion(),
                template.schemaVersion(),
                template.systemPrompt(),
                input,
                "",
                correlationId,
                llmProperties.generationOptions());
    }

    private LlmRequest buildRepairRequest(
            IntentPromptTemplate template,
            String input,
            String correlationId,
            List<IntentValidationError> errors) {
        String repairInstruction = "Strict repair retry. The previous response failed backend validation with error codes: "
                + summarizeErrorCodes(errors)
                + ". Return one valid JSON object only. Do not include prose, Markdown, code fences, or unsupported fields. "
                + "Do not select songs, invent songs, or make catalog claims.";
        return new LlmRequest(
                template.promptVersion(),
                template.schemaVersion(),
                template.systemPrompt(),
                input,
                repairInstruction,
                correlationId,
                llmProperties.generationOptions());
    }

    private IntentParseResult providerFailure(LlmProviderException exception, boolean retryAttempted) {
        LOGGER.warn(
                "Intent extraction provider failure errorCode={} retryAttempted={} reason={}",
                exception.errorCode(),
                retryAttempted,
                exception.getMessage());
        UnsupportedRequestIntent safeFailureIntent = new UnsupportedRequestIntent(
                IntentValidationService.CONTRACT_VERSION,
                PROVIDER_FAILURE_REASON_CODE,
                PROVIDER_FAILURE_MESSAGE);
        IntentParseResult result = IntentParseResult.safeFailure(safeFailureIntent, retryAttempted, List.of());
        orchestrationObserver.recordTerminalOutcome(result.status(), result.intent().intentType(), result.retryAttempted());
        return result;
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
