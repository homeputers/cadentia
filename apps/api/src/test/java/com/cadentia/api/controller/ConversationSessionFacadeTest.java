package com.cadentia.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.generated.model.ConversationRecoveryResponse;
import com.cadentia.generated.model.ConversationSessionStateResponse;
import com.cadentia.generated.model.SlotValueSource;
import com.cadentia.generated.model.ConversationSlotUpdateRequest;
import com.cadentia.generated.model.ConversationState;
import com.cadentia.intent.DefaultSessionMergeService;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConversationSessionFacadeTest {

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
        assertThat(recovery.getRestartSuggested()).isTrue();
    }
}
