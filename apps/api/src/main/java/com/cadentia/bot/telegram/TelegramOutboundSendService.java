package com.cadentia.bot.telegram;

import com.cadentia.bot.telegram.TelegramOutboundModels.FailureCategory;
import com.cadentia.bot.telegram.TelegramOutboundModels.OutboundStatus;
import com.cadentia.bot.telegram.TelegramOutboundModels.TelegramOutboundSendRecord;
import com.cadentia.bot.telegram.TelegramOutboundModels.TelegramSendResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class TelegramOutboundSendService {
    private static final int DEFAULT_MAX_ATTEMPTS = 4;

    private final TelegramOutboundRepository repository;
    private final TelegramOutboundClient client;
    private final TelegramIdentifierHasher hasher;
    private final Clock clock;
    private final TelegramObservabilityRecorder observabilityRecorder;
    private final TelegramOperationalControlService controlService;

    @Autowired
    public TelegramOutboundSendService(
            TelegramOutboundRepository repository,
            ObjectProvider<TelegramOutboundClient> clientProvider,
            TelegramIdentifierHasher hasher,
            TelegramObservabilityRecorder observabilityRecorder,
            TelegramOperationalControlService controlService) {
        this(repository, configuredClient(clientProvider), hasher, Clock.systemUTC(), observabilityRecorder, controlService);
    }

    public TelegramOutboundSendService(
            TelegramOutboundRepository repository, TelegramOutboundClient client, TelegramIdentifierHasher hasher) {
        this(repository, client, hasher, Clock.systemUTC(), null, null);
    }

    TelegramOutboundSendService(
            TelegramOutboundRepository repository, TelegramOutboundClient client, TelegramIdentifierHasher hasher, Clock clock) {
        this(repository, client, hasher, clock, null, null);
    }

    TelegramOutboundSendService(
            TelegramOutboundRepository repository, TelegramOutboundClient client, TelegramIdentifierHasher hasher, Clock clock,
            TelegramObservabilityRecorder observabilityRecorder, TelegramOperationalControlService controlService) {
        this.repository = repository;
        this.client = client;
        this.hasher = hasher;
        this.clock = clock;
        this.observabilityRecorder = observabilityRecorder;
        this.controlService = controlService;
    }

    public TelegramOutboundSendRecord send(TelegramRenderedMessage message, String correlationId, String operation) {
        Instant now = clock.instant();
        if (controlService != null && controlService.outboundPaused(null)) {
            record("outbound_send_attempt", "paused", 0, "none", "outbound_paused", now, null, null);
            throw new IllegalStateException("Telegram outbound sends are paused for this channel.");
        }
        String idempotencyKey = idempotencyKey(message, correlationId, operation);
        String chatHash = message.chatId() == null ? "callback-only" : hasher.hash("telegram", message.chatId());
        TelegramOutboundSendRecord initial = new TelegramOutboundSendRecord(
                UUID.randomUUID(), idempotencyKey, safe(correlationId), chatHash, safe(operation), preview(message),
                OutboundStatus.PENDING, 0, DEFAULT_MAX_ATTEMPTS, now, null, null, null, now, now);
        TelegramOutboundSendRecord record = repository.createIfAbsent(initial);
        if (record.status() == OutboundStatus.SENT || record.status() == OutboundStatus.DEAD_LETTERED) {
            return record;
        }
        try {
            record("outbound_send_attempt", "attempted", record.attempts(), "none", "none", now, null, chatHash);
            TelegramSendResult result = client.send(message);
            TelegramOutboundSendRecord sent = repository.markSent(idempotencyKey, result.telegramMessageId(), now);
            record("outbound_send_attempt", "sent", sent.attempts(), "none", "none", now, null, chatHash);
            return sent;
        } catch (TelegramOutboundClientNotConfiguredException ex) {
            throw ex;
        } catch (TelegramClientException ex) {
            FailureCategory category = categorize(ex);
            record(category == FailureCategory.RATE_LIMIT ? "rate_limit_response" : "outbound_send_attempt",
                    "failure", record.attempts() + 1, "none", category.name(), now, null, chatHash);
            return handleFailure(record, category, sanitize(ex.getMessage()), ex.retryAfter(), now);
        } catch (RuntimeException ex) {
            record("outbound_send_attempt", "failure", record.attempts() + 1, "none", FailureCategory.NETWORK.name(), now, null, chatHash);
            return handleFailure(record, FailureCategory.NETWORK, sanitize(ex.getMessage()), null, now);
        }
    }

    private TelegramOutboundSendRecord handleFailure(
            TelegramOutboundSendRecord record,
            FailureCategory category,
            String detail,
            Duration retryAfter,
            Instant now) {
        if (!retryable(category) || record.attempts() + 1 >= record.maxAttempts()) {
            repository.deadLetter(record, category.name(), detail, now);
            record("dead_letter_creation", "created", record.attempts() + 1, "none", category.name(), now, null, record.chatHash());
            return repository.findByIdempotencyKey(record.idempotencyKey()).orElse(record);
        }
        Instant retryAt = retryAfter == null ? now.plus(backoff(record.attempts() + 1)) : now.plus(retryAfter);
        TelegramOutboundSendRecord retry = repository.markRetry(record, category.name(), detail, retryAt, now);
        record("outbound_retry", "scheduled", retry.attempts(), "none", category.name(), now, null, record.chatHash());
        return retry;
    }

    private void record(String operation, String outcome, int retryCount, String sessionState, String errorCategory,
            Instant startedAt, String instanceRef, String actorRef) {
        if (observabilityRecorder != null) {
            observabilityRecorder.record(operation, outcome, Duration.between(startedAt, clock.instant()), retryCount,
                    sessionState, errorCategory, instanceRef, actorRef);
        }
    }

    private boolean retryable(FailureCategory category) {
        return category == FailureCategory.NETWORK
                || category == FailureCategory.TELEGRAM_5XX
                || category == FailureCategory.RATE_LIMIT;
    }

    private Duration backoff(int attempt) {
        long seconds = Math.min(60, (long) Math.pow(2, Math.max(0, attempt - 1)));
        return Duration.ofSeconds(seconds);
    }

    private FailureCategory categorize(TelegramClientException ex) {
        int status = ex.statusCode();
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        if (status == 429) {
            return FailureCategory.RATE_LIMIT;
        }
        if (status >= 500) {
            return FailureCategory.TELEGRAM_5XX;
        }
        if (status == 401) {
            return FailureCategory.UNAUTHORIZED_BOT;
        }
        if (status == 403 && message.contains("blocked")) {
            return FailureCategory.CHAT_BLOCKED;
        }
        if (status == 403) {
            return FailureCategory.DISABLED_CHANNEL;
        }
        if (status == 400 && message.contains("chat")) {
            return FailureCategory.INVALID_CHAT;
        }
        if (status >= 400 && status < 500) {
            return FailureCategory.MALFORMED_REQUEST;
        }
        return FailureCategory.UNKNOWN;
    }

    private static TelegramOutboundClient configuredClient(ObjectProvider<TelegramOutboundClient> clientProvider) {
        return clientProvider.getIfAvailable(() -> message -> {
            throw new TelegramOutboundClientNotConfiguredException(
                    "Telegram outbound client is not configured; provide a TelegramOutboundClient transport before enabling outbound sends.");
        });
    }

    private String idempotencyKey(TelegramRenderedMessage message, String correlationId, String operation) {
        String basis = safe(correlationId)
                + ":"
                + safe(operation)
                + ":"
                + safe(message.chatId())
                + ":"
                + safe(message.callbackQueryId())
                + ":"
                + safe(message.text());
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(basis.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to derive Telegram outbound idempotency key.", ex);
        }
    }

    private String preview(TelegramRenderedMessage message) {
        String value = message.text() == null ? message.callbackAcknowledgement() : message.text();
        value = sanitize(value);
        return value.length() > 240 ? value.substring(0, 240) : value;
    }

    private String sanitize(String value) {
        return safe(value).replaceAll("(?i)(bot)?token[=: ][^\\s]+", "$1token=[redacted]")
                .replaceAll("(?i)(secret|webhook)[=: ][^\\s]+", "$1=[redacted]")
                .replaceAll("(?i)prompt text:[\\s\\S]*", "prompt text:[redacted]");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class TelegramOutboundClientNotConfiguredException extends IllegalStateException {
        private TelegramOutboundClientNotConfiguredException(String message) {
            super(message);
        }
    }
}
