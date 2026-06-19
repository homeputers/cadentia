package com.cadentia.api.controller;

import com.cadentia.bot.telegram.TelegramWebhookIdempotencyStore;
import com.cadentia.bot.telegram.TelegramWebhookIdempotencyStore.IdempotencyResult;
import com.cadentia.bot.telegram.TelegramWebhookProperties;
import com.cadentia.generated.api.TelegramApi;
import com.cadentia.generated.model.TelegramCallbackQuery;
import com.cadentia.generated.model.TelegramChat;
import com.cadentia.generated.model.TelegramMessage;
import com.cadentia.generated.model.TelegramUpdate;
import com.cadentia.generated.model.TelegramValidationError;
import com.cadentia.generated.model.TelegramWebhookAcceptedResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@RestController
public class TelegramWebhookController implements TelegramApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelegramWebhookController.class);

    private final TelegramWebhookProperties properties;
    private final TelegramWebhookIdempotencyStore idempotencyStore;
    private final TelegramWebhookProblemFactory problemFactory;
    private final Clock clock;
    private final ObjectProvider<NativeWebRequest> nativeWebRequestProvider;

    @Autowired
    public TelegramWebhookController(
            TelegramWebhookProperties properties,
            TelegramWebhookIdempotencyStore idempotencyStore,
            TelegramWebhookProblemFactory problemFactory,
            ObjectProvider<NativeWebRequest> nativeWebRequestProvider) {
        this(properties, idempotencyStore, problemFactory, Clock.systemUTC(), nativeWebRequestProvider);
    }

    TelegramWebhookController(
            TelegramWebhookProperties properties,
            TelegramWebhookIdempotencyStore idempotencyStore,
            TelegramWebhookProblemFactory problemFactory,
            Clock clock) {
        this(properties, idempotencyStore, problemFactory, clock, null);
    }

    TelegramWebhookController(
            TelegramWebhookProperties properties,
            TelegramWebhookIdempotencyStore idempotencyStore,
            TelegramWebhookProblemFactory problemFactory,
            Clock clock,
            ObjectProvider<NativeWebRequest> nativeWebRequestProvider) {
        this.properties = properties;
        this.idempotencyStore = idempotencyStore;
        this.problemFactory = problemFactory;
        this.clock = clock;
        this.nativeWebRequestProvider = nativeWebRequestProvider;
    }

    @Override
    public Optional<NativeWebRequest> getRequest() {
        if (nativeWebRequestProvider == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(nativeWebRequestProvider.getIfAvailable());
    }

    @Override
    public ResponseEntity<TelegramWebhookAcceptedResponse> acceptTelegramWebhookUpdate(
            String botId,
            String xTelegramBotApiSecretToken,
            TelegramUpdate telegramUpdate) {
        RequestMetadata metadata = requestMetadata();
        List<TelegramValidationError> errors = validate(telegramUpdate);
        Long updateId = telegramUpdate == null ? null : telegramUpdate.getUpdateId();
        String channelId = extractChannelId(telegramUpdate).orElse("unknown");
        if (!errors.isEmpty()) {
            logFailure("REJECTED", updateId, metadata, botId, channelId, "VALIDATION_FAILED");
            throw problem(HttpStatus.BAD_REQUEST, "invalid-telegram-update", "Telegram update failed validation.", metadata, errors);
        }
        if (isStale(telegramUpdate)) {
            logFailure("REJECTED", updateId, metadata, botId, channelId, "STALE_UPDATE");
            throw problem(HttpStatus.BAD_REQUEST, "stale-telegram-update", "Telegram update is outside the accepted replay window.", metadata, List.of());
        }

        IdempotencyResult result = idempotencyStore.record(botId, channelId, updateId);
        LOGGER.info(
                "telegram_webhook outcome={} updateId={} requestId={} correlationId={} botId={} channelId={} failureCategory={}",
                result.name(), updateId, metadata.requestId(), metadata.correlationId(), botId, channelId, "NONE");
        return ResponseEntity.accepted().body(accepted(result, updateId, botId, channelId));
    }

    private List<TelegramValidationError> validate(TelegramUpdate update) {
        List<TelegramValidationError> errors = new ArrayList<>();
        if (update == null) {
            errors.add(error("/", "REQUIRED_OBJECT", "Payload must be a JSON object."));
            return errors;
        }
        if (update.getUpdateId() == null) {
            errors.add(error("/update_id", "REQUIRED_INT64", "update_id is required."));
        }
        long supported = Stream.of(
                        update.getMessage(),
                        update.getEditedMessage(),
                        update.getChannelPost(),
                        update.getCallbackQuery())
                .filter(value -> value != null)
                .count();
        if (supported != 1) {
            errors.add(error("/", "ONE_SUPPORTED_UPDATE_KIND", "Exactly one supported update kind is required."));
        }
        extractMessage(update).ifPresent(message -> validateMessage(message, errors));
        return errors;
    }

    private void validateMessage(TelegramMessage message, List<TelegramValidationError> errors) {
        if (message.getMessageId() == null) {
            errors.add(error("/message/message_id", "REQUIRED_INT64", "message_id is required."));
        }
        TelegramChat chat = message.getChat();
        if (chat == null || chat.getId() == null || chat.getType() == null || !StringUtils.hasText(chat.getType().getValue())) {
            errors.add(error("/message/chat", "REQUIRED_CHAT", "chat.id and chat.type are required."));
        }
        if (message.getText() != null && message.getText().length() > 4096) {
            errors.add(error("/message/text", "MAX_LENGTH", "message text exceeds Telegram limit."));
        }
    }

    private boolean isStale(TelegramUpdate update) {
        Optional<TelegramMessage> message = extractMessage(update);
        if (message.isEmpty() || message.get().getDate() == null) {
            return false;
        }
        Instant updateTime = Instant.ofEpochSecond(message.get().getDate());
        return updateTime.isBefore(clock.instant().minus(properties.getMaxUpdateAge()));
    }

    private Optional<TelegramMessage> extractMessage(TelegramUpdate update) {
        if (update == null) {
            return Optional.empty();
        }
        if (update.getMessage() != null) {
            return Optional.of(update.getMessage());
        }
        if (update.getEditedMessage() != null) {
            return Optional.of(update.getEditedMessage());
        }
        if (update.getChannelPost() != null) {
            return Optional.of(update.getChannelPost());
        }
        TelegramCallbackQuery callbackQuery = update.getCallbackQuery();
        if (callbackQuery != null && callbackQuery.getMessage() != null) {
            return Optional.of(callbackQuery.getMessage());
        }
        return Optional.empty();
    }

    private Optional<String> extractChannelId(TelegramUpdate update) {
        return extractMessage(update)
                .map(TelegramMessage::getChat)
                .filter(chat -> chat.getId() != null)
                .map(chat -> Long.toString(chat.getId()));
    }

    private TelegramWebhookAcceptedResponse accepted(IdempotencyResult result, long updateId, String botId, String channelId) {
        TelegramWebhookAcceptedResponse.StatusEnum status = result == IdempotencyResult.ACCEPTED
                ? TelegramWebhookAcceptedResponse.StatusEnum.ACCEPTED
                : TelegramWebhookAcceptedResponse.StatusEnum.DUPLICATE_ACCEPTED;
        return new TelegramWebhookAcceptedResponse()
                .accepted(true)
                .updateId(updateId)
                .idempotencyKey(botId + ":" + channelId + ":" + updateId)
                .status(status)
                .queuedAt(OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }

    private TelegramValidationError error(String field, String code, String message) {
        return new TelegramValidationError().field(field).code(code).message(message);
    }

    private TelegramWebhookProblemException problem(
            HttpStatus status,
            String type,
            String detail,
            RequestMetadata metadata,
            List<TelegramValidationError> errors) {
        return new TelegramWebhookProblemException(
                status,
                problemFactory.problem(status, type, detail, metadata.correlationId(), errors));
    }

    private RequestMetadata requestMetadata() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            String requestId = attributes.getRequest().getHeader("X-Request-ID");
            if (!StringUtils.hasText(requestId)) {
                requestId = java.util.UUID.randomUUID().toString();
            }
            String correlationId = attributes.getRequest().getHeader("X-Correlation-ID");
            if (!StringUtils.hasText(correlationId)) {
                correlationId = requestId;
            }
            return new RequestMetadata(requestId, correlationId);
        }
        String generated = java.util.UUID.randomUUID().toString();
        return new RequestMetadata(generated, generated);
    }

    private void logFailure(
            String outcome,
            Long updateId,
            RequestMetadata metadata,
            String botId,
            String channelId,
            String failureCategory) {
        LOGGER.warn(
                "telegram_webhook outcome={} updateId={} requestId={} correlationId={} botId={} channelId={} failureCategory={}",
                outcome, updateId, metadata.requestId(), metadata.correlationId(), botId, channelId, failureCategory);
    }

    private record RequestMetadata(String requestId, String correlationId) {
    }
}
