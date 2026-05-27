package com.cadentia.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.cadentia.generated.model.NaturalLanguageSetlistRequest;
import com.cadentia.generated.model.SetlistProposalResponse;
import com.cadentia.llm.DefaultIntentService;
import com.cadentia.llm.IntentOrchestrationObserver;
import com.cadentia.llm.IntentParseResult;
import com.cadentia.llm.IntentParseStatus;
import com.cadentia.llm.LlmClient;
import com.cadentia.llm.prompt.IntentPromptRegistry;
import com.cadentia.reng.SetlistService;
import com.cadentia.intent.IntentType;
import com.cadentia.intent.IntentValidationError;
import com.cadentia.intent.IntentValidationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
        LlmClient llmClient = Mockito.mock(LlmClient.class);
        if (secondReply == null) {
            when(llmClient.complete(Mockito.anyString())).thenReturn(firstReply);
        } else {
            when(llmClient.complete(Mockito.anyString())).thenReturn(firstReply, secondReply);
        }
        DefaultIntentService intentService = new DefaultIntentService(
                llmClient,
                new IntentValidationService(new ObjectMapper()),
                new IntentPromptRegistry(),
                new NoopObserver());
        CapturingSetlistService setlistService = new CapturingSetlistService();
        SetlistController controller = new SetlistController(setlistService, intentService, new ValidatedSetlistRequestMapper(),
                new ConversationSessionFacade(new com.cadentia.intent.DefaultSessionMergeService(), new ValidatedSetlistRequestMapper()));

        ResponseEntity<SetlistProposalResponse> response = controller.generateSetlistProposalFromNaturalLanguage(
                new NaturalLanguageSetlistRequest().text("help"));

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(expectedStatus);
        assertThat(setlistService.invocationCount).isZero();
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
        public com.cadentia.generated.model.SetlistProposalResponse generate(com.cadentia.generated.model.GenerateSetlistRequest request) {
            invocationCount++;
            return super.generate(request);
        }
    }
}
