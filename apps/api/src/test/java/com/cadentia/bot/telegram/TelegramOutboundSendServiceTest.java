package com.cadentia.bot.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.bot.telegram.TelegramOutboundModels.FailureCategory;
import com.cadentia.bot.telegram.TelegramOutboundModels.OutboundStatus;
import com.cadentia.bot.telegram.TelegramOutboundModels.TelegramOutboundSendRecord;
import com.cadentia.bot.telegram.TelegramOutboundModels.TelegramSendResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Queue;
import org.junit.jupiter.api.Test;

class TelegramOutboundSendServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-19T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void retrySuccessUsesIdempotencyToPreventDuplicateSendAfterDelivered() {
        InMemoryTelegramOutboundRepository repository = new InMemoryTelegramOutboundRepository();
        ScriptedClient client = new ScriptedClient();
        client.results.add(new TelegramClientException(502, "Telegram server error token=secret"));
        client.results.add(TelegramSendResult.delivered("m-1"));
        TelegramOutboundSendService service = service(repository, client);
        TelegramRenderedMessage message = TelegramRenderedMessage.message("42", "Proposal ready", null);

        TelegramOutboundSendRecord first = service.send(message, "corr", "proposal");
        TelegramOutboundSendRecord second = service.send(message, "corr", "proposal");
        TelegramOutboundSendRecord third = service.send(message, "corr", "proposal");

        assertThat(first.status()).isEqualTo(OutboundStatus.RETRY_SCHEDULED);
        assertThat(first.failureCategory()).isEqualTo(FailureCategory.TELEGRAM_5XX);
        assertThat(first.sanitizedFailureDetail()).contains("token=[redacted]");
        assertThat(second.status()).isEqualTo(OutboundStatus.SENT);
        assertThat(third.status()).isEqualTo(OutboundStatus.SENT);
        assertThat(client.calls).isEqualTo(2);
    }

    @Test
    void rateLimitSchedulesRetryUsingTelegramRetryAfter() {
        InMemoryTelegramOutboundRepository repository = new InMemoryTelegramOutboundRepository();
        ScriptedClient client = new ScriptedClient();
        client.results.add(new TelegramClientException(429, "Too many requests", Duration.ofSeconds(17)));
        TelegramOutboundSendService service = service(repository, client);

        TelegramOutboundSendRecord record = service.send(TelegramRenderedMessage.message("42", "Status", null), "corr", "status");

        assertThat(record.status()).isEqualTo(OutboundStatus.RETRY_SCHEDULED);
        assertThat(record.failureCategory()).isEqualTo(FailureCategory.RATE_LIMIT);
        assertThat(record.nextAttemptAt()).isEqualTo(Instant.parse("2026-06-19T00:00:17Z"));
    }

    @Test
    void permanentFailureIsDeadLetteredWithOperatorSafeMetadata() {
        InMemoryTelegramOutboundRepository repository = new InMemoryTelegramOutboundRepository();
        ScriptedClient client = new ScriptedClient();
        client.results.add(new TelegramClientException(403, "bot was blocked by the user webhook=secret raw prompt text: private"));
        TelegramOutboundSendService service = service(repository, client);

        TelegramOutboundSendRecord record = service.send(TelegramRenderedMessage.message("42", "token=secret proposal", null), "corr", "proposal");

        assertThat(record.status()).isEqualTo(OutboundStatus.DEAD_LETTERED);
        assertThat(record.failureCategory()).isEqualTo(FailureCategory.CHAT_BLOCKED);
        assertThat(repository.deadLetters()).hasSize(1);
        assertThat(repository.deadLetters().get(0).chatHash()).isNotEqualTo("42");
        assertThat(repository.deadLetters().get(0).sanitizedPreview()).doesNotContain("secret");
        assertThat(repository.deadLetters().get(0).sanitizedFailureDetail()).doesNotContain("private");
    }

    private static TelegramOutboundSendService service(InMemoryTelegramOutboundRepository repository, ScriptedClient client) {
        return new TelegramOutboundSendService(repository, client, new TelegramIdentifierHasher("hash-secret"), CLOCK);
    }

    private static class ScriptedClient implements TelegramOutboundClient {
        private final Queue<Object> results = new ArrayDeque<>();
        private int calls;

        @Override
        public TelegramSendResult send(TelegramRenderedMessage message) {
            calls++;
            Object result = results.remove();
            if (result instanceof RuntimeException exception) {
                throw exception;
            }
            return (TelegramSendResult) result;
        }
    }
}
