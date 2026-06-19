package com.cadentia.bot.telegram;

import com.cadentia.api.controller.TelegramWebhookProblemFactory;
import com.cadentia.generated.model.TelegramProblemResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TelegramWebhookAuthenticationFilter extends OncePerRequestFilter {

    public static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    private static final Logger LOGGER = LoggerFactory.getLogger(TelegramWebhookAuthenticationFilter.class);
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final TelegramWebhookProperties properties;
    private final TelegramSecretResolver secretResolver;
    private final TelegramWebhookProblemFactory problemFactory;
    private final ObjectMapper objectMapper;

    @Autowired
    public TelegramWebhookAuthenticationFilter(
            ObjectProvider<TelegramWebhookProperties> propertiesProvider,
            ObjectProvider<TelegramSecretResolver> secretResolverProvider,
            ObjectProvider<TelegramWebhookProblemFactory> problemFactoryProvider,
            ObjectMapper objectMapper,
            Environment environment) {
        this(
                propertiesProvider.getIfAvailable(TelegramWebhookProperties::new),
                secretResolverProvider.getIfAvailable(() -> new TelegramSecretResolver(environment)),
                problemFactoryProvider.getIfAvailable(TelegramWebhookProblemFactory::new),
                objectMapper);
    }

    public TelegramWebhookAuthenticationFilter(
            TelegramWebhookProperties properties,
            TelegramSecretResolver secretResolver,
            TelegramWebhookProblemFactory problemFactory,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.secretResolver = secretResolver;
        this.problemFactory = problemFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !PATH_MATCHER.match("/telegram/webhooks/*", request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = safeHeaderOrGenerated(request, "X-Request-ID");
        String correlationId = safeHeaderOrDefault(request, "X-Correlation-ID", requestId);
        String botId = PATH_MATCHER.extractUriTemplateVariables("/telegram/webhooks/{botId}", request.getRequestURI()).get("botId");
        String secretToken = request.getHeader(SECRET_HEADER);

        if (!StringUtils.hasText(secretToken)) {
            writeProblem(response, HttpStatus.UNAUTHORIZED, "missing-secret-token", "Missing Telegram secret-token header.", correlationId);
            logFailure("REJECTED", requestId, correlationId, botId, "MISSING_SECRET");
            return;
        }
        if (!secretMatches(secretToken)) {
            writeProblem(response, HttpStatus.FORBIDDEN, "invalid-secret-token", "Invalid Telegram secret-token header.", correlationId);
            logFailure("REJECTED", requestId, correlationId, botId, "INVALID_SECRET");
            return;
        }
        if (secretResolver.resolve(properties.getBotTokenRef()).isEmpty()) {
            writeProblem(response, HttpStatus.INTERNAL_SERVER_ERROR, "telegram-secret-unavailable", "Telegram bot credential is unavailable.", correlationId);
            logFailure("RETRYABLE_FAILURE", requestId, correlationId, botId, "BOT_TOKEN_UNAVAILABLE");
            return;
        }
        long contentLength = request.getContentLengthLong();
        if (contentLength > properties.getMaxPayloadBytes()) {
            writeProblem(response, HttpStatus.BAD_REQUEST, "oversized-telegram-update", "Telegram update payload size is invalid.", correlationId);
            logFailure("REJECTED", requestId, correlationId, botId, "PAYLOAD_SIZE");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean secretMatches(String presented) {
        List<String> candidates = new ArrayList<>();
        secretResolver.resolve(properties.getSecretTokenRef()).ifPresent(candidates::add);
        secretResolver.resolve(properties.getPreviousSecretTokenRef()).ifPresent(candidates::add);
        return candidates.stream().anyMatch(candidate -> ConstantTimeSecretMatcher.matches(candidate, presented));
    }

    private void writeProblem(
            HttpServletResponse response,
            HttpStatus status,
            String type,
            String detail,
            String correlationId) throws IOException {
        TelegramProblemResponse problem = problemFactory.problem(status, type, detail, correlationId);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), problem);
    }

    private String safeHeaderOrGenerated(HttpServletRequest request, String header) {
        String value = request.getHeader(header);
        return StringUtils.hasText(value) ? value : java.util.UUID.randomUUID().toString();
    }

    private String safeHeaderOrDefault(HttpServletRequest request, String header, String fallback) {
        String value = request.getHeader(header);
        return StringUtils.hasText(value) ? value : fallback;
    }

    private void logFailure(String outcome, String requestId, String correlationId, String botId, String failureCategory) {
        LOGGER.warn(
                "telegram_webhook outcome={} updateId={} requestId={} correlationId={} botId={} channelId={} failureCategory={}",
                outcome, null, requestId, correlationId, botId, "unknown", failureCategory);
    }
}
