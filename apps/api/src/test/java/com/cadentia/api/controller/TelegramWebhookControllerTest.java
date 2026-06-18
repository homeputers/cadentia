package com.cadentia.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.bot.telegram.TelegramSecretResolver;
import com.cadentia.bot.telegram.TelegramWebhookIdempotencyStore;
import com.cadentia.bot.telegram.TelegramWebhookProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;

class TelegramWebhookControllerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-18T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void rejectsMissingSecretBeforeIdempotencyOrDomainProcessing() throws Exception {
        // Arrange
        CapturingIdempotencyStore store = new CapturingIdempotencyStore();
        TelegramWebhookController controller = controller(store);

        // Act
        ResponseEntity<Map<String, Object>> response = controller.acceptTelegramWebhookUpdate(
                "bot-a", null, "req-1", "corr-1", validPayload(100, Instant.now(CLOCK)));

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).containsEntry("correlationId", "corr-1");
        assertThat(store.invocations).isZero();
    }

    @Test
    void acceptsCurrentAndPreviousSecretsForRotationOverlap() throws Exception {
        // Arrange
        CapturingIdempotencyStore store = new CapturingIdempotencyStore();
        TelegramWebhookController controller = controller(store);

        // Act
        ResponseEntity<Map<String, Object>> current = controller.acceptTelegramWebhookUpdate(
                "bot-a", "current-secret", "req-1", "corr-1", validPayload(101, Instant.now(CLOCK)));
        ResponseEntity<Map<String, Object>> previous = controller.acceptTelegramWebhookUpdate(
                "bot-a", "previous-secret", "req-2", "corr-2", validPayload(102, Instant.now(CLOCK)));

        // Assert
        assertThat(current.getStatusCode().value()).isEqualTo(202);
        assertThat(previous.getStatusCode().value()).isEqualTo(202);
        assertThat(store.invocations).isEqualTo(2);
    }

    @Test
    void rejectsInvalidAndBlankSecrets() throws Exception {
        // Arrange
        CapturingIdempotencyStore store = new CapturingIdempotencyStore();
        TelegramWebhookController controller = controller(store);

        // Act
        ResponseEntity<Map<String, Object>> invalid = controller.acceptTelegramWebhookUpdate(
                "bot-a", "wrong", "req-1", "corr-1", validPayload(103, Instant.now(CLOCK)));
        ResponseEntity<Map<String, Object>> blank = controller.acceptTelegramWebhookUpdate(
                "bot-a", " ", "req-2", "corr-2", validPayload(104, Instant.now(CLOCK)));

        // Assert
        assertThat(invalid.getStatusCode().value()).isEqualTo(403);
        assertThat(blank.getStatusCode().value()).isEqualTo(401);
        assertThat(store.invocations).isZero();
    }

    @Test
    void duplicateUpdateReturnsDuplicateAcceptedWithoutRerunningSideEffects() throws Exception {
        // Arrange
        CapturingIdempotencyStore store = new CapturingIdempotencyStore();
        TelegramWebhookController controller = controller(store);

        // Act
        ResponseEntity<Map<String, Object>> first = controller.acceptTelegramWebhookUpdate(
                "bot-a", "current-secret", "req-1", "corr-1", validPayload(105, Instant.now(CLOCK)));
        ResponseEntity<Map<String, Object>> duplicate = controller.acceptTelegramWebhookUpdate(
                "bot-a", "current-secret", "req-2", "corr-2", validPayload(105, Instant.now(CLOCK)));

        // Assert
        assertThat(first.getBody()).containsEntry("status", "ACCEPTED");
        assertThat(duplicate.getBody()).containsEntry("status", "DUPLICATE_ACCEPTED");
        assertThat(store.sideEffectInvocations).isEqualTo(1);
    }

    @Test
    void malformedUnsupportedAndStalePayloadsReturnStructuredValidationResponses() throws Exception {
        // Arrange
        CapturingIdempotencyStore store = new CapturingIdempotencyStore();
        TelegramWebhookController controller = controller(store);

        // Act
        ResponseEntity<Map<String, Object>> malformed = controller.acceptTelegramWebhookUpdate(
                "bot-a", "current-secret", "req-1", "corr-1", "{}");
        ResponseEntity<Map<String, Object>> unsupported = controller.acceptTelegramWebhookUpdate(
                "bot-a", "current-secret", "req-2", "corr-2", "{\"update_id\":106,\"poll\":{}}");
        ResponseEntity<Map<String, Object>> stale = controller.acceptTelegramWebhookUpdate(
                "bot-a", "current-secret", "req-3", "corr-3", validPayload(107, Instant.parse("2026-06-16T11:59:00Z")));

        // Assert
        assertThat(malformed.getStatusCode().value()).isEqualTo(400);
        assertThat(malformed.getBody()).containsKey("errors");
        assertThat(unsupported.getStatusCode().value()).isEqualTo(400);
        assertThat(stale.getStatusCode().value()).isEqualTo(400);
        assertThat(stale.getBody()).containsEntry("type", "https://cadentia.local/problems/telegram/stale-telegram-update");
        assertThat(store.invocations).isZero();
    }

    private TelegramWebhookController controller(CapturingIdempotencyStore store) {
        TelegramWebhookProperties properties = new TelegramWebhookProperties();
        properties.setBotTokenRef("env:TELEGRAM_BOT_TOKEN");
        properties.setSecretTokenRef("env:CURRENT_TELEGRAM_SECRET");
        properties.setPreviousSecretTokenRef("env:PREVIOUS_TELEGRAM_SECRET");
        properties.setMaxUpdateAge(Duration.ofHours(24));
        MockEnvironment environment = new MockEnvironment()
                .withProperty("TELEGRAM_BOT_TOKEN", "123456:bot-token")
                .withProperty("CURRENT_TELEGRAM_SECRET", "current-secret")
                .withProperty("PREVIOUS_TELEGRAM_SECRET", "previous-secret");
        return new TelegramWebhookController(properties, new TelegramSecretResolver(environment), store, OBJECT_MAPPER, CLOCK);
    }

    private String validPayload(long updateId, Instant date) {
        return """
                {
                  "update_id": %d,
                  "message": {
                    "message_id": 5,
                    "date": %d,
                    "chat": {"id": 42, "type": "private"},
                    "text": "sanitized"
                  }
                }
                """.formatted(updateId, date.getEpochSecond());
    }

    private static class CapturingIdempotencyStore implements TelegramWebhookIdempotencyStore {
        private int invocations;
        private int sideEffectInvocations;
        private final java.util.Set<String> keys = new java.util.HashSet<>();

        @Override
        public IdempotencyResult record(String botId, String channelId, long updateId) {
            invocations++;
            if (keys.add(botId + channelId + updateId)) {
                sideEffectInvocations++;
                return IdempotencyResult.ACCEPTED;
            }
            return IdempotencyResult.DUPLICATE_ACCEPTED;
        }
    }
}
