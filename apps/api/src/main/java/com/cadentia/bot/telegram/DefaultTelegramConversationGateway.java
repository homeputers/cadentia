package com.cadentia.bot.telegram;

import com.cadentia.api.controller.ConversationSessionFacade;
import com.cadentia.generated.model.ConversationSlotUpdateRequest;
import com.cadentia.generated.model.SlotValueSource;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DefaultTelegramConversationGateway implements TelegramConversationGateway {
    private final ConversationSessionFacade facade;
    private final TelegramAuthorizationService authorizationService;

    public DefaultTelegramConversationGateway(ConversationSessionFacade facade, TelegramAuthorizationService authorizationService) {
        this.facade = facade;
        this.authorizationService = authorizationService;
    }

    DefaultTelegramConversationGateway(ConversationSessionFacade facade) {
        this.facade = facade;
        this.authorizationService = null;
    }

    @Override
    public TelegramAdapterResponse start(TelegramChannelEvent event) {
        TelegramAuthorizationService.TelegramAuthorizationDecision decision = authorize(event, TelegramProtectedAction.SESSION_STATUS);
        if (!decision.permitted()) {
            return denied(event, decision);
        }
        facade.get(sessionId(event));
        touch(decision, event, TelegramSessionState.IDLE);
        return new TelegramAdapterResponse(TelegramAdapterResponseStatus.STARTED, "Welcome to Cadentia.", event, null);
    }

    @Override
    public TelegramAdapterResponse help(TelegramChannelEvent event) {
        return new TelegramAdapterResponse(TelegramAdapterResponseStatus.CONTINUED, "Use /newsetlist to begin.", event, null);
    }

    @Override
    public TelegramAdapterResponse newSetlist(TelegramChannelEvent event) {
        TelegramAuthorizationService.TelegramAuthorizationDecision decision = authorize(event, TelegramProtectedAction.SETLIST_GENERATE);
        if (!decision.permitted()) {
            return denied(event, decision);
        }
        facade.update(sessionId(event), new ConversationSlotUpdateRequest().source(SlotValueSource.MENU));
        touch(decision, event, TelegramSessionState.NEW_SETLIST_ACTIVE);
        return new TelegramAdapterResponse(TelegramAdapterResponseStatus.STARTED, "New setlist flow started.", event, null);
    }

    @Override
    public TelegramAdapterResponse status(TelegramChannelEvent event) {
        TelegramAuthorizationService.TelegramAuthorizationDecision decision = authorize(event, TelegramProtectedAction.SESSION_STATUS);
        if (!decision.permitted()) {
            return denied(event, decision);
        }
        facade.get(sessionId(event));
        touch(decision, event, currentState(decision));
        return new TelegramAdapterResponse(TelegramAdapterResponseStatus.CONTINUED, "Session status returned.", event, null);
    }

    @Override
    public TelegramAdapterResponse cancel(TelegramChannelEvent event) {
        TelegramAuthorizationService.TelegramAuthorizationDecision decision = authorize(event, TelegramProtectedAction.SESSION_CANCEL);
        if (!decision.permitted()) {
            return denied(event, decision);
        }
        facade.cancel(sessionId(event));
        touch(decision, event, TelegramSessionState.CANCELLED);
        return new TelegramAdapterResponse(TelegramAdapterResponseStatus.CANCELLED, "Session cancelled.", event, null);
    }

    @Override
    public TelegramAdapterResponse settings(TelegramChannelEvent event) {
        TelegramAuthorizationService.TelegramAuthorizationDecision decision = authorize(event, TelegramProtectedAction.SETTINGS);
        if (!decision.permitted()) {
            return denied(event, decision);
        }
        touch(decision, event, currentState(decision));
        return new TelegramAdapterResponse(TelegramAdapterResponseStatus.CONTINUED, "Settings opened.", event, null);
    }

    @Override
    public TelegramAdapterResponse text(TelegramChannelEvent event) {
        TelegramAuthorizationService.TelegramAuthorizationDecision decision = authorize(event, TelegramProtectedAction.CONVERSATION_CONTINUE);
        if (!decision.permitted()) {
            return denied(event, decision);
        }
        facade.update(sessionId(event), new ConversationSlotUpdateRequest().source(SlotValueSource.FREE_TEXT));
        touch(decision, event, TelegramSessionState.PENDING_CONFIRMATION);
        return new TelegramAdapterResponse(TelegramAdapterResponseStatus.CONTINUED, "Input recorded.", event, null);
    }

    @Override
    public TelegramAdapterResponse menuSelection(TelegramChannelEvent event) {
        TelegramAuthorizationService.TelegramAuthorizationDecision decision = authorize(event, TelegramProtectedAction.CONVERSATION_CONTINUE);
        if (!decision.permitted()) {
            return denied(event, decision);
        }
        facade.update(sessionId(event), new ConversationSlotUpdateRequest().source(SlotValueSource.MENU));
        touch(decision, event, TelegramSessionState.PENDING_CONFIRMATION);
        return new TelegramAdapterResponse(
                TelegramAdapterResponseStatus.CONTINUED,
                "Menu selection recorded.",
                event,
                event.callbackAction().guidedField());
    }

    private TelegramSessionState currentState(TelegramAuthorizationService.TelegramAuthorizationDecision decision) {
        return decision.session() == null ? TelegramSessionState.IDLE : decision.session().state();
    }

    private void touch(TelegramAuthorizationService.TelegramAuthorizationDecision decision, TelegramChannelEvent event, TelegramSessionState state) {
        if (authorizationService != null) {
            authorizationService.touch(decision, event, state);
        }
    }

    private TelegramAuthorizationService.TelegramAuthorizationDecision authorize(TelegramChannelEvent event, TelegramProtectedAction action) {
        if (authorizationService == null) {
            return new TelegramAuthorizationService.TelegramAuthorizationDecision(true, TelegramIdentityStatus.LINKED, "Authorized.", null, null);
        }
        return authorizationService.authorize(event, action);
    }

    private TelegramAdapterResponse denied(TelegramChannelEvent event, TelegramAuthorizationService.TelegramAuthorizationDecision decision) {
        TelegramAdapterResponseStatus status = decision.status() == TelegramIdentityStatus.DISABLED
                ? TelegramAdapterResponseStatus.DISABLED
                : TelegramAdapterResponseStatus.UNAUTHORIZED;
        return new TelegramAdapterResponse(status, decision.safeResponse(), event, null);
    }

    private UUID sessionId(TelegramChannelEvent event) {
        return UUID.nameUUIDFromBytes(("telegram:" + event.chatId() + ":" + event.userId()).getBytes(StandardCharsets.UTF_8));
    }
}
