package com.cadentia.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.generated.model.ConversationRecoveryResponse;
import com.cadentia.generated.model.ConversationRevisionEvent;
import com.cadentia.generated.model.ConversationSessionStateResponse;
import com.cadentia.generated.model.SlotValueSource;
import com.cadentia.generated.model.ConversationSlotUpdateRequest;
import com.cadentia.generated.model.ConversationState;
import com.cadentia.generated.model.ConversationConfirmRequest;
import com.cadentia.generated.model.SetlistCounts;
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
    void revisionAfterConfirmationRequiresReconfirmationBeforeGeneration() {
        ConversationSessionFacade facade = facade(input -> IntentParseResult.accepted(normalizedIntent(), false));
        UUID sessionId = UUID.randomUUID();

        facade.ingestFreeText(sessionId, "Psalm 100 thanksgiving");
        facade.confirm(sessionId, new ConversationConfirmRequest().accepted(true));
        ConversationSessionStateResponse revised = facade.update(
                sessionId,
                new ConversationSlotUpdateRequest()
                        .source(SlotValueSource.USER_EDIT)
                        .slotPatch(new com.cadentia.generated.model.ConversationSlotUpdateRequestSlotPatch()
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
        return new ConversationSessionFacade(
                new DefaultSessionMergeService(),
                new ValidatedSetlistRequestMapper(),
                Duration.ofMinutes(30),
                Duration.ofHours(4),
                intentService);
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
