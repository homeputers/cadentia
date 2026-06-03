package com.cadentia.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.api.security.RbacAuthorities;
import com.cadentia.generated.model.GenerateSetlistRequest;
import com.cadentia.generated.model.KeyPolicy;
import com.cadentia.generated.model.NaturalLanguageSetlistRequest;
import com.cadentia.generated.model.SetlistCounts;
import com.cadentia.generated.model.SetlistProposalResponse;
import com.cadentia.generated.model.TempoPolicy;
import com.cadentia.intent.ClarifyRequestIntent;
import com.cadentia.intent.Counts;
import com.cadentia.intent.DefaultSessionMergeService;
import com.cadentia.intent.GenerateSetlistIntent;
import com.cadentia.intent.GenerateSetlistSlots;
import com.cadentia.intent.IntentKeyPolicy;
import com.cadentia.intent.IntentTempoPolicy;
import com.cadentia.intent.UnsupportedRequestIntent;
import com.cadentia.llm.IntentParseResult;
import com.cadentia.llm.IntentService;
import com.cadentia.reng.SetlistService;
import com.cadentia.reng.setlist.SetlistVersionDiffService;
import com.cadentia.reng.setlist.SetlistVersionRepository;
import com.cadentia.reng.setlist.SetlistVersionService;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class SetlistControllerTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

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
                new ConversationSessionFacade(new DefaultSessionMergeService(), new ValidatedSetlistRequestMapper(), Duration.ofMinutes(30), Duration.ofHours(4)),
                setlistVersionService(),
                new SetlistVersionDiffService());

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
                new ConversationSessionFacade(new DefaultSessionMergeService(), new ValidatedSetlistRequestMapper(), Duration.ofMinutes(30), Duration.ofHours(4)),
                setlistVersionService(),
                new SetlistVersionDiffService());

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
                new ConversationSessionFacade(new DefaultSessionMergeService(), new ValidatedSetlistRequestMapper(), Duration.ofMinutes(30), Duration.ofHours(4)),
                setlistVersionService(),
                new SetlistVersionDiffService());

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

    @Test
    void structuredRequestRejectsUnauthorizedAdminDiagnostics() {
        // Arrange
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "leader",
                "n/a",
                List.of(new SimpleGrantedAuthority(RbacAuthorities.ROLE_WORSHIP_LEADER))));
        CapturingSetlistService setlistService = new CapturingSetlistService();
        SetlistController controller = new SetlistController(
                setlistService,
                new StubIntentService(IntentParseResult.accepted(new UnsupportedRequestIntent("v1", "unused", "unused"), false)),
                new ValidatedSetlistRequestMapper(),
                new ConversationSessionFacade(new DefaultSessionMergeService(), new ValidatedSetlistRequestMapper(), Duration.ofMinutes(30), Duration.ofHours(4)),
                setlistVersionService(),
                new SetlistVersionDiffService());
        GenerateSetlistRequest request = new GenerateSetlistRequest()
                .verseText("Psalm 100")
                .counts(new SetlistCounts().praise(1).worship(0))
                .keyPolicy(new KeyPolicy()
                        .preferSameKey(true)
                        .allowRelativeMajorMinor(true)
                        .maxKeyCenters(2))
                .tempoPolicy(new TempoPolicy().maxJumpBpm(12))
                .explanationAudience(GenerateSetlistRequest.ExplanationAudienceEnum.ADMIN)
                .includeAdminDiagnostics(true);

        // Act
        ResponseEntity<SetlistProposalResponse> response = controller.generateSetlistProposal(request);

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(setlistService.invocationCount).isZero();
        assertThat(request.getIncludeAdminDiagnostics()).isFalse();
        assertThat(request.getExplanationAudience()).isEqualTo(GenerateSetlistRequest.ExplanationAudienceEnum.PUBLIC);
    }

    @Test
    void structuredRequestAllowsAuthorizedAdminDiagnosticsWithoutChangingServicePath() {
        // Arrange
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin",
                "n/a",
                List.of(new SimpleGrantedAuthority(RbacAuthorities.ROLE_ADMIN))));
        CapturingSetlistService setlistService = new CapturingSetlistService();
        SetlistController controller = new SetlistController(
                setlistService,
                new StubIntentService(IntentParseResult.accepted(new UnsupportedRequestIntent("v1", "unused", "unused"), false)),
                new ValidatedSetlistRequestMapper(),
                new ConversationSessionFacade(new DefaultSessionMergeService(), new ValidatedSetlistRequestMapper(), Duration.ofMinutes(30), Duration.ofHours(4)),
                setlistVersionService(),
                new SetlistVersionDiffService());
        GenerateSetlistRequest request = new GenerateSetlistRequest()
                .verseText("Psalm 100")
                .counts(new SetlistCounts().praise(1).worship(0))
                .keyPolicy(new KeyPolicy()
                        .preferSameKey(true)
                        .allowRelativeMajorMinor(true)
                        .maxKeyCenters(2))
                .tempoPolicy(new TempoPolicy().maxJumpBpm(12))
                .explanationAudience(GenerateSetlistRequest.ExplanationAudienceEnum.ADMIN)
                .includeAdminDiagnostics(true);

        // Act
        ResponseEntity<SetlistProposalResponse> response = controller.generateSetlistProposal(request);

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(setlistService.invocationCount).isEqualTo(1);
        assertThat(setlistService.lastRequest.getExplanationAudience()).isEqualTo(GenerateSetlistRequest.ExplanationAudienceEnum.ADMIN);
        assertThat(setlistService.lastRequest.getIncludeAdminDiagnostics()).isTrue();
    }


    private static SetlistVersionService setlistVersionService() {
        return new SetlistVersionService(new SetlistVersionRepository() {
            @Override public com.cadentia.reng.setlist.SetlistVersionModels.SetlistVersionSnapshot createBaseline(com.cadentia.reng.setlist.SetlistVersionModels.CreateSetlistBaselineCommand command) { throw new UnsupportedOperationException(); }
            @Override public com.cadentia.reng.setlist.SetlistVersionModels.SetlistVersionSnapshot createEditedVersion(com.cadentia.reng.setlist.SetlistVersionModels.CreateSetlistVersionCommand command) { throw new UnsupportedOperationException(); }
            @Override public java.util.Optional<com.cadentia.reng.setlist.SetlistVersionModels.SetlistVersionSnapshot> findVersion(java.util.UUID setlistId, java.util.UUID versionId) { return java.util.Optional.empty(); }
            @Override public java.util.List<com.cadentia.reng.setlist.SetlistVersionModels.SetlistVersionSnapshot> findVersions(java.util.UUID setlistId) { return java.util.List.of(); }
        });
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
