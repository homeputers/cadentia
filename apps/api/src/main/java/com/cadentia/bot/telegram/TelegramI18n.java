package com.cadentia.bot.telegram;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/** Loads Telegram copy from locale resource bundles with an English fallback. */
final class TelegramI18n {
    private static final String BUNDLE_NAME = "telegram.messages";
    private static final ResourceBundle.Control NO_DEFAULT_LOCALE_FALLBACK = new ResourceBundle.Control() {
        @Override
        public Locale getFallbackLocale(String baseName, Locale locale) {
            return null;
        }
    };

    private TelegramI18n() {}

    static Locale locale(String configuredLocale) {
        if (configuredLocale == null || configuredLocale.isBlank()) {
            return Locale.US;
        }
        Locale parsed = Locale.forLanguageTag(configuredLocale.replace('_', '-'));
        return parsed.getLanguage().isBlank() ? Locale.US : parsed;
    }

    static String text(String key, Locale locale) {
        try {
            return bundle(locale).getString(key);
        } catch (MissingResourceException exception) {
            return key;
        }
    }

    static String label(String category, String value, Locale locale) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.replace('-', '_').toLowerCase(Locale.ROOT);
        if ("language".equals(category)) {
            normalized = normalized.split("_")[0];
        }
        String key = category + "." + normalized;
        String translated = text(key, locale);
        return key.equals(translated) ? value : translated;
    }

    static String format(String key, Locale locale, Object... arguments) {
        Locale formatLocale = locale == null ? Locale.US : locale;
        return String.format(formatLocale, text(key, locale), arguments);
    }

    private static ResourceBundle bundle(Locale locale) {
        Locale requested = locale == null ? Locale.US : locale;
        return ResourceBundle.getBundle(
                BUNDLE_NAME,
                requested,
                TelegramI18n.class.getClassLoader(),
                NO_DEFAULT_LOCALE_FALLBACK);
    }
}
