package com.cadentia.auth.service;

import com.cadentia.auth.config.AuthProperties;
import com.cadentia.auth.domain.AuthUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingPasswordResetNotifier implements PasswordResetNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingPasswordResetNotifier.class);
    private final AuthProperties properties;

    public LoggingPasswordResetNotifier(AuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public void send(AuthUser user, String rawToken) {
        if (properties.logPasswordResetTokens()) {
            LOGGER.warn("Password reset token for {} is {}. Configure a real notifier before production use.", user.email(), rawToken);
        }
    }
}
