package com.cadentia.bot.telegram;

import com.cadentia.bot.telegram.TelegramOutboundModels.FailureCategory;
import com.cadentia.bot.telegram.TelegramOutboundModels.OutboundStatus;
import com.cadentia.bot.telegram.TelegramOutboundModels.TelegramDeadLetterRecord;
import com.cadentia.bot.telegram.TelegramOutboundModels.TelegramOutboundSendRecord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryTelegramOutboundRepository implements TelegramOutboundRepository {
    private final Map<String, TelegramOutboundSendRecord> records = new LinkedHashMap<>();
    private final List<TelegramDeadLetterRecord> deadLetters = new ArrayList<>();

    @Override
    public synchronized TelegramOutboundSendRecord createIfAbsent(TelegramOutboundSendRecord record) {
        return records.computeIfAbsent(record.idempotencyKey(), ignored -> record);
    }

    @Override
    public synchronized Optional<TelegramOutboundSendRecord> findByIdempotencyKey(String idempotencyKey) {
        return Optional.ofNullable(records.get(idempotencyKey));
    }

    @Override
    public synchronized TelegramOutboundSendRecord markSent(
            String idempotencyKey, String telegramMessageId, Instant now) {
        TelegramOutboundSendRecord current = records.get(idempotencyKey);
        TelegramOutboundSendRecord updated = copy(
                current,
                OutboundStatus.SENT,
                current.attempts() + 1,
                null,
                telegramMessageId,
                null,
                null,
                now);
        records.put(idempotencyKey, updated);
        return updated;
    }

    @Override
    public synchronized TelegramOutboundSendRecord markRetry(
            TelegramOutboundSendRecord record,
            String category,
            String sanitizedDetail,
            Instant retryAt,
            Instant now) {
        TelegramOutboundSendRecord updated = copy(
                record,
                OutboundStatus.RETRY_SCHEDULED,
                record.attempts() + 1,
                retryAt,
                null,
                FailureCategory.valueOf(category),
                sanitizedDetail,
                now);
        records.put(record.idempotencyKey(), updated);
        return updated;
    }

    @Override
    public synchronized TelegramDeadLetterRecord deadLetter(
            TelegramOutboundSendRecord record, String category, String sanitizedDetail, Instant now) {
        TelegramOutboundSendRecord updated = copy(
                record,
                OutboundStatus.DEAD_LETTERED,
                record.attempts() + 1,
                null,
                null,
                FailureCategory.valueOf(category),
                sanitizedDetail,
                now);
        records.put(record.idempotencyKey(), updated);
        TelegramDeadLetterRecord dead = new TelegramDeadLetterRecord(
                UUID.randomUUID(),
                record.id(),
                record.idempotencyKey(),
                record.correlationId(),
                record.chatHash(),
                record.operation(),
                FailureCategory.valueOf(category),
                sanitizedDetail,
                record.sanitizedPreview(),
                updated.attempts(),
                now);
        deadLetters.add(dead);
        return dead;
    }

    @Override
    public synchronized List<TelegramDeadLetterRecord> deadLetters() {
        return List.copyOf(deadLetters);
    }

    private TelegramOutboundSendRecord copy(
            TelegramOutboundSendRecord current,
            OutboundStatus status,
            int attempts,
            Instant retryAt,
            String telegramMessageId,
            FailureCategory category,
            String detail,
            Instant now) {
        return new TelegramOutboundSendRecord(current.id(), current.idempotencyKey(), current.correlationId(), current.chatHash(),
                current.operation(), current.sanitizedPreview(), status, attempts, current.maxAttempts(), retryAt, telegramMessageId,
                category, detail, current.createdAt(), now);
    }
}
