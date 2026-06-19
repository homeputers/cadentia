package com.cadentia.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cadentia.bot.telegram.TelegramSecretResolver;
import com.cadentia.bot.telegram.TelegramWebhookAuthenticationFilter;
import com.cadentia.bot.telegram.TelegramWebhookIdempotencyStore;
import com.cadentia.bot.telegram.TelegramWebhookProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TelegramWebhookControllerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-19T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void rejectsMissingSecretBeforeIdempotencyOrDomainProcessing() throws Exception {
        // Arrange
        CapturingIdempotencyStore store = new CapturingIdempotencyStore();
        MockMvc mockMvc = mockMvc(store);

        // Act / Assert
        mockMvc.perform(post("/telegram/webhooks/bot-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Request-ID", "req-1")
                        .header("X-Correlation-ID", "corr-1")
                        .content(validPayload(100, Instant.now(CLOCK))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.correlationId").value("corr-1"))
                .andExpect(jsonPath("$.detail").value("Missing Telegram secret-token header."));
        assertThat(store.invocations).isZero();
    }

    @Test
    void acceptsCurrentAndPreviousSecretsForRotationOverlap() throws Exception {
        // Arrange
        CapturingIdempotencyStore store = new CapturingIdempotencyStore();
        MockMvc mockMvc = mockMvc(store);

        // Act / Assert
        mockMvc.perform(validRequest(101, "current-secret"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        mockMvc.perform(validRequest(102, "previous-secret"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        assertThat(store.invocations).isEqualTo(2);
    }

    @Test
    void rejectsInvalidAndBlankSecretsBeforeBodyProcessing() throws Exception {
        // Arrange
        CapturingIdempotencyStore store = new CapturingIdempotencyStore();
        MockMvc mockMvc = mockMvc(store);

        // Act / Assert
        mockMvc.perform(validRequest(103, "wrong"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Invalid Telegram secret-token header."));
        mockMvc.perform(validRequest(104, " "))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Missing Telegram secret-token header."));
        assertThat(store.invocations).isZero();
    }

    @Test
    void duplicateUpdateReturnsDuplicateAcceptedWithoutRerunningSideEffects() throws Exception {
        // Arrange
        CapturingIdempotencyStore store = new CapturingIdempotencyStore();
        MockMvc mockMvc = mockMvc(store);

        // Act / Assert
        mockMvc.perform(validRequest(105, "current-secret"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        mockMvc.perform(validRequest(105, "current-secret"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("DUPLICATE_ACCEPTED"));
        assertThat(store.sideEffectInvocations).isEqualTo(1);
    }

    @Test
    void malformedUnsupportedAndStalePayloadsReturnStructuredValidationResponses() throws Exception {
        // Arrange
        CapturingIdempotencyStore store = new CapturingIdempotencyStore();
        MockMvc mockMvc = mockMvc(store);

        // Act / Assert
        mockMvc.perform(authorizedRequest().content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED_INT64"));
        mockMvc.perform(authorizedRequest().content("{\"update_id\":106,\"poll\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("ONE_SUPPORTED_UPDATE_KIND"));
        mockMvc.perform(authorizedRequest().content(validPayload(107, Instant.parse("2026-06-17T11:59:00Z"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://cadentia.local/problems/telegram/stale-telegram-update"));
        assertThat(store.invocations).isZero();
    }

    @Test
    void unavailableBotTokenReturnsRetryableProblemWithoutProcessingPayload() throws Exception {
        // Arrange
        CapturingIdempotencyStore store = new CapturingIdempotencyStore();
        MockMvc mockMvc = mockMvc(store, false);

        // Act / Assert
        mockMvc.perform(validRequest(108, "current-secret"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type").value("https://cadentia.local/problems/telegram/telegram-secret-unavailable"));
        assertThat(store.invocations).isZero();
    }

    private MockMvc mockMvc(CapturingIdempotencyStore store) {
        return mockMvc(store, true);
    }

    private MockMvc mockMvc(CapturingIdempotencyStore store, boolean includeBotToken) {
        TelegramWebhookProperties properties = properties();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("CURRENT_TELEGRAM_SECRET", "current-secret")
                .withProperty("PREVIOUS_TELEGRAM_SECRET", "previous-secret");
        if (includeBotToken) {
            environment.withProperty("TELEGRAM_BOT_TOKEN", "123456:bot-token");
        }
        TelegramWebhookProblemFactory problemFactory = new TelegramWebhookProblemFactory();
        TelegramSecretResolver secretResolver = new TelegramSecretResolver(environment);
        TelegramWebhookController controller = new TelegramWebhookController(properties, store, problemFactory, CLOCK);
        TelegramWebhookAuthenticationFilter filter = new TelegramWebhookAuthenticationFilter(
                properties, secretResolver, problemFactory, OBJECT_MAPPER);
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new TelegramWebhookExceptionHandler())
                .addFilters(filter)
                .build();
    }

    private TelegramWebhookProperties properties() {
        TelegramWebhookProperties properties = new TelegramWebhookProperties();
        properties.setBotTokenRef("env:TELEGRAM_BOT_TOKEN");
        properties.setSecretTokenRef("env:CURRENT_TELEGRAM_SECRET");
        properties.setPreviousSecretTokenRef("env:PREVIOUS_TELEGRAM_SECRET");
        properties.setMaxUpdateAge(Duration.ofHours(24));
        return properties;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validRequest(
            long updateId,
            String secret) {
        return authorizedRequest(secret).content(validPayload(updateId, Instant.now(CLOCK)));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authorizedRequest() {
        return authorizedRequest("current-secret");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authorizedRequest(String secret) {
        return post("/telegram/webhooks/bot-a")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Telegram-Bot-Api-Secret-Token", secret)
                .header("X-Request-ID", "req-1")
                .header("X-Correlation-ID", "corr-1");
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
