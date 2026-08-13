package com.cadentia.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cadentia.bot.telegram.InMemoryTelegramOutboundRepository;
import com.cadentia.bot.telegram.TelegramAdapterResponse;
import com.cadentia.bot.telegram.TelegramAdapterResponseStatus;
import com.cadentia.bot.telegram.TelegramBotAdapter;
import com.cadentia.bot.telegram.TelegramChannelEvent;
import com.cadentia.bot.telegram.TelegramClientException;
import com.cadentia.bot.telegram.TelegramEventKind;
import com.cadentia.bot.telegram.TelegramIdentifierHasher;
import com.cadentia.bot.telegram.TelegramOutboundClient;
import com.cadentia.bot.telegram.TelegramOutboundModels.OutboundStatus;
import com.cadentia.bot.telegram.TelegramOutboundModels.TelegramSendResult;
import com.cadentia.bot.telegram.TelegramOutboundRepository;
import com.cadentia.bot.telegram.TelegramOutboundSendService;
import com.cadentia.bot.telegram.TelegramRenderedMessage;
import com.cadentia.bot.telegram.TelegramResponseRenderer;
import com.cadentia.bot.telegram.TelegramSecretResolver;
import com.cadentia.bot.telegram.TelegramWebhookAuthenticationFilter;
import com.cadentia.bot.telegram.TelegramWebhookIdempotencyStore;
import com.cadentia.bot.telegram.TelegramWebhookProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    void acceptedUpdateRoutesThroughAdapterRendererAndOutboundSendService() throws Exception {
        // Arrange
        CapturingIdempotencyStore store = new CapturingIdempotencyStore();
        CapturingBotAdapter adapter = new CapturingBotAdapter();
        CapturingRenderer renderer = new CapturingRenderer();
        ScriptedOutboundClient client = new ScriptedOutboundClient();
        TelegramOutboundRepository outboundRepository = new InMemoryTelegramOutboundRepository();
        TelegramOutboundSendService outbound = outbound(outboundRepository, client);
        MockMvc mockMvc = mockMvc(store, adapter, renderer, outbound);

        // Act / Assert
        mockMvc.perform(validRequest(109, "current-secret"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        assertThat(adapter.updateJson).contains("\"update_id\":109");
        assertThat(adapter.correlationId).isEqualTo("corr-1");
        assertThat(renderer.responses).containsExactly(adapter.response);
        assertThat(client.sent).hasSize(1);
        assertThat(client.sent.get(0).text()).contains("rendered response");
        assertThat(outboundRepository.findByIdempotencyKey(client.lastIdempotencyKey)).isPresent()
                .get()
                .extracting(record -> record.status())
                .isEqualTo(OutboundStatus.SENT);
    }

    @Test
    void duplicateUpdateDoesNotRouteAdapterRendererOrOutboundAgain() throws Exception {
        // Arrange
        CapturingIdempotencyStore store = new CapturingIdempotencyStore();
        CapturingBotAdapter adapter = new CapturingBotAdapter();
        CapturingRenderer renderer = new CapturingRenderer();
        ScriptedOutboundClient client = new ScriptedOutboundClient();
        MockMvc mockMvc = mockMvc(store, adapter, renderer, outbound(new InMemoryTelegramOutboundRepository(), client));

        // Act / Assert
        mockMvc.perform(validRequest(110, "current-secret"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        mockMvc.perform(validRequest(110, "current-secret"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("DUPLICATE_ACCEPTED"));

        assertThat(adapter.invocations).isEqualTo(1);
        assertThat(renderer.responses).hasSize(1);
        assertThat(client.sent).hasSize(1);
    }

    @Test
    void outboundRetryResultDoesNotPreventWebhookAcknowledgement() throws Exception {
        // Arrange
        CapturingIdempotencyStore store = new CapturingIdempotencyStore();
        CapturingBotAdapter adapter = new CapturingBotAdapter();
        CapturingRenderer renderer = new CapturingRenderer();
        ScriptedOutboundClient client = new ScriptedOutboundClient();
        client.failures.add(new TelegramClientException(502, "Telegram unavailable token=secret"));
        TelegramOutboundRepository outboundRepository = new InMemoryTelegramOutboundRepository();
        MockMvc mockMvc = mockMvc(store, adapter, renderer, outbound(outboundRepository, client));

        // Act / Assert
        mockMvc.perform(validRequest(111, "current-secret"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        assertThat(client.sent).isEmpty();
        assertThat(outboundRepository.findByIdempotencyKey(client.lastIdempotencyKey)).isPresent()
                .get()
                .extracting(record -> record.status())
                .isEqualTo(OutboundStatus.RETRY_SCHEDULED);
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
        return mockMvc(store, includeBotToken, null, null, null);
    }

    private MockMvc mockMvc(
            CapturingIdempotencyStore store,
            TelegramBotAdapter adapter,
            TelegramResponseRenderer renderer,
            TelegramOutboundSendService outbound) {
        return mockMvc(store, true, adapter, renderer, outbound);
    }

    private MockMvc mockMvc(
            CapturingIdempotencyStore store,
            boolean includeBotToken,
            TelegramBotAdapter adapter,
            TelegramResponseRenderer renderer,
            TelegramOutboundSendService outbound) {
        TelegramWebhookProperties properties = properties();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("CURRENT_TELEGRAM_SECRET", "current-secret")
                .withProperty("PREVIOUS_TELEGRAM_SECRET", "previous-secret");
        if (includeBotToken) {
            environment.withProperty("TELEGRAM_BOT_TOKEN", "123456:bot-token");
        }
        TelegramWebhookProblemFactory problemFactory = new TelegramWebhookProblemFactory();
        TelegramSecretResolver secretResolver = new TelegramSecretResolver(environment);
        TelegramWebhookController controller = new TelegramWebhookController(
                properties, store, problemFactory, CLOCK, adapter, renderer, outbound, OBJECT_MAPPER);
        TelegramWebhookAuthenticationFilter filter = new TelegramWebhookAuthenticationFilter(
                properties, secretResolver, problemFactory, OBJECT_MAPPER);
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new TelegramWebhookExceptionHandler())
                .addFilters(filter)
                .build();
    }

    private TelegramOutboundSendService outbound(
            TelegramOutboundRepository repository,
            ScriptedOutboundClient client) {
        return new TelegramOutboundSendService(repository, client, new TelegramIdentifierHasher("hash-secret"));
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

    private static class CapturingBotAdapter extends TelegramBotAdapter {
        private int invocations;
        private String updateJson;
        private String correlationId;
        private final TelegramAdapterResponse response = new TelegramAdapterResponse(
                TelegramAdapterResponseStatus.CONTINUED,
                "Adapter response.",
                new TelegramChannelEvent(
                        109L,
                        TelegramEventKind.MESSAGE,
                        "42",
                        "99",
                        5,
                        "sanitized",
                        null,
                        null,
                        null,
                        null,
                        null,
                        Locale.ROOT,
                        "corr-1"),
                null);

        private CapturingBotAdapter() {
            super(OBJECT_MAPPER, null, false, Duration.ofMinutes(30), null, null);
        }

        @Override
        public TelegramAdapterResponse handleUpdate(String updateJson, String correlationId) {
            invocations++;
            this.updateJson = updateJson;
            this.correlationId = correlationId;
            return response;
        }
    }

    private static class CapturingRenderer extends TelegramResponseRenderer {
        private final List<TelegramAdapterResponse> responses = new ArrayList<>();

        @Override
        public List<TelegramRenderedMessage> render(TelegramAdapterResponse response) {
            responses.add(response);
            return List.of(TelegramRenderedMessage.message("42", "rendered response", null));
        }
    }

    private static class ScriptedOutboundClient implements TelegramOutboundClient {
        private final List<RuntimeException> failures = new ArrayList<>();
        private final List<TelegramRenderedMessage> sent = new ArrayList<>();
        private String lastIdempotencyKey;

        @Override
        public TelegramSendResult send(TelegramRenderedMessage message) {
            lastIdempotencyKey = outboundIdempotencyKey(message);
            if (!failures.isEmpty()) {
                throw failures.remove(0);
            }
            sent.add(message);
            return TelegramSendResult.delivered("telegram-message-" + sent.size());
        }

        private String outboundIdempotencyKey(TelegramRenderedMessage message) {
            String basis = "corr-1:telegram_webhook_continued:"
                    + (message.chatId() == null ? "" : message.chatId())
                    + ":"
                    + (message.callbackQueryId() == null ? "" : message.callbackQueryId())
                    + ":"
                    + (message.text() == null ? "" : message.text());
            try {
                return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(basis.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
