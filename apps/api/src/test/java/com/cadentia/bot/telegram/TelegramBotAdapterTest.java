package com.cadentia.bot.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TelegramBotAdapterTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-19T12:00:00Z"), ZoneOffset.UTC);

    private final CapturingGateway gateway = new CapturingGateway();

    @Test
    void normalizesCommandMessageAndPropagatesLocaleAndCorrelation() {
        // Arrange
        TelegramBotAdapter adapter = adapter(false);

        // Act
        TelegramAdapterResponse response = adapter.handleUpdate("""
                {
                  "update_id": 11,
                  "message": {
                    "message_id": 7,
                    "date": 1781870400,
                    "chat": {"id": 42},
                    "from": {"id": 99, "language_code": "es"},
                    "text": "/newsetlist please"
                  }
                }
                """, "corr-11");

        // Assert
        assertThat(response.status()).isEqualTo(TelegramAdapterResponseStatus.STARTED);
        assertThat(gateway.lastEvent.command()).isEqualTo(TelegramCommand.NEW_SETLIST);
        assertThat(gateway.lastEvent.chatId()).isEqualTo("42");
        assertThat(gateway.lastEvent.userId()).isEqualTo("99");
        assertThat(gateway.lastEvent.messageId()).isEqualTo(7);
        assertThat(gateway.lastEvent.locale().toLanguageTag()).isEqualTo("es");
        assertThat(gateway.lastEvent.correlationId()).isEqualTo("corr-11");
    }

    @Test
    void routesSupportedCommandsDeterministicallyAndDisablesSettingsByConfiguration() {
        // Arrange
        TelegramBotAdapter disabled = adapter(false);
        TelegramBotAdapter enabled = adapter(true);

        // Act / Assert
        assertThat(disabled.handleUpdate(commandPayload(12, "/start"), "corr").status()).isEqualTo(TelegramAdapterResponseStatus.STARTED);
        assertThat(disabled.handleUpdate(commandPayload(13, "/help"), "corr").status()).isEqualTo(TelegramAdapterResponseStatus.CONTINUED);
        assertThat(disabled.handleUpdate(commandPayload(14, "/status"), "corr").status()).isEqualTo(TelegramAdapterResponseStatus.CONTINUED);
        assertThat(disabled.handleUpdate(commandPayload(15, "/cancel"), "corr").status()).isEqualTo(TelegramAdapterResponseStatus.CANCELLED);
        assertThat(disabled.handleUpdate(commandPayload(16, "/settings"), "corr").status()).isEqualTo(TelegramAdapterResponseStatus.DISABLED);
        assertThat(enabled.handleUpdate(commandPayload(17, "/settings"), "corr").status()).isEqualTo(TelegramAdapterResponseStatus.CONTINUED);
    }

    @Test
    void routesRequestAccessCommandAndCallbackToGateway() {
        // Arrange
        TelegramBotAdapter adapter = adapter(false);

        // Act
        TelegramAdapterResponse commandResponse = adapter.handleUpdate(commandPayload(26, "/requestaccess"), "corr-26");

        // Assert
        assertThat(commandResponse.status()).isEqualTo(TelegramAdapterResponseStatus.CONTINUED);
        assertThat(commandResponse.message()).isEqualTo("requestAccess");
        assertThat(gateway.lastMethod).isEqualTo("requestAccess");
        assertThat(gateway.lastEvent.command()).isEqualTo(TelegramCommand.REQUEST_ACCESS);

        // Act
        TelegramAdapterResponse callbackResponse = adapter.handleUpdate(callbackPayload(27, "cad:v1:request_access:", 1781870400), "corr-27");

        // Assert
        assertThat(callbackResponse.status()).isEqualTo(TelegramAdapterResponseStatus.CONTINUED);
        assertThat(gateway.lastMethod).isEqualTo("menuSelection");
        assertThat(gateway.lastEvent.callbackAction()).isEqualTo(TelegramCallbackAction.REQUEST_ACCESS);
    }

    @Test
    void unsupportedCommandIsAcknowledgedWithoutInvokingFacade() {
        // Arrange
        TelegramBotAdapter adapter = adapter(false);

        // Act
        TelegramAdapterResponse response = adapter.handleUpdate(commandPayload(18, "/unknown"), "corr");

        // Assert
        assertThat(response.status()).isEqualTo(TelegramAdapterResponseStatus.UNSUPPORTED);
        assertThat(gateway.invocations).isZero();
    }

    @Test
    void mapsCallbackPayloadToAdr015GuidedField() {
        // Arrange
        TelegramBotAdapter adapter = adapter(false);

        // Act
        TelegramAdapterResponse response = adapter.handleUpdate(callbackPayload(19, "cad:v1:key_policy:minimal", 1781870400), "corr-19");

        // Assert
        assertThat(response.status()).isEqualTo(TelegramAdapterResponseStatus.CONTINUED);
        assertThat(response.guidedField()).isEqualTo("keyPolicy");
        assertThat(gateway.lastEvent.callbackAction()).isEqualTo(TelegramCallbackAction.KEY_POLICY);
        assertThat(gateway.lastEvent.callbackValue()).isEqualTo("minimal");
        assertThat(gateway.lastEvent.callbackQueryId()).isEqualTo("cb-1");
        assertThat(gateway.lastEvent.callbackMessageId()).isEqualTo(33);
        assertThat(gateway.lastEvent.locale().toLanguageTag()).isEqualTo("en-US");

        TelegramAdapterResponse serviceMoment = adapter.handleUpdate(callbackPayload(25, "cad:v1:service_moment:opening", 1781870400), "corr-25");
        assertThat(serviceMoment.guidedField()).isEqualTo("serviceMoment");
        assertThat(gateway.lastEvent.callbackAction()).isEqualTo(TelegramCallbackAction.SERVICE_MOMENT);
        assertThat(gateway.lastEvent.callbackValue()).isEqualTo("opening");
    }

    @Test
    void rejectsStaleInvalidAndJsonCallbackPayloadsSafely() {
        // Arrange
        TelegramBotAdapter adapter = adapter(false);
        String oversized = "cad:v1:language:" + "a".repeat(70);

        // Act / Assert
        assertThat(adapter.handleUpdate(callbackPayload(20, "cad:v1:language:en", 1781865000), "corr").status())
                .isEqualTo(TelegramAdapterResponseStatus.STALE_CALLBACK);
        assertThat(adapter.handleUpdate(callbackPayload(21, "{\"action\":\"language\"}", 1781870400), "corr").status())
                .isEqualTo(TelegramAdapterResponseStatus.INVALID);
        assertThat(adapter.handleUpdate(callbackPayload(22, oversized, 1781870400), "corr").status())
                .isEqualTo(TelegramAdapterResponseStatus.INVALID);
        assertThat(adapter.handleUpdate(callbackPayload(23, "cad:v1:not_real:x", 1781870400), "corr").status())
                .isEqualTo(TelegramAdapterResponseStatus.INVALID);
    }

    @Test
    void unsupportedUpdateTypesAreAcknowledgedWithoutInvokingFacade() {
        // Arrange
        TelegramBotAdapter adapter = adapter(false);

        // Act
        TelegramAdapterResponse response = adapter.handleUpdate("{\"update_id\":24,\"edited_message\":{}}", "corr");

        // Assert
        assertThat(response.status()).isEqualTo(TelegramAdapterResponseStatus.UNSUPPORTED);
        assertThat(gateway.invocations).isZero();
    }

    private TelegramBotAdapter adapter(boolean settingsEnabled) {
        return new TelegramBotAdapter(new ObjectMapper(), gateway, settingsEnabled, Duration.ofMinutes(30), CLOCK);
    }

    private String commandPayload(long updateId, String text) {
        return """
                {"update_id":%d,"message":{"message_id":7,"date":1781870400,"chat":{"id":42},"from":{"id":99,"language_code":"en"},"text":"%s"}}
                """.formatted(updateId, text);
    }

    private String callbackPayload(long updateId, String data, long messageDate) {
        return """
                {"update_id":%d,"callback_query":{"id":"cb-1","from":{"id":99,"language_code":"en-US"},"message":{"message_id":33,"date":%d,"chat":{"id":42}},"data":"%s"}}
                """.formatted(updateId, messageDate, data.replace("\"", "\\\""));
    }

    private static class CapturingGateway implements TelegramConversationGateway {
        private TelegramChannelEvent lastEvent;
        private String lastMethod;
        private int invocations;

        @Override
        public TelegramAdapterResponse start(TelegramChannelEvent event) {
            return capture("start", event, TelegramAdapterResponseStatus.STARTED, null);
        }

        @Override
        public TelegramAdapterResponse help(TelegramChannelEvent event) {
            return capture("help", event, TelegramAdapterResponseStatus.CONTINUED, null);
        }

        @Override
        public TelegramAdapterResponse newSetlist(TelegramChannelEvent event) {
            return capture("newSetlist", event, TelegramAdapterResponseStatus.STARTED, null);
        }

        @Override
        public TelegramAdapterResponse status(TelegramChannelEvent event) {
            return capture("status", event, TelegramAdapterResponseStatus.CONTINUED, null);
        }

        @Override
        public TelegramAdapterResponse cancel(TelegramChannelEvent event) {
            return capture("cancel", event, TelegramAdapterResponseStatus.CANCELLED, null);
        }

        @Override
        public TelegramAdapterResponse settings(TelegramChannelEvent event) {
            return capture("settings", event, TelegramAdapterResponseStatus.CONTINUED, null);
        }

        @Override
        public TelegramAdapterResponse text(TelegramChannelEvent event) {
            return capture("text", event, TelegramAdapterResponseStatus.CONTINUED, null);
        }

        @Override
        public TelegramAdapterResponse menuSelection(TelegramChannelEvent event) {
            return capture("menuSelection", event, TelegramAdapterResponseStatus.CONTINUED, event.callbackAction().guidedField());
        }

        @Override
        public TelegramAdapterResponse requestAccess(TelegramChannelEvent event) {
            return capture("requestAccess", event, TelegramAdapterResponseStatus.CONTINUED, null);
        }

        private TelegramAdapterResponse capture(String method, TelegramChannelEvent event, TelegramAdapterResponseStatus status, String guidedField) {
            invocations++;
            lastMethod = method;
            lastEvent = event;
            return new TelegramAdapterResponse(status, method, event, guidedField);
        }
    }
}
