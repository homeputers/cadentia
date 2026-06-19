package com.cadentia.bot.telegram;

import com.cadentia.bot.telegram.TelegramOutboundModels.TelegramSendResult;
import org.springframework.stereotype.Component;

@Component
public class DisabledTelegramOutboundClient implements TelegramOutboundClient {
    @Override
    public TelegramSendResult send(TelegramRenderedMessage message) {
        throw new TelegramClientException(403, "Telegram outbound channel disabled until bot API transport is configured.");
    }
}
