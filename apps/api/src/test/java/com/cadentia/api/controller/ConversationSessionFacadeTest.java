package com.cadentia.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.generated.model.ConversationRecoveryResponse;
import com.cadentia.generated.model.ConversationRevisionEvent;
import com.cadentia.generated.model.ConversationSessionStateResponse;
import com.cadentia.generated.model.ConversationConfirmRequest;
import com.cadentia.generated.model.ConversationSlotUpdateRequest;
import com.cadentia.generated.model.ConversationSlotUpdateRequestSlotPatch;
import com.cadentia.generated.model.ConversationState;
import com.cadentia.generated.model.SetlistCounts;
import com.cadentia.generated.model.SlotValueSource;
import com.cadentia.intent.Counts;
import com.cadentia.intent.DefaultSessionMergeService;
import com.cadentia.intent.GenerateSetlistIntent;
import com.cadentia.intent.GenerateSetlistSlots;
import com.cadentia.intent.IntentKeyPolicy;
import com.cadentia.intent.IntentTempoPolicy;
import com.cadentia.llm.IntentParseResult;
import com.cadentia.llm.IntentService;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConversationSessionFacadeTest {

    @Test
    void confirmRequiresReadyToConfirmState() {
        ConversationSessionFacade facade = facade(null);
        UUID sessionId = UUID.randomUUID();

        ConversationSessionStateResponse state = facade.confirm(
                sessionId, new ConversationConfirmRequest().accepted(true));

        assertThat(state.getState()).isEqualTo(ConversationState.COLLECTING);
        assertThat(state.getAuditMessages())
                .contains("Session is not ready to confirm. Provide a valid request before recommendation.");
        assertThat(state.getRevisionHistory()).isEmpty();
    }

    @Test
    void emptyMenuUpdateDoesNotRelabelDefaultedSlotsAsMenuSources() {
        ConversationSessionFacade facade = facade(null);
        UUID sessionId = UUID.randomUUID();

        ConversationSessionStateResponse state = facade.update(
                sessionId,
                new ConversationSlotUpdateRequest().source(SlotValueSource.MENU));

        assertThat(state.getSlotSources())
                .anySatisfy(source -> {
                    assertThat(source.getSlot().getValue()).isEqualTo("counts");
                    assertThat(source.getSource()).isEqualTo(SlotValueSource.DEFAULT);
                })
                .anySatisfy(source -> {
                    assertThat(source.getSlot().getValue()).isEqualTo("keyPolicy");
                    assertThat(source.getSource()).isEqualTo(SlotValueSource.DEFAULT);
                })
                .anySatisfy(source -> {
                    assertThat(source.getSlot().getValue()).isEqualTo("tempoPolicy");
                    assertThat(source.getSource()).isEqualTo(SlotValueSource.DEFAULT);
                });
        assertThat(state.getRevisionHistory()).isEmpty();
    }

    @Test
    void freeTextReadySessionCanConfirmAndRecordsSourcesAndRevisionHistory() {
        ConversationSessionFacade facade = facade(input -> IntentParseResult.accepted(normalizedIntent(), false));
        UUID sessionId = UUID.randomUUID();

        ConversationSessionStateResponse ready = facade.ingestFreeText(sessionId, "Psalm 100 thanksgiving");
        ConversationSessionStateResponse confirmed = facade.confirm(
                sessionId, new ConversationConfirmRequest().accepted(true));

        assertThat(ready.getState()).isEqualTo(ConversationState.READY_TO_CONFIRM);
        assertThat(ready.getSlotSources())
                .anySatisfy(source -> {
                    assertThat(source.getSlot().getValue()).isEqualTo("verseText");
                    assertThat(source.getSource()).isEqualTo(SlotValueSource.FREE_TEXT);
                })
                .anySatisfy(source -> {
                    assertThat(source.getSlot().getValue()).isEqualTo("counts");
                    assertThat(source.getSource()).isEqualTo(SlotValueSource.FREE_TEXT);
                });
        assertThat(ready.getRevisionHistory())
                .extracting(event -> event.getEventType().getValue())
                .contains("slot_update");
        assertThat(confirmed.getState()).isEqualTo(ConversationState.CONFIRMED);
        assertThat(confirmed.getConfirmedAt()).isNotNull();
        assertThat(confirmed.getRevisionHistory())
                .extracting(event -> event.getEventType().getValue())
                .contains("confirm");
    }

    @Test
    void repositoryBackedSessionsSurviveFacadeRestartWithSourcesAndRevisions() {
        InMemoryConversationSessionRepository repository = new InMemoryConversationSessionRepository();
        UUID sessionId = UUID.randomUUID();
        ConversationSessionFacade firstFacade = facade(repository, input -> IntentParseResult.accepted(normalizedIntent(), false));

        firstFacade.ingestFreeText(sessionId, "Psalm 100 thanksgiving");
        firstFacade.confirm(sessionId, new ConversationConfirmRequest().accepted(true));
        ConversationSessionFacade restartedFacade = facade(repository, input -> IntentParseResult.accepted(normalizedIntent(), false));
        ConversationSessionStateResponse recovered = restartedFacade.get(sessionId);

        assertThat(recovered.getState()).isEqualTo(ConversationState.CONFIRMED);
        assertThat(recovered.getConfirmedAt()).isNotNull();
        assertThat(recovered.getSlotSources())
                .anySatisfy(source -> {
                    assertThat(source.getSlot().getValue()).isEqualTo("verseText");
                    assertThat(source.getSource()).isEqualTo(SlotValueSource.FREE_TEXT);
                });
        assertThat(recovered.getRevisionHistory())
                .extracting(event -> event.getEventType().getValue())
                .contains("slot_update", "confirm");
    }

    @Test
    void recoverConfirmedSessionPreservesConfirmedStateAndSetlistCorrelation() throws Exception {
        InMemoryConversationSessionRepository repository = new InMemoryConversationSessionRepository();
        ConversationSessionFacade facade = new ConversationSessionFacade(
                new DefaultSessionMergeService(),
                new ValidatedSetlistRequestMapper(),
                Duration.ofMillis(5),
                Duration.ofHours(1),
                repository,
                input -> IntentParseResult.accepted(normalizedIntent(), false));
        UUID sessionId = UUID.randomUUID();

        facade.ingestFreeText(sessionId, "Psalm 100 thanksgiving");
        facade.confirm(sessionId, new ConversationConfirmRequest().accepted(true));
        facade.recordRecommendationCorrelation(sessionId, "rec-result-123", "setlist-456", "version-789");
        Thread.sleep(15);

        ConversationRecoveryResponse recovery = facade.recover(sessionId);

        assertThat(recovery.getPriorState()).isEqualTo(ConversationState.CONFIRMED);
        assertThat(recovery.getRecoveredState().getState()).isEqualTo(ConversationState.CONFIRMED);
        assertThat(recovery.getRecoveredState().getRecommendationResultId()).isEqualTo("rec-result-123");
        assertThat(recovery.getRecoveredState().getSetlistId()).isEqualTo("setlist-456");
        assertThat(recovery.getRecoveredState().getSetlistVersionId()).isEqualTo("version-789");
        assertThat(recovery.getRecoveredState().getRevisionHistory())
                .extracting(event -> event.getEventType().getValue())
                .doesNotContain("expire", "recover");
        assertThat(recovery.getRestartSuggested()).isFalse();
    }

    @Test
    void channelAndRecommendationCorrelationAreStoredWithoutRawMessageText() {
        InMemoryConversationSessionRepository repository = new InMemoryConversationSessionRepository();
        ConversationSessionFacade facade = facade(repository, input -> IntentParseResult.accepted(normalizedIntent(), false));
        UUID sessionId = UUID.randomUUID();

        facade.ingestFreeText(sessionId, "Psalm 100 thanksgiving");
        facade.recordChannelCorrelation(sessionId, "telegram", "9103", "corr-bot-e2e");
        facade.recordRecommendationCorrelation(sessionId, "rec-result-123", "setlist-456", "version-789");
        facade.recordChannelCorrelation(sessionId, "telegram", "9104", "corr-after-confirm");

        ConversationSessionRecord record = repository.findById(sessionId).orElseThrow();
        assertThat(record.channel()).isEqualTo("telegram");
        assertThat(record.channelUpdateId()).isEqualTo("9104");
        assertThat(record.recommendationResultId()).isEqualTo("rec-result-123");
        assertThat(record.correlationMetadata())
                .containsEntry("correlationId", "corr-after-confirm")
                .containsEntry("setlistId", "setlist-456")
                .containsEntry("setlistVersionId", "version-789");
        assertThat(record.correlationMetadata().toString()).doesNotContain("Psalm 100 thanksgiving");

        ConversationSessionStateResponse state = facade.get(sessionId);
        assertThat(state.getRecommendationResultId()).isEqualTo("rec-result-123");
        assertThat(state.getSetlistId()).isEqualTo("setlist-456");
        assertThat(state.getSetlistVersionId()).isEqualTo("version-789");
    }

    @Test
    void cancelledSessionDoesNotConfirm() {
        ConversationSessionFacade facade = facade(input -> IntentParseResult.accepted(normalizedIntent(), false));
        UUID sessionId = UUID.randomUUID();

        facade.ingestFreeText(sessionId, "Psalm 100 thanksgiving");
        ConversationSessionStateResponse cancelled = facade.cancel(sessionId);
        ConversationSessionStateResponse confirmAttempt = facade.confirm(
                sessionId, new ConversationConfirmRequest().accepted(true));

        assertThat(cancelled.getState()).isEqualTo(ConversationState.CANCELLED);
        assertThat(confirmAttempt.getState()).isEqualTo(ConversationState.CANCELLED);
        assertThat(confirmAttempt.getRevisionHistory())
                .extracting(ConversationRevisionEvent::getEventType)
                .extracting(ConversationRevisionEvent.EventTypeEnum::getValue)
                .doesNotContain("confirm");
    }

    @Test
    void recoverCancelledSessionPreservesCancelledState() throws Exception {
        ConversationSessionFacade facade = new ConversationSessionFacade(
                new DefaultSessionMergeService(),
                new ValidatedSetlistRequestMapper(),
                Duration.ofMillis(5),
                Duration.ofHours(1),
                new InMemoryConversationSessionRepository(),
                input -> IntentParseResult.accepted(normalizedIntent(), false));
        UUID sessionId = UUID.randomUUID();

        facade.ingestFreeText(sessionId, "Psalm 100 thanksgiving");
        facade.cancel(sessionId);
        Thread.sleep(15);

        ConversationRecoveryResponse recovery = facade.recover(sessionId);
        ConversationSessionStateResponse confirmAttempt = facade.confirm(
                sessionId, new ConversationConfirmRequest().accepted(true));

        assertThat(recovery.getPriorState()).isEqualTo(ConversationState.CANCELLED);
        assertThat(recovery.getRecoveredState().getState()).isEqualTo(ConversationState.CANCELLED);
        assertThat(recovery.getRecoveredState().getRevisionHistory())
                .extracting(event -> event.getEventType().getValue())
                .doesNotContain("expire", "recover", "confirm");
        assertThat(recovery.getRestartSuggested()).isTrue();
        assertThat(confirmAttempt.getState()).isEqualTo(ConversationState.CANCELLED);
    }

    @Test
    void revisionAfterConfirmationRequiresReconfirmationBeforeGeneration() {
        ConversationSessionFacade facade = facade(input -> IntentParseResult.accepted(normalizedIntent(), false));
        UUID sessionId = UUID.randomUUID();

        facade.ingestFreeText(sessionId, "Psalm 100 thanksgiving");
        facade.confirm(sessionId, new ConversationConfirmRequest().accepted(true));
        ConversationSessionStateResponse revised = facade.update(
                sessionId,
                new ConversationSlotUpdateRequest()
                        .source(SlotValueSource.USER_EDIT)
                        .slotPatch(new ConversationSlotUpdateRequestSlotPatch()
                                .counts(new SetlistCounts().praise(3).worship(2))));
        ConversationSessionStateResponse confirmAttempt = facade.confirm(
                sessionId, new ConversationConfirmRequest().accepted(true));

        assertThat(revised.getState()).isEqualTo(ConversationState.COLLECTING);
        assertThat(revised.getConfirmedAt()).isNull();
        assertThat(revised.getSlotSources())
                .anySatisfy(source -> {
                    assertThat(source.getSlot().getValue()).isEqualTo("counts");
                    assertThat(source.getSource()).isEqualTo(SlotValueSource.USER_EDIT);
                });
        assertThat(confirmAttempt.getState()).isEqualTo(ConversationState.COLLECTING);
    }

    @Test
    void recoverActiveSessionPreservesCurrentState() {
        ConversationSessionFacade facade = facade(input -> IntentParseResult.accepted(normalizedIntent(), false));
        UUID sessionId = UUID.randomUUID();

        facade.ingestFreeText(sessionId, "Psalm 100 thanksgiving");

        ConversationRecoveryResponse recovery = facade.recover(sessionId);
        ConversationSessionStateResponse confirmAttempt = facade.confirm(
                sessionId, new ConversationConfirmRequest().accepted(true));

        assertThat(recovery.getPriorState()).isEqualTo(ConversationState.READY_TO_CONFIRM);
        assertThat(recovery.getRecoveredState().getState()).isEqualTo(ConversationState.READY_TO_CONFIRM);
        assertThat(recovery.getRecoveredState().getSlots().getVerseText()).isEqualTo("Psalm 100");
        assertThat(recovery.getRecoveredState().getRevisionHistory())
                .extracting(event -> event.getEventType().getValue())
                .doesNotContain("expire", "recover");
        assertThat(recovery.getRestartSuggested()).isFalse();
        assertThat(confirmAttempt.getState()).isEqualTo(ConversationState.CONFIRMED);
    }

    @Test
    void sessionExpiresByInactivityAndCanRecoverWithContextSummary() throws Exception {
        ConversationSessionFacade facade = new ConversationSessionFacade(
                new DefaultSessionMergeService(),
                new ValidatedSetlistRequestMapper(),
                Duration.ofMillis(5),
                Duration.ofHours(1));
        UUID sessionId = UUID.randomUUID();

        facade.update(sessionId, new ConversationSlotUpdateRequest().source(SlotValueSource.MENU));
        Thread.sleep(15);

        ConversationSessionStateResponse state = facade.get(sessionId);
        ConversationRecoveryResponse recovery = facade.recover(sessionId);

        assertThat(state.getState()).isEqualTo(ConversationState.EXPIRED);
        assertThat(recovery.getPriorState()).isEqualTo(ConversationState.EXPIRED);
        assertThat(recovery.getRecoveredState().getState()).isEqualTo(ConversationState.START);
        assertThat(recovery.getRecoveredState().getAuditMessages())
                .contains("Lost context summary: all unconfirmed slot constraints were discarded.")
                .contains("Retained context summary: immutable session identifier and revision history remain auditable.");
        assertThat(recovery.getRecoveredState().getRevisionHistory())
                .extracting(event -> event.getEventType().getValue())
                .contains("expire", "recover");
        assertThat(recovery.getRestartSuggested()).isTrue();
    }

    private static ConversationSessionFacade facade(IntentService intentService) {
        return facade(new InMemoryConversationSessionRepository(), intentService);
    }

    private static ConversationSessionFacade facade(
            ConversationSessionRepository repository,
            IntentService intentService) {
        return new ConversationSessionFacade(
                new DefaultSessionMergeService(),
                new ValidatedSetlistRequestMapper(),
                Duration.ofMinutes(30),
                Duration.ofHours(4),
                repository,
                intentService);
    }

    @Test
    void startNewDiscardsPriorSessionAndReturnsFreshDefaults() {
        InMemoryConversationSessionRepository repository = new InMemoryConversationSessionRepository();
        ConversationSessionFacade facade = facade(repository, null);
        UUID sessionId = UUID.randomUUID();

        facade.update(sessionId, new ConversationSlotUpdateRequest()
                .source(SlotValueSource.MENU)
                .slotPatch(new ConversationSlotUpdateRequestSlotPatch()
                        .counts(new SetlistCounts(3, 2))
                        .language("es")));
        ConversationSessionStateResponse prior = facade.get(sessionId);
        assertThat(prior.getSlots().getCounts().getPraise()).isEqualTo(3);
        assertThat(prior.getSlots().getLanguage()).isEqualTo("es");

        ConversationSessionStateResponse fresh = facade.startNew(sessionId);

        assertThat(fresh.getState()).isEqualTo(ConversationState.COLLECTING);
        assertThat(fresh.getSlots().getCounts().getPraise()).isEqualTo(10);
        assertThat(fresh.getSlots().getCounts().getWorship()).isEqualTo(5);
        assertThat(fresh.getSlots().getLanguage()).isNull();
        assertThat(fresh.getSlots().getVerseText()).isEmpty();
        assertThat(fresh.getAuditMessages()).contains("New setlist flow started.");
        assertThat(fresh.getRevisionHistory())
                .extracting(event -> event.getEventType().getValue())
                .contains("cancel");

        ConversationSessionRecord record = repository.findById(sessionId).orElseThrow();
        assertThat(record.slots().language()).isNull();
        assertThat(record.state()).isEqualTo(ConversationState.COLLECTING);
    }

    @Test
    void startNewCreatesFreshSessionWhenNoPriorExists() {
        ConversationSessionFacade facade = facade(null);
        UUID sessionId = UUID.randomUUID();

        ConversationSessionStateResponse fresh = facade.startNew(sessionId);

        assertThat(fresh.getState()).isEqualTo(ConversationState.COLLECTING);
        assertThat(fresh.getSlots().getCounts().getPraise()).isEqualTo(10);
        assertThat(fresh.getSlots().getCounts().getWorship()).isEqualTo(5);
        assertThat(fresh.getAuditMessages()).contains("New setlist flow started.");
        assertThat(fresh.getRevisionHistory()).isEmpty();
    }

    private static GenerateSetlistIntent normalizedIntent() {
        return new GenerateSetlistIntent("v1", new GenerateSetlistSlots(
                "Psalm 100",
                List.of("Psalm 100:1-5"),
                List.of("thanksgiving", "joy"),
                new Counts(2, 1),
                new IntentKeyPolicy(true, true, 2),
                new IntentTempoPolicy(12),
                "en",
                "rising",
                List.of(),
                "opening"));
    }
}
