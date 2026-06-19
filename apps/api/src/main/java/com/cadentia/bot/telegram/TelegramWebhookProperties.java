package com.cadentia.bot.telegram;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cadentia.telegram.webhook")
public class TelegramWebhookProperties {

    private String botTokenRef = "env:CADENTIA_TELEGRAM_BOT_TOKEN";
    private String secretTokenRef = "env:CADENTIA_TELEGRAM_WEBHOOK_SECRET";
    private String previousSecretTokenRef;
    private Duration maxUpdateAge = Duration.ofHours(24);
    private int maxPayloadBytes = 262144;

    public String getBotTokenRef() {
        return botTokenRef;
    }

    public void setBotTokenRef(String botTokenRef) {
        this.botTokenRef = botTokenRef;
    }

    public String getSecretTokenRef() {
        return secretTokenRef;
    }

    public void setSecretTokenRef(String secretTokenRef) {
        this.secretTokenRef = secretTokenRef;
    }

    public String getPreviousSecretTokenRef() {
        return previousSecretTokenRef;
    }

    public void setPreviousSecretTokenRef(String previousSecretTokenRef) {
        this.previousSecretTokenRef = previousSecretTokenRef;
    }

    public Duration getMaxUpdateAge() {
        return maxUpdateAge;
    }

    public void setMaxUpdateAge(Duration maxUpdateAge) {
        this.maxUpdateAge = maxUpdateAge;
    }

    public int getMaxPayloadBytes() {
        return maxPayloadBytes;
    }

    public void setMaxPayloadBytes(int maxPayloadBytes) {
        this.maxPayloadBytes = maxPayloadBytes;
    }
}
