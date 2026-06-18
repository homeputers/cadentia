package com.cadentia.api.controller;

import com.cadentia.bot.telegram.TelegramSecretResolver;
import com.cadentia.bot.telegram.TelegramWebhookIdempotencyStore;
import com.cadentia.bot.telegram.TelegramWebhookIdempotencyStore.IdempotencyResult;
import com.cadentia.bot.telegram.TelegramWebhookProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@EnableConfigurationProperties(TelegramWebhookProperties.class)
public class TelegramWebhookController {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelegramWebhookController.class);
    private static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    private final TelegramWebhookProperties properties;
    private final TelegramSecretResolver secretResolver;
    private final TelegramWebhookIdempotencyStore idempotencyStore;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TelegramWebhookController(
            TelegramWebhookProperties properties,
            TelegramSecretResolver secretResolver,
            TelegramWebhookIdempotencyStore idempotencyStore) {
        this(properties, secretResolver, idempotencyStore, new ObjectMapper(), Clock.systemUTC());
    }

    TelegramWebhookController(
            TelegramWebhookProperties properties,
            TelegramSecretResolver secretResolver,
            TelegramWebhookIdempotencyStore idempotencyStore,
            ObjectMapper objectMapper,
            Clock clock) {
        this.properties = properties;
        this.secretResolver = secretResolver;
        this.idempotencyStore = idempotencyStore;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @PostMapping("/telegram/webhooks/{botId}")
    public ResponseEntity<Map<String, Object>> acceptTelegramWebhookUpdate(
            @PathVariable String botId,
            @RequestHeader(value = SECRET_HEADER, required = false) String secretToken,
            @RequestHeader(value = "X-Request-ID", required = false) String requestId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestBody(required = false) String rawPayload) {
        String safeRequestId = StringUtils.hasText(requestId) ? requestId : UUID.randomUUID().toString();
        String safeCorrelationId = StringUtils.hasText(correlationId) ? correlationId : safeRequestId;

        if (!StringUtils.hasText(secretToken)) {
            logFailure("REJECTED", null, safeRequestId, safeCorrelationId, botId, "MISSING_SECRET");
            return problem(HttpStatus.UNAUTHORIZED, "missing-secret-token", "Missing Telegram secret-token header.", safeCorrelationId);
        }
        if (!secretMatches(secretToken)) {
            logFailure("REJECTED", null, safeRequestId, safeCorrelationId, botId, "INVALID_SECRET");
            return problem(HttpStatus.FORBIDDEN, "invalid-secret-token", "Invalid Telegram secret-token header.", safeCorrelationId);
        }
        if (secretResolver.resolve(properties.getBotTokenRef()).isEmpty()) {
            logFailure("RETRYABLE_FAILURE", null, safeRequestId, safeCorrelationId, botId, "BOT_TOKEN_UNAVAILABLE");
            return problem(HttpStatus.INTERNAL_SERVER_ERROR, "telegram-secret-unavailable", "Telegram bot credential is unavailable.", safeCorrelationId);
        }

        if (rawPayload == null || rawPayload.getBytes(StandardCharsets.UTF_8).length > properties.getMaxPayloadBytes()) {
            logFailure("REJECTED", null, safeRequestId, safeCorrelationId, botId, "PAYLOAD_SIZE");
            return problem(HttpStatus.BAD_REQUEST, "oversized-telegram-update", "Telegram update payload size is invalid.", safeCorrelationId);
        }

        JsonNode payload;
        try {
            payload = objectMapper.readTree(rawPayload);
        } catch (JsonProcessingException ex) {
            logFailure("REJECTED", null, safeRequestId, safeCorrelationId, botId, "MALFORMED_JSON");
            return problem(HttpStatus.BAD_REQUEST, "malformed-telegram-update", "Telegram update payload is malformed JSON.", safeCorrelationId);
        }

        List<Map<String, String>> errors = validate(payload);
        Long updateId = payload.path("update_id").canConvertToLong() ? payload.path("update_id").asLong() : null;
        String channelId = extractChannelId(payload).orElse("unknown");
        if (!errors.isEmpty()) {
            logFailure("REJECTED", updateId, safeRequestId, safeCorrelationId, botId, "VALIDATION_FAILED");
            return problem(HttpStatus.BAD_REQUEST, "invalid-telegram-update", "Telegram update failed validation.", safeCorrelationId, errors);
        }
        if (isStale(payload)) {
            logFailure("REJECTED", updateId, safeRequestId, safeCorrelationId, botId, "STALE_UPDATE");
            return problem(HttpStatus.BAD_REQUEST, "stale-telegram-update", "Telegram update is outside the accepted replay window.", safeCorrelationId);
        }

        IdempotencyResult result = idempotencyStore.record(botId, channelId, updateId);
        LOGGER.info(
                "telegram_webhook outcome={} updateId={} requestId={} correlationId={} botId={} channelId={} failureCategory={}",
                result.name(), updateId, safeRequestId, safeCorrelationId, botId, channelId, "NONE");
        return ResponseEntity.accepted().body(accepted(result, updateId, botId, channelId));
    }

    private boolean secretMatches(String presented) {
        List<String> candidates = new ArrayList<>();
        secretResolver.resolve(properties.getSecretTokenRef()).ifPresent(candidates::add);
        secretResolver.resolve(properties.getPreviousSecretTokenRef()).ifPresent(candidates::add);
        return candidates.stream().anyMatch(candidate -> constantTimeEquals(candidate, presented));
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private List<Map<String, String>> validate(JsonNode payload) {
        List<Map<String, String>> errors = new ArrayList<>();
        if (payload == null || !payload.isObject()) {
            errors.add(error("/", "REQUIRED_OBJECT", "Payload must be a JSON object."));
            return errors;
        }
        if (!payload.has("update_id") || !payload.path("update_id").canConvertToLong()) {
            errors.add(error("/update_id", "REQUIRED_INT64", "update_id is required."));
        }
        long supported = List.of("message", "edited_message", "channel_post", "callback_query").stream().filter(payload::has).count();
        if (supported != 1) {
            errors.add(error("/", "ONE_SUPPORTED_UPDATE_KIND", "Exactly one supported update kind is required."));
        }
        extractMessage(payload).ifPresent(message -> {
            if (!message.path("message_id").canConvertToLong()) {
                errors.add(error("/message/message_id", "REQUIRED_INT64", "message_id is required."));
            }
            JsonNode chat = message.path("chat");
            if (!chat.isObject() || !chat.path("id").canConvertToLong() || !StringUtils.hasText(chat.path("type").asText(null))) {
                errors.add(error("/message/chat", "REQUIRED_CHAT", "chat.id and chat.type are required."));
            }
            if (message.path("text").isTextual() && message.path("text").asText().length() > 4096) {
                errors.add(error("/message/text", "MAX_LENGTH", "message text exceeds Telegram limit."));
            }
        });
        return errors;
    }

    private boolean isStale(JsonNode payload) {
        Optional<JsonNode> message = extractMessage(payload);
        if (message.isEmpty() || !message.get().path("date").canConvertToLong()) {
            return false;
        }
        Instant updateTime = Instant.ofEpochSecond(message.get().path("date").asLong());
        return updateTime.isBefore(clock.instant().minus(properties.getMaxUpdateAge()));
    }

    private Optional<JsonNode> extractMessage(JsonNode payload) {
        if (payload == null) {
            return Optional.empty();
        }
        for (String field : List.of("message", "edited_message", "channel_post")) {
            if (payload.path(field).isObject()) {
                return Optional.of(payload.path(field));
            }
        }
        if (payload.path("callback_query").path("message").isObject()) {
            return Optional.of(payload.path("callback_query").path("message"));
        }
        return Optional.empty();
    }

    private Optional<String> extractChannelId(JsonNode payload) {
        return extractMessage(payload)
                .filter(message -> message.path("chat").path("id").canConvertToLong())
                .map(message -> Long.toString(message.path("chat").path("id").asLong()));
    }

    private Map<String, Object> accepted(IdempotencyResult result, long updateId, String botId, String channelId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("accepted", true);
        response.put("updateId", updateId);
        response.put("idempotencyKey", botId + ":" + channelId + ":" + updateId);
        response.put("status", result.name());
        response.put("queuedAt", clock.instant().toString());
        return response;
    }

    private ResponseEntity<Map<String, Object>> problem(HttpStatus status, String type, String detail, String correlationId) {
        return problem(status, type, detail, correlationId, List.of());
    }

    private ResponseEntity<Map<String, Object>> problem(
            HttpStatus status, String type, String detail, String correlationId, List<Map<String, String>> errors) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "https://cadentia.local/problems/telegram/" + type);
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("correlationId", correlationId);
        if (!errors.isEmpty()) {
            body.put("errors", errors);
        }
        return ResponseEntity.status(status).body(body);
    }

    private Map<String, String> error(String field, String code, String message) {
        return Map.of("field", field, "code", code, "message", message);
    }

    private void logFailure(String outcome, Long updateId, String requestId, String correlationId, String botId, String failureCategory) {
        LOGGER.warn(
                "telegram_webhook outcome={} updateId={} requestId={} correlationId={} botId={} channelId={} failureCategory={}",
                outcome, updateId, requestId, correlationId, botId, "unknown", failureCategory);
    }
}
