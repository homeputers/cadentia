package com.cadentia.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.intent.ClarifyRequestIntent;
import com.cadentia.intent.Counts;
import com.cadentia.intent.GenerateSetlistIntent;
import com.cadentia.intent.IntentType;
import com.cadentia.intent.IntentValidationErrorCode;
import com.cadentia.intent.IntentValidationService;
import com.cadentia.intent.UnsupportedRequestIntent;
import com.cadentia.llm.prompt.IntentPromptRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultIntentServiceTest {

    private FakeLlmClient llmClient;
    private DefaultIntentService intentService;
    private RecordingObserver observer;

    @BeforeEach
    void setUp() {
        llmClient = new FakeLlmClient();
        observer = new RecordingObserver();
        intentService = new DefaultIntentService(
                llmClient,
                new LlmProperties(),
                new IntentValidationService(new ObjectMapper()),
                new IntentPromptRegistry(),
                observer);
    }

    @Test
    void parseReturnsFirstPassSuccessWithoutRetry() {
        // Arrange
        llmClient.enqueue(response("""
                {
                  "intent": "GENERATE_SETLIST",
                  "slots": {
                    "themeHints": ["joy"],
                    "counts": { "praise": 3, "worship": 2 }
                  }
                }
                """));

        // Act
        IntentParseResult result = intentService.parse("Build a short joyful setlist.");

        // Assert
        assertThat(result.status()).isEqualTo(IntentParseStatus.ACCEPTED);
        assertThat(result.retryAttempted()).isFalse();
        assertThat(result.errors()).isEmpty();
        assertThat(result.intent()).isInstanceOfSatisfying(GenerateSetlistIntent.class, intent -> {
            assertThat(intent.intentType()).isEqualTo(IntentType.GENERATE_SETLIST);
            assertThat(intent.slots().themeHints()).containsExactly("joy");
            assertThat(intent.slots().counts()).isEqualTo(new Counts(3, 2));
        });
        assertThat(llmClient.requests.get(0).systemPrompt())
                .contains("Return JSON only")
                .doesNotContain("Build a short joyful setlist.");
        assertThat(llmClient.requests.get(0).userMessage())
                .contains("User request to extract into JSON")
                .contains("Build a short joyful setlist.");
        assertThat(llmClient.requests.get(0).promptVersion()).isEqualTo("intent-v1");
        assertThat(llmClient.requests.get(0).schemaVersion()).isEqualTo("v1");
        assertThat(llmClient.requests).hasSize(1);
        assertThat(observer.terminalOutcomes).containsExactly("ACCEPTED:GENERATE_SETLIST:false");
    }

    @Test
    void parseRetriesExactlyOnceAfterMalformedJsonAndReturnsRetrySuccess() {
        // Arrange
        llmClient.enqueue(response("Here is the JSON: { bad"));
        llmClient.enqueue(response("""
                        {
                          "intent": "GENERATE_SETLIST",
                          "slots": {
                            "scriptureReferences": ["Psalm 100"],
                            "themeHints": ["thanksgiving"]
                          }
                        }
                        """));

        // Act
        IntentParseResult result = intentService.parse("Psalm 100 thanksgiving set.");

        // Assert
        assertThat(result.status()).isEqualTo(IntentParseStatus.ACCEPTED);
        assertThat(result.retryAttempted()).isTrue();
        assertThat(result.intent()).isInstanceOfSatisfying(GenerateSetlistIntent.class, intent -> {
            assertThat(intent.slots().scriptureReferences()).containsExactly("Psalm 100");
            assertThat(intent.slots().themeHints()).containsExactly("thanksgiving");
        });
        assertThat(llmClient.requests).hasSize(2);
        assertThat(llmClient.requests.get(1).userMessage())
                .contains("Strict repair retry")
                .contains("MALFORMED_JSON")
                .contains("Return one valid JSON object only");
        assertThat(llmClient.requests.get(1).correlationId())
                .isEqualTo(llmClient.requests.get(0).correlationId());
        assertThat(observer.firstPassFailures).containsExactly("MALFORMED_JSON");
        assertThat(observer.retryOutcomes).containsExactly("true:");
        assertThat(observer.terminalOutcomes).containsExactly("ACCEPTED:GENERATE_SETLIST:true");
    }

    @Test
    void parseRetriesExactlyOnceAfterSchemaViolationAndReturnsSafeFailureWhenRepairIsInvalid() {
        // Arrange
        llmClient.enqueue(response("""
                        {
                          "intent": "GENERATE_SETLIST",
                          "slots": {
                            "selectedSongs": ["Invented Song"]
                          }
                        }
                        """));
        llmClient.enqueue(response("still not json"));

        // Act
        IntentParseResult result = intentService.parse("Give me Invented Song as the opener.");

        // Assert
        assertThat(result.status()).isEqualTo(IntentParseStatus.SAFE_FAILURE);
        assertThat(result.retryAttempted()).isTrue();
        assertThat(result.intent()).isInstanceOfSatisfying(UnsupportedRequestIntent.class, intent -> {
            assertThat(intent.intentType()).isEqualTo(IntentType.UNSUPPORTED_REQUEST);
            assertThat(intent.reasonCode()).isEqualTo("UNSUPPORTED_INTENT");
            assertThat(intent.safeMessage()).contains("could not safely understand");
        });
        assertThat(result.errors())
                .singleElement()
                .satisfies(error -> assertThat(error.code()).isEqualTo(IntentValidationErrorCode.MALFORMED_JSON));
        assertThat(llmClient.requests).hasSize(2);
        assertThat(llmClient.requests.get(1).userMessage())
                .contains("UNSUPPORTED_FIELD")
                .doesNotContain("selectedSongs");
        assertThat(observer.retryOutcomes).containsExactly("false:MALFORMED_JSON");
        assertThat(observer.terminalOutcomes).containsExactly("SAFE_FAILURE:UNSUPPORTED_REQUEST:true");
    }

    @Test
    void parseReturnsStructuredClarifyOutcome() {
        // Arrange
        llmClient.enqueue(response("""
                {
                  "intent": "CLARIFY_REQUEST",
                  "reasonCode": "MISSING_REQUIRED_INFORMATION",
                  "clarificationQuestion": "Which scripture or theme should the setlist focus on?",
                  "missingSlots": ["verseText", "scriptureReferences"]
                }
                """));

        // Act
        IntentParseResult result = intentService.parse("Make me a setlist.");

        // Assert
        assertThat(result.status()).isEqualTo(IntentParseStatus.ACCEPTED);
        assertThat(result.retryAttempted()).isFalse();
        assertThat(result.intent()).isInstanceOfSatisfying(ClarifyRequestIntent.class, intent -> {
            assertThat(intent.intentType()).isEqualTo(IntentType.CLARIFY_REQUEST);
            assertThat(intent.reasonCode()).isEqualTo("MISSING_REQUIRED_INFORMATION");
            assertThat(intent.missingSlots()).containsExactly("verseText", "scriptureReferences");
        });
        assertThat(llmClient.requests).hasSize(1);
        assertThat(observer.terminalOutcomes).containsExactly("ACCEPTED:CLARIFY_REQUEST:false");
    }

    @Test
    void parseReturnsStructuredUnsupportedOutcome() {
        // Arrange
        llmClient.enqueue(response("""
                {
                  "intent": "UNSUPPORTED_REQUEST",
                  "reasonCode": "UNSUPPORTED_ACTION",
                  "safeMessage": "I cannot approve songs or update catalog records."
                }
                """));

        // Act
        IntentParseResult result = intentService.parse("Approve this new catalog song.");

        // Assert
        assertThat(result.status()).isEqualTo(IntentParseStatus.ACCEPTED);
        assertThat(result.retryAttempted()).isFalse();
        assertThat(result.intent()).isInstanceOfSatisfying(UnsupportedRequestIntent.class, intent -> {
            assertThat(intent.intentType()).isEqualTo(IntentType.UNSUPPORTED_REQUEST);
            assertThat(intent.reasonCode()).isEqualTo("UNSUPPORTED_ACTION");
            assertThat(intent.safeMessage()).contains("cannot approve songs");
        });
        assertThat(llmClient.requests).hasSize(1);
        assertThat(observer.terminalOutcomes).containsExactly("ACCEPTED:UNSUPPORTED_REQUEST:false");
    }

    @Test
    void parseReturnsSafeFailureWhenProviderIsDisabled() {
        // Arrange
        llmClient.enqueue(new LlmProviderException(LlmProviderErrorCode.DISABLED, "disabled"));

        // Act
        IntentParseResult result = intentService.parse("Build a setlist from Psalm 23.");

        // Assert
        assertThat(result.status()).isEqualTo(IntentParseStatus.SAFE_FAILURE);
        assertThat(result.retryAttempted()).isFalse();
        assertThat(result.errors()).isEmpty();
        assertThat(result.intent()).isInstanceOfSatisfying(UnsupportedRequestIntent.class, intent -> {
            assertThat(intent.intentType()).isEqualTo(IntentType.UNSUPPORTED_REQUEST);
            assertThat(intent.reasonCode()).isEqualTo("LLM_PROVIDER_UNAVAILABLE");
            assertThat(intent.safeMessage()).contains("Natural-language interpretation is unavailable");
        });
        assertThat(llmClient.requests).hasSize(1);
        assertThat(observer.terminalOutcomes).containsExactly("SAFE_FAILURE:UNSUPPORTED_REQUEST:false");
    }

    private LlmResponse response(String content) {
        return new LlmResponse(content, "test", "fake", java.util.Map.of());
    }

    private static final class FakeLlmClient implements LlmClient {
        private final ArrayDeque<Object> replies = new ArrayDeque<>();
        private final List<LlmRequest> requests = new ArrayList<>();

        private void enqueue(LlmResponse response) {
            replies.add(response);
        }

        private void enqueue(LlmProviderException exception) {
            replies.add(exception);
        }

        @Override
        public LlmResponse complete(LlmRequest request) {
            requests.add(request);
            Object reply = replies.removeFirst();
            if (reply instanceof LlmProviderException exception) {
                throw exception;
            }
            return (LlmResponse) reply;
        }
    }

    private static final class RecordingObserver implements IntentOrchestrationObserver {

        private final java.util.List<String> firstPassFailures = new java.util.ArrayList<>();
        private final java.util.List<String> retryOutcomes = new java.util.ArrayList<>();
        private final java.util.List<String> terminalOutcomes = new java.util.ArrayList<>();

        @Override
        public void recordFirstPassFailure(String promptVersion, String schemaVersion, java.util.List<com.cadentia.intent.IntentValidationError> errors) {
            firstPassFailures.add(summarize(errors));
        }

        @Override
        public void recordRetryAttempt(String promptVersion, String schemaVersion, java.util.List<com.cadentia.intent.IntentValidationError> firstPassErrors) {
            // no-op for this test helper
        }

        @Override
        public void recordRetryOutcome(String promptVersion, String schemaVersion, boolean success, java.util.List<com.cadentia.intent.IntentValidationError> retryErrors) {
            retryOutcomes.add(success + ":" + summarize(retryErrors));
        }

        @Override
        public void recordTerminalOutcome(IntentParseStatus status, IntentType intentType, boolean retryAttempted) {
            terminalOutcomes.add(status + ":" + intentType + ":" + retryAttempted);
        }

        private String summarize(java.util.List<com.cadentia.intent.IntentValidationError> errors) {
            return errors.stream().map(error -> error.code().name()).distinct().collect(java.util.stream.Collectors.joining(","));
        }
    }
}
