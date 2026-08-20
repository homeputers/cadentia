package com.cadentia.bot.telegram;

import com.cadentia.bot.BotAdapter;
import com.cadentia.runtime.InstanceConfigurationProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TelegramBotAdapter implements BotAdapter {

    static final int TELEGRAM_CALLBACK_DATA_LIMIT = TelegramCallbackData.LIMIT;

    private final ObjectMapper objectMapper;
    private final TelegramConversationGateway conversationGateway;
    private final boolean settingsEnabled;
    private final Duration callbackTtl;
    private final Clock clock;
    private final TelegramObservabilityRecorder observabilityRecorder;
    private final TelegramOperationalControlService controlService;
    private final InstanceConfigurationProvider configurationProvider;

    @Autowired
    public TelegramBotAdapter(
            ObjectMapper objectMapper,
            TelegramConversationGateway conversationGateway,
            @Value("${cadentia.telegram.settings-enabled:false}") boolean settingsEnabled,
            @Value("${cadentia.telegram.callback-ttl:PT30M}") Duration callbackTtl,
            TelegramObservabilityRecorder observabilityRecorder,
            TelegramOperationalControlService controlService,
            InstanceConfigurationProvider configurationProvider) {
        this(objectMapper, conversationGateway, settingsEnabled, callbackTtl, Clock.systemUTC(), observabilityRecorder, controlService, configurationProvider);
    }

    TelegramBotAdapter(
            ObjectMapper objectMapper,
            TelegramConversationGateway conversationGateway,
            @Value("${cadentia.telegram.settings-enabled:false}") boolean settingsEnabled,
            @Value("${cadentia.telegram.callback-ttl:PT30M}") Duration callbackTtl,
            Clock clock) {
        this(objectMapper, conversationGateway, settingsEnabled, callbackTtl, clock, null, null, null);
    }

    public TelegramBotAdapter(
            ObjectMapper objectMapper,
            TelegramConversationGateway conversationGateway,
            boolean settingsEnabled,
            Duration callbackTtl,
            TelegramObservabilityRecorder observabilityRecorder,
            TelegramOperationalControlService controlService) {
        this(objectMapper, conversationGateway, settingsEnabled, callbackTtl, Clock.systemUTC(), observabilityRecorder, controlService, null);
    }

    TelegramBotAdapter(
            ObjectMapper objectMapper,
            TelegramConversationGateway conversationGateway,
            boolean settingsEnabled,
            Duration callbackTtl,
            Clock clock,
            TelegramObservabilityRecorder observabilityRecorder,
            TelegramOperationalControlService controlService) {
        this(objectMapper, conversationGateway, settingsEnabled, callbackTtl, clock, observabilityRecorder, controlService, null);
    }

    TelegramBotAdapter(
            ObjectMapper objectMapper,
            TelegramConversationGateway conversationGateway,
            boolean settingsEnabled,
            Duration callbackTtl,
            Clock clock,
            TelegramObservabilityRecorder observabilityRecorder,
            TelegramOperationalControlService controlService,
            InstanceConfigurationProvider configurationProvider) {
        this.objectMapper = objectMapper;
        this.conversationGateway = conversationGateway;
        this.settingsEnabled = settingsEnabled;
        this.callbackTtl = callbackTtl;
        this.clock = clock;
        this.observabilityRecorder = observabilityRecorder;
        this.controlService = controlService;
        this.configurationProvider = configurationProvider;
    }

    @Override
    public void handleMessage(String chatId, String message) {
        TelegramChannelEvent event = new TelegramChannelEvent(
                0L,
                TelegramEventKind.MESSAGE,
                chatId,
                chatId,
                null,
                message,
                TelegramCommand.fromText(message).orElse(null),
                null,
                null,
                null,
                null,
                Locale.ROOT,
                "telegram:legacy:" + chatId);
        route(event);
    }

    public TelegramAdapterResponse handleUpdate(String updateJson, String correlationId) {
        Instant startedAt = clock.instant();
        try {
            record("webhook_receipt", "received", startedAt, 0, "none", "none", null, null);
            TelegramChannelEvent event = normalize(objectMapper.readTree(updateJson), correlationId);
            record("update_normalization", "success", startedAt, 0, "none", "none", null, null);
            return route(event);
        } catch (IllegalArgumentException ex) {
            record("update_normalization", "failure", startedAt, 0, "none", "validation", null, null);
            return invalid(ex.getMessage(), correlationId);
        } catch (Exception ex) {
            record("update_normalization", "failure", startedAt, 0, "none", "malformed_json", null, null);
            return invalid("Invalid Telegram update payload.", correlationId);
        }
    }

    TelegramChannelEvent normalize(JsonNode update, String correlationId) {
        long updateId = requiredLong(update, "update_id");
        if (update.hasNonNull("message")) {
            return normalizeMessage(updateId, update.get("message"), correlationId);
        }
        if (update.hasNonNull("callback_query")) {
            return normalizeCallback(updateId, update.get("callback_query"), correlationId);
        }
        return new TelegramChannelEvent(updateId, TelegramEventKind.UNSUPPORTED, null, null, null, null, null, null, null, null, null, Locale.ROOT, correlationId);
    }

    private TelegramChannelEvent normalizeMessage(long updateId, JsonNode message, String correlationId) {
        String chatId = requiredText(message.path("chat"), "id");
        String userId = optionalText(message.path("from"), "id").orElse(chatId);
        String text = optionalText(message, "text").orElse(null);
        Integer messageId = optionalInt(message, "message_id").orElse(null);
        Locale locale = optionalText(message.path("from"), "language_code").map(Locale::forLanguageTag).orElse(Locale.ROOT);
        return new TelegramChannelEvent(updateId, TelegramEventKind.MESSAGE, chatId, userId, messageId, text,
                TelegramCommand.fromText(text).orElse(null), null, null, null, null, locale, correlationId);
    }

    private TelegramChannelEvent normalizeCallback(long updateId, JsonNode callback, String correlationId) {
        String callbackQueryId = requiredText(callback, "id");
        String userId = requiredText(callback.path("from"), "id");
        JsonNode message = callback.path("message");
        String chatId = requiredText(message.path("chat"), "id");
        Integer callbackMessageId = optionalInt(message, "message_id").orElse(null);
        Instant messageDate = optionalLong(message, "date").map(Instant::ofEpochSecond).orElse(null);
        if (messageDate != null && messageDate.plus(callbackTtl).isBefore(Instant.now(clock))) {
            throw new StaleCallbackException("Stale callback ignored.");
        }
        String data = requiredText(callback, "data");
        TelegramCallbackPayload payload = parseCallbackPayload(data);
        Locale locale = optionalText(callback.path("from"), "language_code").map(Locale::forLanguageTag).orElse(Locale.ROOT);
        return new TelegramChannelEvent(updateId, TelegramEventKind.CALLBACK_QUERY, chatId, userId, callbackMessageId, null,
                null, payload.action(), payload.value(), callbackQueryId, callbackMessageId, locale, correlationId);
    }

    public TelegramAdapterResponse route(TelegramChannelEvent event) {
        Instant startedAt = clock.instant();
        if (controlService != null && !controlService.acceptsInbound(null)) {
            record("channel_disablement", "disabled", startedAt, 0, "none", "disabled_channel", null, null);
            return new TelegramAdapterResponse(TelegramAdapterResponseStatus.DISABLED, message("channelDisabled"), event, null);
        }
        if (event.kind() == TelegramEventKind.UNSUPPORTED) {
            record("update_normalization", "unsupported", startedAt, 0, "none", "unsupported_update", null, null);
            return new TelegramAdapterResponse(TelegramAdapterResponseStatus.UNSUPPORTED, message("unsupportedUpdate"), event, null);
        }
        if (event.kind() == TelegramEventKind.CALLBACK_QUERY) {
            record("callback_routing", "routed", startedAt, 0, "active", "none", null, event.userId());
            return conversationGateway.menuSelection(event);
        }
        if (event.command() == null) {
            if (event.text() != null && event.text().startsWith("/")) {
                record("command_routing", "unsupported", startedAt, 0, "none", "unsupported_command", null, event.userId());
                return new TelegramAdapterResponse(TelegramAdapterResponseStatus.UNSUPPORTED, message("unsupportedCommand"), event, null);
            }
            if (event.text() == null) {
                record("command_routing", "unsupported", startedAt, 0, "none", "unsupported_message", null, event.userId());
                return new TelegramAdapterResponse(TelegramAdapterResponseStatus.UNSUPPORTED, message("unsupportedMessage"), event, null);
            }
            record("command_routing", "text", startedAt, 0, "active", "none", null, event.userId());
            return conversationGateway.text(event);
        }
        record("command_routing", event.command().name().toLowerCase(Locale.ROOT), startedAt, 0, "active", "none", null, event.userId());
        return switch (event.command()) {
            case START -> conversationGateway.start(event);
            case HELP -> conversationGateway.help(event);
            case NEW_SETLIST -> conversationGateway.newSetlist(event);
            case STATUS -> conversationGateway.status(event);
            case CANCEL -> conversationGateway.cancel(event);
            case SETTINGS -> settingsEnabled
                    ? conversationGateway.settings(event)
                    : new TelegramAdapterResponse(TelegramAdapterResponseStatus.DISABLED, message("settingsDisabled"), event, null);
        };
    }

    private TelegramCallbackPayload parseCallbackPayload(String data) {
        if (data.length() > TELEGRAM_CALLBACK_DATA_LIMIT || data.contains("{") || data.contains("}") || !data.startsWith(TelegramCallbackData.PREFIX)) {
            throw new IllegalArgumentException("Invalid Telegram callback payload.");
        }
        String[] parts = data.substring(TelegramCallbackData.PREFIX.length()).split(":", 2);
        TelegramCallbackAction action = TelegramCallbackAction.fromToken(parts[0])
                .orElseThrow(() -> new IllegalArgumentException("Unsupported Telegram callback action."));
        String value = parts.length == 2 ? parts[1] : "";
        if (!TelegramCallbackData.validValue(value)) {
            throw new IllegalArgumentException("Invalid Telegram callback value.");
        }
        return new TelegramCallbackPayload(action, value);
    }

    private String message(String key) {
        Locale configuredLocale = configurationProvider == null
                ? Locale.US
                : TelegramI18n.locale(configurationProvider.current().locale());
        return TelegramI18n.text(key, configuredLocale);
    }

    private void record(String operation, String outcome, Instant startedAt, int retryCount, String sessionState,
            String errorCategory, String instanceRef, String actorRef) {
        if (observabilityRecorder != null) {
            observabilityRecorder.record(operation, outcome, Duration.between(startedAt, clock.instant()), retryCount,
                    sessionState, errorCategory, instanceRef, actorRef);
        }
    }

    private TelegramAdapterResponse invalid(String detail, String correlationId) {
        TelegramAdapterResponseStatus status = "Stale callback ignored.".equals(detail)
                ? TelegramAdapterResponseStatus.STALE_CALLBACK
                : TelegramAdapterResponseStatus.INVALID;
        return new TelegramAdapterResponse(status, detail, null, null);
    }

    private long requiredLong(JsonNode node, String field) {
        return optionalLong(node, field).orElseThrow(() -> new IllegalArgumentException("Missing required Telegram field: " + field));
    }

    private String requiredText(JsonNode node, String field) {
        return optionalText(node, field).orElseThrow(() -> new IllegalArgumentException("Missing required Telegram field: " + field));
    }

    private Optional<String> optionalText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return Optional.empty();
        }
        return Optional.of(value.asText());
    }

    private Optional<Integer> optionalInt(JsonNode node, String field) {
        return optionalLong(node, field).map(Long::intValue);
    }

    private Optional<Long> optionalLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || !value.canConvertToLong()) {
            return Optional.empty();
        }
        return Optional.of(value.asLong());
    }

    private record TelegramCallbackPayload(TelegramCallbackAction action, String value) {}

    private static class StaleCallbackException extends IllegalArgumentException {
        StaleCallbackException(String message) {
            super(message);
        }
    }
}
