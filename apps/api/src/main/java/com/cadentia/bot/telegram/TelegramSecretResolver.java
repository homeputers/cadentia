package com.cadentia.bot.telegram;

import java.util.Optional;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TelegramSecretResolver {

    private final Environment environment;

    public TelegramSecretResolver(Environment environment) {
        this.environment = environment;
    }

    public Optional<String> resolve(String secretRef) {
        if (!StringUtils.hasText(secretRef)) {
            return Optional.empty();
        }
        if (secretRef.startsWith("env:")) {
            String name = secretRef.substring("env:".length());
            return Optional.ofNullable(environment.getProperty(name)).filter(StringUtils::hasText);
        }
        if (secretRef.startsWith("secret-manager:") || secretRef.startsWith("vault:")
                || secretRef.startsWith("aws-sm:") || secretRef.startsWith("gcp-sm:")
                || secretRef.startsWith("azure-kv:")) {
            return Optional.ofNullable(environment.getProperty(secretRef)).filter(StringUtils::hasText);
        }
        return Optional.empty();
    }
}
