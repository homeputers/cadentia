package com.cadentia.bot.telegram;

import com.cadentia.bot.telegram.TelegramOutboundModels.TelegramSendResult;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Component
public class TelegramBotApiClient implements TelegramOutboundClient {
    private static final int CALLBACK_ACK_CACHE_SECONDS = 0;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final TelegramSecretResolver secretResolver;
    private final String botTokenRef;
    private final String baseUrl;

    public TelegramBotApiClient(
            ObjectMapper objectMapper,
            TelegramSecretResolver secretResolver,
            @Value("${cadentia.telegram.outbound.bot-token-ref:"
                    + "${cadentia.telegram.webhook.bot-token-ref:env:CADENTIA_TELEGRAM_BOT_TOKEN}}")
                    String botTokenRef,
            @Value("${cadentia.telegram.outbound.bot-api-base-url:https://api.telegram.org}") String baseUrl) {
        this(new RestTemplate(), objectMapper, secretResolver, botTokenRef, baseUrl);
    }

    TelegramBotApiClient(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            TelegramSecretResolver secretResolver,
            String botTokenRef,
            String baseUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.secretResolver = secretResolver;
        this.botTokenRef = botTokenRef;
        this.baseUrl = trimTrailingSlash(baseUrl);
    }

    @Override
    public TelegramSendResult send(TelegramRenderedMessage message) {
        String token = resolveToken();
        if (message.callbackOnly()) {
            answerCallbackQuery(token, message);
            return TelegramSendResult.delivered("callback:" + message.callbackQueryId());
        }
        return sendMessage(token, message);
    }

    private TelegramSendResult sendMessage(String token, TelegramRenderedMessage message) {
        if (!StringUtils.hasText(message.chatId())) {
            throw new TelegramClientException(400, "Telegram chat id is required for outbound message.");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chat_id", message.chatId());
        payload.put("text", message.text());
        payload.put("parse_mode", message.parseMode());
        payload.put("disable_web_page_preview", true);
        Map<String, Object> replyMarkup = replyMarkup(message.inlineKeyboard());
        if (!replyMarkup.isEmpty()) {
            payload.put("reply_markup", replyMarkup);
        }
        TelegramApiResponse response = exchange(token, "sendMessage", payload);
        Integer messageId = response.messageId();
        if (messageId == null) {
            throw new TelegramClientException(502, "Telegram sendMessage response omitted message id.");
        }
        return TelegramSendResult.delivered(String.valueOf(messageId));
    }

    private void answerCallbackQuery(String token, TelegramRenderedMessage message) {
        Map<String, Object> payload = Map.of(
                "callback_query_id", message.callbackQueryId(),
                "text", message.callbackAcknowledgement(),
                "show_alert", false,
                "cache_time", CALLBACK_ACK_CACHE_SECONDS);
        exchange(token, "answerCallbackQuery", payload);
    }

    private TelegramApiResponse exchange(String token, String method, Map<String, Object> payload) {
        try {
            ResponseEntity<TelegramApiResponse> response = restTemplate.postForEntity(
                    endpoint(token, method), new HttpEntity<>(payload, headers()), TelegramApiResponse.class);
            TelegramApiResponse body = response.getBody();
            if (body == null || !body.ok()) {
                throw apiException(response.getStatusCode().value(), body);
            }
            return body;
        } catch (HttpStatusCodeException ex) {
            throw apiException(ex.getStatusCode().value(), parseError(ex.getResponseBodyAsString()));
        }
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private Map<String, Object> replyMarkup(TelegramRenderedMessage.TelegramInlineKeyboard keyboard) {
        if (keyboard == null || keyboard.rows().isEmpty()) {
            return Map.of();
        }
        List<List<Map<String, String>>> rows = keyboard.rows().stream()
                .map(row -> row.stream()
                        .map(button -> Map.of("text", button.text(), "callback_data", button.callbackData()))
                        .toList())
                .toList();
        return Map.of("inline_keyboard", rows);
    }

    private TelegramClientException apiException(int status, TelegramApiResponse body) {
        String description = body == null || !StringUtils.hasText(body.description())
                ? "Telegram Bot API request failed."
                : body.description();
        return new TelegramClientException(status, description, retryAfter(body));
    }

    private Duration retryAfter(TelegramApiResponse body) {
        Integer retryAfter = body == null || body.parameters() == null ? null : body.parameters().retryAfter();
        return retryAfter == null ? null : Duration.ofSeconds(retryAfter);
    }

    private TelegramApiResponse parseError(String body) {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        try {
            return objectMapper.readValue(body, TelegramApiResponse.class);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private String resolveToken() {
        return secretResolver.resolve(botTokenRef)
                .orElseThrow(() -> new TelegramClientException(401, "Telegram bot token is unavailable."));
    }

    private String endpoint(String token, String method) {
        return baseUrl + "/bot" + token + "/" + method;
    }

    private static String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "https://api.telegram.org";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    record TelegramApiResponse(
            boolean ok,
            JsonNode result,
            String description,
            TelegramApiResponseParameters parameters) {

        Integer messageId() {
            if (result == null || !result.has("message_id")) {
                return null;
            }
            return result.get("message_id").asInt();
        }
    }

    record TelegramApiResponseParameters(@JsonProperty("retry_after") Integer retryAfter) {
    }
}
