package com.cadentia.bot.telegram;

import com.cadentia.bot.telegram.TelegramOutboundModels.TelegramDeadLetterRecord;
import com.cadentia.bot.telegram.TelegramOutboundModels.TelegramOutboundSendRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TelegramOutboundRepository {
    TelegramOutboundSendRecord createIfAbsent(TelegramOutboundSendRecord record);

    Optional<TelegramOutboundSendRecord> findByIdempotencyKey(String idempotencyKey);

    TelegramOutboundSendRecord markSent(String idempotencyKey, String telegramMessageId, Instant now);

    TelegramOutboundSendRecord markRetry(TelegramOutboundSendRecord record, String category, String sanitizedDetail, Instant retryAt, Instant now);

    TelegramDeadLetterRecord deadLetter(TelegramOutboundSendRecord record, String category, String sanitizedDetail, Instant now);

    List<TelegramDeadLetterRecord> deadLetters();
}
