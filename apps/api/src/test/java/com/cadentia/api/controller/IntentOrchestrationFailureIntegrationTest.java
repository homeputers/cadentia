package com.cadentia.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.generated.model.GenerateSetlistRequest;
import com.cadentia.generated.model.NaturalLanguageSetlistRequest;
import com.cadentia.generated.model.SetlistProposalResponse;
import com.cadentia.llm.DefaultIntentService;
import com.cadentia.llm.IntentOrchestrationObserver;
import com.cadentia.llm.IntentParseResult;
import com.cadentia.llm.IntentParseStatus;
import com.cadentia.llm.LlmClient;
import com.cadentia.llm.LlmProperties;
import com.cadentia.llm.LlmResponse;
import com.cadentia.llm.prompt.IntentPromptRegistry;
import com.cadentia.reng.SetlistService;
import com.cadentia.reng.setlist.SetlistVersionDiffService;
import com.cadentia.reng.setlist.SetlistVersionRepository;
import com.cadentia.reng.setlist.SetlistVersionService;
import com.cadentia.intent.DefaultSessionMergeService;
import com.cadentia.intent.IntentType;
import com.cadentia.intent.IntentValidationError;
import com.cadentia.intent.IntentValidationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class IntentOrchestrationFailureIntegrationTest {

    @Test
    void malformedJsonThenMalformedJsonDoesNotInvokeRecommendation() {
        assertNoRecommendationInvocation("not-json", "still-not-json", "UNSUPPORTED_REQUEST");
    }

    @Test
    void schemaInvalidUnknownFieldThenMalformedJsonDoesNotInvokeRecommendation() {
        assertNoRecommendationInvocation(
                """
                {"intent":"GENERATE_SETLIST","slots":{"prohibitedField":true}}
                """,
                "not-json",
                "UNSUPPORTED_REQUEST");
    }

    @Test
    void selectedSongsBoundaryViolationThenMalformedJsonDoesNotInvokeRecommendation() {
        assertNoRecommendationInvocation(
                """
                {"intent":"GENERATE_SETLIST","slots":{"selectedSongs":["Invented"]}}
                """,
                "not-json",
                "UNSUPPORTED_REQUEST");
    }

    @Test
    void unsupportedIntentDoesNotInvokeRecommendation() {
        assertNoRecommendationInvocation(
                """
                {"intent":"DELETE_CATALOG_RECORD","slots":{}}
                """,
                """
                {"intent":"UNSUPPORTED_REQUEST","reasonCode":"UNSUPPORTED_ACTION","safeMessage":"cannot do that"}
                """,
                "UNSUPPORTED_REQUEST");
    }

    @Test
    void clarifyIntentDoesNotInvokeRecommendation() {
        assertNoRecommendationInvocation(
                """
                {"intent":"CLARIFY_REQUEST","reasonCode":"MISSING_REQUIRED_INFORMATION","clarificationQuestion":"Which passage?","missingSlots":["verseText"]}
                """,
                null,
                "CLARIFICATION_REQUIRED");
    }

    private void assertNoRecommendationInvocation(String firstReply, String secondReply, String expectedStatus) {
        FakeLlmClient llmClient = new FakeLlmClient();
        llmClient.enqueue(response(firstReply));
        if (secondReply == null) {
            // no retry expected
        } else {
            llmClient.enqueue(response(secondReply));
        }
        DefaultIntentService intentService = new DefaultIntentService(
                llmClient,
                new LlmProperties(),
                new IntentValidationService(new ObjectMapper()),
                new IntentPromptRegistry(),
                new NoopObserver());
        CapturingSetlistService setlistService = new CapturingSetlistService();
        SetlistController controller = new SetlistController(setlistService, intentService, new ValidatedSetlistRequestMapper(),
                new ConversationSessionFacade(new DefaultSessionMergeService(), new ValidatedSetlistRequestMapper(), Duration.ofMinutes(30), Duration.ofHours(4)),
                setlistVersionService(),
                new SetlistVersionDiffService());

        ResponseEntity<SetlistProposalResponse> response = controller.generateSetlistProposalFromNaturalLanguage(
                new NaturalLanguageSetlistRequest().text("help"));

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(expectedStatus);
        assertThat(setlistService.invocationCount).isZero();
        assertThat(llmClient.requests).hasSize(secondReply == null ? 1 : 2);
    }

    private static LlmResponse response(String content) {
        return new LlmResponse(content, "test", "fake", java.util.Map.of());
    }

    private static final class FakeLlmClient implements LlmClient {
        private final ArrayDeque<LlmResponse> replies = new ArrayDeque<>();
        private final List<com.cadentia.llm.LlmRequest> requests = new ArrayList<>();

        private void enqueue(LlmResponse response) {
            replies.add(response);
        }

        @Override
        public LlmResponse complete(com.cadentia.llm.LlmRequest request) {
            requests.add(request);
            return replies.removeFirst();
        }
    }


    private static SetlistVersionService setlistVersionService() {
        return new SetlistVersionService(new SetlistVersionRepository() {
            @Override public com.cadentia.reng.setlist.SetlistVersionModels.SetlistVersionSnapshot createBaseline(com.cadentia.reng.setlist.SetlistVersionModels.CreateSetlistBaselineCommand command) { throw new UnsupportedOperationException(); }
            @Override public com.cadentia.reng.setlist.SetlistVersionModels.SetlistVersionSnapshot createEditedVersion(com.cadentia.reng.setlist.SetlistVersionModels.CreateSetlistVersionCommand command) { throw new UnsupportedOperationException(); }
            @Override public java.util.Optional<com.cadentia.reng.setlist.SetlistVersionModels.SetlistVersionSnapshot> findVersion(java.util.UUID setlistId, java.util.UUID versionId) { return java.util.Optional.empty(); }
            @Override public java.util.List<com.cadentia.reng.setlist.SetlistVersionModels.SetlistVersionSnapshot> findVersions(java.util.UUID setlistId) { return java.util.List.of(); }
        });
    }

    private static final class NoopObserver implements IntentOrchestrationObserver {
        @Override
        public void recordFirstPassFailure(String promptVersion, String schemaVersion, List<IntentValidationError> errors) {}
        @Override
        public void recordRetryAttempt(String promptVersion, String schemaVersion, List<IntentValidationError> firstPassErrors) {}
        @Override
        public void recordRetryOutcome(String promptVersion, String schemaVersion, boolean success, List<IntentValidationError> retryErrors) {}
        @Override
        public void recordTerminalOutcome(IntentParseStatus status, IntentType intentType, boolean retryAttempted) {}
    }

    private static class CapturingSetlistService extends SetlistService {
        private int invocationCount;

        @Override
        public SetlistProposalResponse generate(GenerateSetlistRequest request) {
            invocationCount++;
            return super.generate(request);
        }
    }
}
