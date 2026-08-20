package com.cadentia.bot.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class TelegramI18nTest {
    @Test
    void loadsLocalizedTextAndLabelsFromResourceBundles() {
        Locale spanish = TelegramI18n.locale("es-MX");

        assertThat(TelegramI18n.text("welcome", spanish)).isEqualTo("Bienvenido a Cadentia.");
        assertThat(TelegramI18n.label("serviceMoment", "altar_call", spanish)).isEqualTo("Llamado al altar");
        assertThat(TelegramI18n.format("proposalGenerated", spanish, "audit-1"))
                .isEqualTo("Se generó una propuesta determinista de lista. audit-1");
    }

    @Test
    void fallsBackToEnglishForUnsupportedLocalesAndUnknownLabels() {
        Locale french = TelegramI18n.locale("fr-FR");

        assertThat(TelegramI18n.text("help", french)).isEqualTo("Use /newsetlist to begin.");
        assertThat(TelegramI18n.label("language", "fr", french)).isEqualTo("fr");
    }
}
