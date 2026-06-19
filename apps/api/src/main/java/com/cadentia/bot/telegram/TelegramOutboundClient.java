package com.cadentia.bot.telegram;

import com.cadentia.bot.telegram.TelegramOutboundModels.TelegramSendResult;

public interface TelegramOutboundClient {
    TelegramSendResult send(TelegramRenderedMessage message);
}
