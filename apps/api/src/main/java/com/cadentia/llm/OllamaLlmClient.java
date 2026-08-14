package com.cadentia.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
@ConditionalOnProperty(name = "cadentia.llm.enabled", havingValue = "true")
public class OllamaLlmClient implements LlmClient {

    private static final String PROVIDER = "ollama";

    private final LlmProperties properties;
    private final RestTemplate restTemplate;

    @Autowired
    public OllamaLlmClient(LlmProperties properties, RestTemplateBuilder restTemplateBuilder) {
        this.properties = properties;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(properties.getOllama().getTimeout())
                .setReadTimeout(properties.getOllama().getTimeout())
                .build();
    }

    OllamaLlmClient(LlmProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        if (!PROVIDER.equalsIgnoreCase(properties.getProvider())) {
            throw new LlmProviderException(
                    LlmProviderErrorCode.UNSUPPORTED_RESPONSE_SHAPE,
                    "Unsupported LLM provider configured.");
        }

        OllamaChatRequest chatRequest = new OllamaChatRequest(
                properties.getOllama().getModel(),
                false,
                false,
                List.of(
                        new OllamaMessage("system", request.systemPrompt()),
                        new OllamaMessage("user", request.userMessage())),
                new OllamaOptions(
                        request.generationOptions().temperature(),
                        request.generationOptions().topP(),
                        request.generationOptions().maxOutputTokens()));

        try {
            ResponseEntity<OllamaChatResponse> response = restTemplate.postForEntity(
                    chatUri(),
                    chatRequest,
                    OllamaChatResponse.class);
            return toLlmResponse(response);
        } catch (HttpStatusCodeException exception) {
            throw new LlmProviderException(
                    LlmProviderErrorCode.NON_SUCCESS_STATUS,
                    "Ollama returned non-success status.",
                    exception);
        } catch (ResourceAccessException exception) {
            if (isTimeout(exception)) {
                throw new LlmProviderException(
                        LlmProviderErrorCode.TIMEOUT,
                        "Ollama request timed out.",
                        exception);
            }
            throw new LlmProviderException(
                    LlmProviderErrorCode.CONNECTION_FAILURE,
                    "Ollama could not be reached before the configured timeout.",
                    exception);
        } catch (RestClientException exception) {
            throw new LlmProviderException(
                    LlmProviderErrorCode.UNSUPPORTED_RESPONSE_SHAPE,
                    "Ollama response could not be read.",
                    exception);
        }
    }

    private URI chatUri() {
        String baseUrl = properties.getOllama().getBaseUrl();
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(normalized + "/api/chat");
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private LlmResponse toLlmResponse(ResponseEntity<OllamaChatResponse> response) {
        HttpStatusCode statusCode = response.getStatusCode();
        if (!statusCode.is2xxSuccessful()) {
            throw new LlmProviderException(
                    LlmProviderErrorCode.NON_SUCCESS_STATUS,
                    "Ollama returned non-success status.");
        }
        OllamaChatResponse body = response.getBody();
        if (body == null) {
            throw new LlmProviderException(
                    LlmProviderErrorCode.UNSUPPORTED_RESPONSE_SHAPE,
                    "Ollama response body was empty.");
        }
        if (body.error() != null && !body.error().isBlank()) {
            throw new LlmProviderException(
                    LlmProviderErrorCode.NON_SUCCESS_STATUS,
                    "Ollama returned an error response.");
        }
        if (body.message() == null || body.message().content() == null) {
            throw new LlmProviderException(
                    LlmProviderErrorCode.UNSUPPORTED_RESPONSE_SHAPE,
                    "Ollama response did not include message content.");
        }
        String content = stripMarkdownFences(body.message().content().trim());
        if (content.isBlank()) {
            throw new LlmProviderException(
                    LlmProviderErrorCode.UNSUPPORTED_RESPONSE_SHAPE,
                    "Ollama response content was empty.");
        }
        return new LlmResponse(
                content,
                PROVIDER,
                Objects.requireNonNullElse(body.model(), properties.getOllama().getModel()),
                Map.of("done", Boolean.toString(body.done())));
    }

    /**
     * Strips Markdown code fences (e.g., {@code ```json ... ```}) from LLM output.
     * Local models frequently ignore "JSON only" instructions and wrap responses.
     */
    static String stripMarkdownFences(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String stripped = raw;
        // Remove opening fence with optional language tag (e.g., ```json)
        if (stripped.startsWith("```")) {
            int firstNewline = stripped.indexOf('\n');
            if (firstNewline != -1) {
                stripped = stripped.substring(firstNewline + 1);
            } else {
                stripped = stripped.substring(3);
                // Strip language tag if present on same line (e.g., ```json{...})
                int contentStart = 0;
                while (contentStart < stripped.length()
                        && (Character.isLetterOrDigit(stripped.charAt(contentStart))
                                || stripped.charAt(contentStart) == '_' || stripped.charAt(contentStart) == '-')) {
                    contentStart++;
                }
                stripped = stripped.substring(contentStart);
            }
        }
        // Remove closing fence
        if (stripped.endsWith("```")) {
            stripped = stripped.substring(0, stripped.length() - 3);
        }
        return stripped.trim();
    }

    private record OllamaChatRequest(
            String model,
            boolean stream,
            boolean think,
            List<OllamaMessage> messages,
            OllamaOptions options) {}

    private record OllamaMessage(String role, String content) {}

    private record OllamaOptions(
            double temperature,
            @JsonProperty("top_p") double topP,
            @JsonProperty("num_predict") int numPredict) {}

    private record OllamaChatResponse(
            String model,
            OllamaMessage message,
            boolean done,
            String error) {}
}
