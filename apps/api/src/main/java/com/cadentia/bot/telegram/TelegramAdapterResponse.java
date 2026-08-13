package com.cadentia.bot.telegram;

import com.cadentia.generated.model.SetlistProposalResponse;

public record TelegramAdapterResponse(
        TelegramAdapterResponseStatus status,
        String message,
        TelegramChannelEvent event,
        String guidedField,
        SetlistProposalResponse proposal) {

    public TelegramAdapterResponse(
            TelegramAdapterResponseStatus status,
            String message,
            TelegramChannelEvent event,
            String guidedField) {
        this(status, message, event, guidedField, null);
    }
}
