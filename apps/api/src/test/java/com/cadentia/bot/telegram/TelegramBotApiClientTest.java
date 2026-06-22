package com.cadentia.bot.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.cadentia.bot.telegram.TelegramOutboundModels.TelegramSendResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class TelegramBotApiClientTest {

    @Test
    void sendsRenderedMessageThroughTelegramBotApi() {
        // Arrange
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        TelegramBotApiClient client = client(restTemplate, environmentWithToken(), "http://telegram.local");
        TelegramRenderedMessage message = TelegramRenderedMessage.message(
                "42",
                "<b>Proposal ready</b>",
                new TelegramRenderedMessage.TelegramInlineKeyboard(List.of(List.of(
                        new TelegramRenderedMessage.TelegramInlineKeyboardButton("Revise", "cad:revise")))));
        server.expect(once(), requestTo("http://telegram.local/bottest-token/sendMessage"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "chat_id": "42",
                          "text": "<b>Proposal ready</b>",
                          "parse_mode": "HTML",
                          "disable_web_page_preview": true,
                          "reply_markup": {
                            "inline_keyboard": [[{"text":"Revise","callback_data":"cad:revise"}]]
                          }
                        }
                        """))
                .andRespond(withSuccess("{\"ok\":true,\"result\":{\"message_id\":123}}", MediaType.APPLICATION_JSON));

        // Act
        TelegramSendResult result = client.send(message);

        // Assert
        assertThat(result.delivered()).isTrue();
        assertThat(result.telegramMessageId()).isEqualTo("123");
        server.verify();
    }

    @Test
    void acknowledgesCallbackQueriesThroughTelegramBotApi() {
        // Arrange
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        TelegramBotApiClient client = client(restTemplate, environmentWithToken(), "http://telegram.local/");
        server.expect(once(), requestTo("http://telegram.local/bottest-token/answerCallbackQuery"))
                .andExpect(content().json("""
                        {
                          "callback_query_id": "callback-1",
                          "text": "Received.",
                          "show_alert": false,
                          "cache_time": 0
                        }
                        """))
                .andRespond(withSuccess("{\"ok\":true,\"result\":true}", MediaType.APPLICATION_JSON));

        // Act
        TelegramSendResult result = client.send(TelegramRenderedMessage.callbackAck("callback-1", "Received."));

        // Assert
        assertThat(result.telegramMessageId()).isEqualTo("callback:callback-1");
        server.verify();
    }

    @Test
    void mapsTelegramRateLimitResponseToRetryableExceptionWithoutTokenLeak() {
        // Arrange
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        TelegramBotApiClient client = client(restTemplate, environmentWithToken(), "http://telegram.local");
        server.expect(once(), requestTo("http://telegram.local/bottest-token/sendMessage"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"ok\":false,\"description\":\"Too Many Requests\",\"parameters\":{\"retry_after\":17}}"));

        // Act / Assert
        assertThatThrownBy(() -> client.send(TelegramRenderedMessage.message("42", "Status", null)))
                .isInstanceOfSatisfying(TelegramClientException.class, exception -> {
                    assertThat(exception.statusCode()).isEqualTo(429);
                    assertThat(exception.retryAfter()).isEqualTo(Duration.ofSeconds(17));
                    assertThat(exception.getMessage()).doesNotContain("test-token");
                });
        server.verify();
    }

    @Test
    void failsSafelyWhenBotTokenIsUnavailable() {
        // Arrange
        RestTemplate restTemplate = new RestTemplate();
        TelegramBotApiClient client = client(restTemplate, new MockEnvironment(), "http://telegram.local");

        // Act / Assert
        assertThatThrownBy(() -> client.send(TelegramRenderedMessage.message("42", "Status", null)))
                .isInstanceOfSatisfying(TelegramClientException.class, exception -> {
                    assertThat(exception.statusCode()).isEqualTo(401);
                    assertThat(exception.getMessage()).doesNotContain("test-token");
                });
    }

    private static TelegramBotApiClient client(RestTemplate restTemplate, Environment environment, String baseUrl) {
        return new TelegramBotApiClient(
                restTemplate,
                new ObjectMapper(),
                new TelegramSecretResolver(environment),
                "env:CADENTIA_TELEGRAM_BOT_TOKEN",
                baseUrl);
    }

    private static Environment environmentWithToken() {
        return new MockEnvironment().withProperty("CADENTIA_TELEGRAM_BOT_TOKEN", "test-token");
    }
}
