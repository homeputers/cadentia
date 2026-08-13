package com.cadentia.bot.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.api.controller.ConversationSessionFacade;
import com.cadentia.api.controller.ValidatedSetlistRequestMapper;
import com.cadentia.bot.telegram.TelegramOutboundModels.OutboundStatus;
import com.cadentia.bot.telegram.TelegramOutboundModels.TelegramOutboundSendRecord;
import com.cadentia.bot.telegram.TelegramOutboundModels.TelegramSendResult;
import com.cadentia.generated.model.ConversationSessionStateResponse;
import com.cadentia.generated.model.ConversationState;
import com.cadentia.generated.model.GenerateSetlistRequest;
import com.cadentia.generated.model.RecommendationExplanation;
import com.cadentia.generated.model.RecommendationExplanationCode;
import com.cadentia.generated.model.RecommendationExplanationEntry;
import com.cadentia.generated.model.RecommendationExplanationEvidence;
import com.cadentia.generated.model.RecommendationExplanationScope;
import com.cadentia.generated.model.RecommendationExplanationSubject;
import com.cadentia.generated.model.SetlistProposalResponse;
import com.cadentia.intent.Counts;
import com.cadentia.intent.DefaultSessionMergeService;
import com.cadentia.intent.GenerateSetlistIntent;
import com.cadentia.intent.GenerateSetlistSlots;
import com.cadentia.intent.IntentKeyPolicy;
import com.cadentia.intent.IntentTempoPolicy;
import com.cadentia.llm.IntentParseResult;
import com.cadentia.reng.SetlistService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

class TelegramE2eFixtureTest {
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(1781870440), ZoneOffset.UTC);

    @Test
    void fixtureBackedNewSetlistFlowPersistsSessionRendersAndSendsDeterministicApprovedResponse() throws Exception {
        // Arrange
        FixtureHarness harness = new FixtureHarness(false);
        InMemoryTelegramWebhookIdempotencyStore idempotency = new InMemoryTelegramWebhookIdempotencyStore();

        // Act
        TelegramAdapterResponse start = harness.handle("newsetlist-command.json", "corr-9001");
        TelegramAdapterResponse prompt = harness.handle("newsetlist-prompt.json", "corr-9002");
        ConversationSessionStateResponse state = harness.facade.get(sessionId("42001", "99001"));
        TelegramAdapterResponse confirm = harness.handle("newsetlist-confirm-callback.json", "corr-9003");
        List<TelegramRenderedMessage> rendered = harness.renderer.render(confirm);
        TelegramOutboundSendRecord sent = harness.outbound.send(rendered.get(1), "corr-9003", "proposal");

        // Assert
        assertThat(idempotency.record("bot-fixture", "42001", 9001)).isEqualTo(TelegramWebhookIdempotencyStore.IdempotencyResult.ACCEPTED);
        assertThat(idempotency.record("bot-fixture", "42001", 9001)).isEqualTo(TelegramWebhookIdempotencyStore.IdempotencyResult.DUPLICATE_ACCEPTED);
        assertThat(start.status()).isEqualTo(TelegramAdapterResponseStatus.STARTED);
        assertThat(prompt.status()).isEqualTo(TelegramAdapterResponseStatus.CONTINUED);
        assertThat(state.getState()).isEqualTo(ConversationState.READY_TO_CONFIRM);
        assertThat(confirm.status()).isEqualTo(TelegramAdapterResponseStatus.COMPLETED);
        assertThat(confirm.proposal()).isSameAs(harness.setlistService.proposal);
        assertThat(harness.setlistService.lastRequest).usingRecursiveComparison().isEqualTo(state.getSlots());
        assertThat(rendered.get(0).callbackAcknowledgement()).isEqualTo("Proposal generated.");
        assertThat(rendered.get(1).text()).contains("Setlist proposal", "catalog:song-approved-1", "approval:fixture-approved-1");
        assertThat(rendered.get(1).text()).doesNotContain("unapproved").doesNotContain("raw lyric");
        assertThat(sent.status()).isEqualTo(OutboundStatus.SENT);
        assertThat(harness.client.sent).hasSize(1);
    }

    @Test
    void failureFixturesCoverDisabledStaleCancellationAndRetryDeadLetterPaths() throws Exception {
        // Arrange
        FixtureHarness disabled = new FixtureHarness(true);
        FixtureHarness harness = new FixtureHarness(false);
        InMemoryTelegramOutboundRepository repository = new InMemoryTelegramOutboundRepository();
        ScriptedClient client = new ScriptedClient();
        TelegramOutboundSendService outbound = new TelegramOutboundSendService(repository, client, new TelegramIdentifierHasher("hash-secret"), CLOCK);
        TelegramRenderedMessage message = TelegramRenderedMessage.message("42001", "retryable proposal", null);

        // Act
        TelegramAdapterResponse disabledResponse = disabled.handle("newsetlist-command.json", "corr-disabled");
        TelegramAdapterResponse staleResponse = harness.handle("stale-callback.json", "corr-stale");
        TelegramAdapterResponse cancelResponse = harness.handle("cancel-callback.json", "corr-cancel");
        client.failures.add(new TelegramClientException(502, "Telegram unavailable token=secret"));
        TelegramOutboundSendRecord retry = outbound.send(message, "corr-retry", "proposal");
        client.failures.add(new TelegramClientException(403, "bot was blocked by the user prompt text: private"));
        TelegramOutboundSendRecord deadLetter = outbound.send(TelegramRenderedMessage.message("42002", "blocked proposal", null), "corr-dead", "proposal");

        // Assert
        assertThat(disabledResponse.status()).isEqualTo(TelegramAdapterResponseStatus.DISABLED);
        assertThat(staleResponse.status()).isEqualTo(TelegramAdapterResponseStatus.STALE_CALLBACK);
        assertThat(cancelResponse.status()).isEqualTo(TelegramAdapterResponseStatus.CANCELLED);
        assertThat(retry.status()).isEqualTo(OutboundStatus.RETRY_SCHEDULED);
        assertThat(deadLetter.status()).isEqualTo(OutboundStatus.DEAD_LETTERED);
        assertThat(repository.deadLetters()).hasSize(1);
    }

    private static String fixture(String name) throws Exception {
        return StreamUtils.copyToString(new ClassPathResource("telegram/fixtures/" + name).getInputStream(), StandardCharsets.UTF_8);
    }

    private static UUID sessionId(String chatId, String userId) {
        return UUID.nameUUIDFromBytes(("telegram:" + chatId + ":" + userId).getBytes(StandardCharsets.UTF_8));
    }

    private static GenerateSetlistIntent normalizedIntent() {
        return new GenerateSetlistIntent("v1", new GenerateSetlistSlots("Psalm 100", List.of("Psalm 100:1-5"),
                List.of("thanksgiving", "joy"), new Counts(2, 1), new IntentKeyPolicy(true, true, 2),
                new IntentTempoPolicy(12), "en", "rising", List.of(), "opening"));
    }

    private static class FixtureHarness {
        private final ConversationSessionFacade facade = new ConversationSessionFacade(new DefaultSessionMergeService(),
                new ValidatedSetlistRequestMapper(), Duration.ofMinutes(30), Duration.ofHours(4), input -> IntentParseResult.accepted(normalizedIntent(), false));
        private final CapturingSetlistService setlistService = new CapturingSetlistService();
        private final TelegramResponseRenderer renderer = new TelegramResponseRenderer();
        private final ScriptedClient client = new ScriptedClient();
        private final TelegramOutboundSendService outbound = new TelegramOutboundSendService(
                new InMemoryTelegramOutboundRepository(), client, new TelegramIdentifierHasher("hash-secret"), CLOCK);
        private final TelegramBotAdapter adapter;

        FixtureHarness(boolean disabled) {
            TelegramOperationalControlService controls = new TelegramOperationalControlService(!disabled, null);
            adapter = new TelegramBotAdapter(new com.fasterxml.jackson.databind.ObjectMapper(),
                    new DefaultTelegramConversationGateway(facade, null, setlistService), false, Duration.ofMinutes(30), CLOCK, null, controls);
        }

        TelegramAdapterResponse handle(String fixture, String correlationId) throws Exception {
            return adapter.handleUpdate(fixture(fixture), correlationId);
        }
    }

    private static class CapturingSetlistService extends SetlistService {
        private GenerateSetlistRequest lastRequest;
        private SetlistProposalResponse proposal;

        @Override
        public SetlistProposalResponse generate(GenerateSetlistRequest request) {
            lastRequest = request;
            RecommendationExplanationEntry selected = new RecommendationExplanationEntry(
                    RecommendationExplanationCode.APPROVAL_ELIGIBLE,
                    RecommendationExplanationEntry.SeverityEnum.INFO,
                    RecommendationExplanationScope.SELECTED_SONG,
                    RecommendationExplanationEntry.AudienceEnum.PUBLIC,
                    new RecommendationExplanationSubject(RecommendationExplanationSubject.TypeEnum.SONG, "song-approved-1"),
                    "selected.approved",
                    Map.of(),
                    List.of(new RecommendationExplanationEvidence(RecommendationExplanationEvidence.TypeEnum.CATALOG, "catalog:song-approved-1"),
                            new RecommendationExplanationEvidence(RecommendationExplanationEvidence.TypeEnum.APPROVAL, "approval:fixture-approved-1")))
                    .defaultText("Approved fixture song fits Psalm 100 thanksgiving.");
            proposal = new SetlistProposalResponse().status("PROPOSED").recommendationResultId("fixture-rec-1")
                    .auditMessages(List.of("Approved-only policy applied.", "raw lyric detail redacted."))
                    .explanation(new RecommendationExplanation().selectedSongs(List.of(selected)));
            return proposal;
        }
    }

    private static class ScriptedClient implements TelegramOutboundClient {
        private final List<RuntimeException> failures = new ArrayList<>();
        private final List<TelegramRenderedMessage> sent = new ArrayList<>();

        @Override
        public TelegramSendResult send(TelegramRenderedMessage message) {
            if (!failures.isEmpty()) {
                throw failures.remove(0);
            }
            sent.add(message);
            return TelegramSendResult.delivered("telegram-message-" + sent.size());
        }
    }
}
