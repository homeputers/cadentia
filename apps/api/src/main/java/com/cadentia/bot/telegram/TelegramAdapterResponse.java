package com.cadentia.bot.telegram;

import com.cadentia.generated.model.ConversationSlotSource;
import com.cadentia.generated.model.GenerateSetlistRequest;
import com.cadentia.generated.model.SetlistProposalResponse;
import java.util.List;

public record TelegramAdapterResponse(
        TelegramAdapterResponseStatus status,
        String message,
        TelegramChannelEvent event,
        String guidedField,
        GenerateSetlistRequest currentSlots,
        List<ConversationSlotSource> slotSources,
        SetlistProposalResponse proposal) {

    public TelegramAdapterResponse(
            TelegramAdapterResponseStatus status,
            String message,
            TelegramChannelEvent event,
            String guidedField) {
        this(status, message, event, guidedField, null, null, null);
    }

    public TelegramAdapterResponse(
            TelegramAdapterResponseStatus status,
            String message,
            TelegramChannelEvent event,
            String guidedField,
            GenerateSetlistRequest currentSlots) {
        this(status, message, event, guidedField, currentSlots, null, null);
    }

    public TelegramAdapterResponse(
            TelegramAdapterResponseStatus status,
            String message,
            TelegramChannelEvent event,
            String guidedField,
            GenerateSetlistRequest currentSlots,
            List<ConversationSlotSource> slotSources) {
        this(status, message, event, guidedField, currentSlots, slotSources, null);
    }

    public TelegramAdapterResponse(
            TelegramAdapterResponseStatus status,
            String message,
            TelegramChannelEvent event,
            String guidedField,
            GenerateSetlistRequest currentSlots,
            SetlistProposalResponse proposal) {
        this(status, message, event, guidedField, currentSlots, null, proposal);
    }
}
