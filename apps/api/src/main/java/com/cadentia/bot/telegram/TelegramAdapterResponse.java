package com.cadentia.bot.telegram;

import com.cadentia.generated.model.GenerateSetlistRequest;
import com.cadentia.generated.model.SetlistProposalResponse;

public record TelegramAdapterResponse(
        TelegramAdapterResponseStatus status,
        String message,
        TelegramChannelEvent event,
        String guidedField,
        GenerateSetlistRequest currentSlots,
        SetlistProposalResponse proposal) {

    public TelegramAdapterResponse(
            TelegramAdapterResponseStatus status,
            String message,
            TelegramChannelEvent event,
            String guidedField) {
        this(status, message, event, guidedField, null, null);
    }

    public TelegramAdapterResponse(
            TelegramAdapterResponseStatus status,
            String message,
            TelegramChannelEvent event,
            String guidedField,
            GenerateSetlistRequest currentSlots) {
        this(status, message, event, guidedField, currentSlots, null);
    }
}
