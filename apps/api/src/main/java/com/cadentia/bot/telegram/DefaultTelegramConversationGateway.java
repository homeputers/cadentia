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

    public DefaultTelegramConversationGateway(ConversationSessionFacade facade) {
        this.facade = facade;
    }

    @Override
    public TelegramAdapterResponse start(TelegramChannelEvent event) {
        facade.get(sessionId(event));
        return new TelegramAdapterResponse(TelegramAdapterResponseStatus.STARTED, "Welcome to Cadentia.", event, null);
    }

    @Override
    public TelegramAdapterResponse help(TelegramChannelEvent event) {
        return new TelegramAdapterResponse(TelegramAdapterResponseStatus.CONTINUED, "Use /newsetlist to begin.", event, null);
    }

    @Override
    public TelegramAdapterResponse newSetlist(TelegramChannelEvent event) {
        facade.update(sessionId(event), new ConversationSlotUpdateRequest().source(SlotValueSource.MENU));
        return new TelegramAdapterResponse(TelegramAdapterResponseStatus.STARTED, "New setlist flow started.", event, null);
    }

    @Override
    public TelegramAdapterResponse status(TelegramChannelEvent event) {
        facade.get(sessionId(event));
        return new TelegramAdapterResponse(TelegramAdapterResponseStatus.CONTINUED, "Session status returned.", event, null);
    }

    @Override
    public TelegramAdapterResponse cancel(TelegramChannelEvent event) {
        facade.cancel(sessionId(event));
        return new TelegramAdapterResponse(TelegramAdapterResponseStatus.CANCELLED, "Session cancelled.", event, null);
    }

    @Override
    public TelegramAdapterResponse settings(TelegramChannelEvent event) {
        return new TelegramAdapterResponse(TelegramAdapterResponseStatus.CONTINUED, "Settings opened.", event, null);
    }

    @Override
    public TelegramAdapterResponse text(TelegramChannelEvent event) {
        facade.update(sessionId(event), new ConversationSlotUpdateRequest().source(SlotValueSource.FREE_TEXT));
        return new TelegramAdapterResponse(TelegramAdapterResponseStatus.CONTINUED, "Input recorded.", event, null);
    }

    @Override
    public TelegramAdapterResponse menuSelection(TelegramChannelEvent event) {
        facade.update(sessionId(event), new ConversationSlotUpdateRequest().source(SlotValueSource.MENU));
        return new TelegramAdapterResponse(
                TelegramAdapterResponseStatus.CONTINUED,
                "Menu selection recorded.",
                event,
                event.callbackAction().guidedField());
    }

    private UUID sessionId(TelegramChannelEvent event) {
        return UUID.nameUUIDFromBytes(("telegram:" + event.chatId() + ":" + event.userId()).getBytes(StandardCharsets.UTF_8));
    }
}
