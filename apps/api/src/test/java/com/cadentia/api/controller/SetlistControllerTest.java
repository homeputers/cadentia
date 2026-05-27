package com.cadentia.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.generated.model.GenerateSetlistRequest;
import com.cadentia.generated.model.NaturalLanguageSetlistRequest;
import com.cadentia.generated.model.SetlistProposalResponse;
import com.cadentia.intent.ClarifyRequestIntent;
import com.cadentia.intent.Counts;
import com.cadentia.intent.GenerateSetlistIntent;
import com.cadentia.intent.GenerateSetlistSlots;
import com.cadentia.intent.IntentKeyPolicy;
import com.cadentia.intent.IntentTempoPolicy;
import com.cadentia.intent.UnsupportedRequestIntent;
import com.cadentia.llm.IntentParseResult;
import com.cadentia.llm.IntentService;
import com.cadentia.reng.SetlistService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class SetlistControllerTest {

    @Test
    void naturalLanguageRequestInvokesRecommendationOnlyForValidGenerateSetlistIntent() {
        // Arrange
        GenerateSetlistIntent intent = new GenerateSetlistIntent("v1", new GenerateSetlistSlots(
                "Psalm 100",
                List.of("Psalm 100"),
                List.of("thanksgiving"),
                new Counts(4, 2),
                new IntentKeyPolicy(true, true, 2),
                new IntentTempoPolicy(12),
                null,
                "rising",
                List.of("Song To Exclude"),
                "opening"));
        StubIntentService intentService = new StubIntentService(IntentParseResult.accepted(intent, false));
        CapturingSetlistService setlistService = new CapturingSetlistService();
        SetlistController controller = new SetlistController(
                setlistService,
                intentService,
                new ValidatedSetlistRequestMapper(),
                new ConversationSessionFacade(new com.cadentia.intent.DefaultSessionMergeService(), new ValidatedSetlistRequestMapper()));

        // Act
        ResponseEntity<SetlistProposalResponse> response = controller.generateSetlistProposalFromNaturalLanguage(
                new NaturalLanguageSetlistRequest().text("Psalm 100 thanksgiving, exclude Song To Exclude."));

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("PENDING_CATALOG_IMPLEMENTATION");
        assertThat(intentService.lastInput).isEqualTo("Psalm 100 thanksgiving, exclude Song To Exclude.");
        assertThat(setlistService.invocationCount).isEqualTo(1);
        assertThat(setlistService.lastRequest.getVerseText()).isEqualTo("Psalm 100");
        assertThat(setlistService.lastRequest.getScriptureReferences()).containsExactly("Psalm 100");
        assertThat(setlistService.lastRequest.getExcludedSongs()).containsExactly("Song To Exclude");
        assertThat(setlistService.lastRequest.toString()).doesNotContain("selectedSongs");
    }

    @Test
    void naturalLanguageClarificationDoesNotInvokeRecommendation() {
        // Arrange
        ClarifyRequestIntent intent = new ClarifyRequestIntent(
                "v1",
                "MISSING_REQUIRED_INFORMATION",
                "Which scripture or theme should the setlist focus on?",
                List.of("verseText", "scriptureReferences"));
        StubIntentService intentService = new StubIntentService(IntentParseResult.accepted(intent, false));
        CapturingSetlistService setlistService = new CapturingSetlistService();
        SetlistController controller = new SetlistController(
                setlistService,
                intentService,
                new ValidatedSetlistRequestMapper(),
                new ConversationSessionFacade(new com.cadentia.intent.DefaultSessionMergeService(), new ValidatedSetlistRequestMapper()));

        // Act
        ResponseEntity<SetlistProposalResponse> response = controller.generateSetlistProposalFromNaturalLanguage(
                new NaturalLanguageSetlistRequest().text("Make me a setlist."));

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("CLARIFICATION_REQUIRED");
        assertThat(response.getBody().getAuditMessages())
                .contains("Intent extraction requested clarification before recommendation.")
                .contains("Which scripture or theme should the setlist focus on?")
                .contains("Missing slots: verseText, scriptureReferences.");
        assertThat(setlistService.invocationCount).isZero();
    }

    @Test
    void naturalLanguageUnsupportedRequestDoesNotInvokeRecommendation() {
        // Arrange
        UnsupportedRequestIntent intent = new UnsupportedRequestIntent(
                "v1",
                "UNSUPPORTED_ACTION",
                "I cannot approve songs or update catalog records.");
        StubIntentService intentService = new StubIntentService(IntentParseResult.accepted(intent, false));
        CapturingSetlistService setlistService = new CapturingSetlistService();
        SetlistController controller = new SetlistController(
                setlistService,
                intentService,
                new ValidatedSetlistRequestMapper(),
                new ConversationSessionFacade(new com.cadentia.intent.DefaultSessionMergeService(), new ValidatedSetlistRequestMapper()));

        // Act
        ResponseEntity<SetlistProposalResponse> response = controller.generateSetlistProposalFromNaturalLanguage(
                new NaturalLanguageSetlistRequest().text("Approve this new catalog song."));

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("UNSUPPORTED_REQUEST");
        assertThat(response.getBody().getAuditMessages())
                .contains("Intent extraction rejected the request before recommendation.")
                .contains("I cannot approve songs or update catalog records.");
        assertThat(setlistService.invocationCount).isZero();
    }

    private static class StubIntentService implements IntentService {

        private final IntentParseResult result;
        private String lastInput;

        StubIntentService(IntentParseResult result) {
            this.result = result;
        }

        @Override
        public IntentParseResult parse(String input) {
            lastInput = input;
            return result;
        }
    }

    private static class CapturingSetlistService extends SetlistService {

        private int invocationCount;
        private GenerateSetlistRequest lastRequest;

        @Override
        public SetlistProposalResponse generate(GenerateSetlistRequest request) {
            invocationCount++;
            lastRequest = request;
            return new SetlistProposalResponse()
                    .status("PENDING_CATALOG_IMPLEMENTATION")
                    .auditMessages(List.of("Recommendation Engine scaffold accepted the structured request."));
        }
    }
}
