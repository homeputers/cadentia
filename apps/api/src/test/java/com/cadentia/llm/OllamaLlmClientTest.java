package com.cadentia.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class OllamaLlmClientTest {

    private LlmProperties properties;
    private MockRestServiceServer server;
    private OllamaLlmClient client;

    @BeforeEach
    void setUp() {
        properties = new LlmProperties();
        properties.setEnabled(true);
        properties.getOllama().setBaseUrl("http://ollama.local");
        properties.getOllama().setModel("llama3.1:8b");
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        client = new OllamaLlmClient(properties, restTemplate);
    }

    @Test
    void completePostsChatRequestAndReturnsRawContent() {
        // Arrange
        server.expect(requestTo("http://ollama.local/api/chat"))
                .andExpect(content().string(containsString("\"model\":\"llama3.1:8b\"")))
                .andExpect(content().string(containsString("\"think\":false")))
                .andExpect(content().string(containsString("\"role\":\"system\"")))
                .andExpect(content().string(containsString("\"role\":\"user\"")))
                .andExpect(content().string(containsString("\"top_p\":0.9")))
                .andExpect(content().string(containsString("\"num_predict\":800")))
                .andRespond(withSuccess("""
                        {
                          "model": "llama3.1:8b",
                          "message": {
                            "role": "assistant",
                            "content": "{\\"intent\\":\\"UNSUPPORTED_REQUEST\\",\\"reasonCode\\":\\"UNSUPPORTED_ACTION\\",\\"safeMessage\\":\\"No\\"}"
                          },
                          "done": true
                        }
                        """, MediaType.APPLICATION_JSON));

        // Act
        LlmResponse response = client.complete(request());

        // Assert
        assertThat(response.provider()).isEqualTo("ollama");
        assertThat(response.model()).isEqualTo("llama3.1:8b");
        assertThat(response.content()).contains("\"intent\":\"UNSUPPORTED_REQUEST\"");
        assertThat(response.metadata()).containsEntry("done", "true");
        server.verify();
    }

    @Test
    void completeMapsNonSuccessStatusToProviderError() {
        // Arrange
        server.expect(requestTo("http://ollama.local/api/chat"))
                .andRespond(withServerError());

        // Act / Assert
        assertThatThrownBy(() -> client.complete(request()))
                .isInstanceOfSatisfying(LlmProviderException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(LlmProviderErrorCode.NON_SUCCESS_STATUS));
        server.verify();
    }

    @Test
    void completeRejectsEmptyContent() {
        // Arrange
        server.expect(requestTo("http://ollama.local/api/chat"))
                .andRespond(withSuccess("""
                        {
                          "model": "llama3.1:8b",
                          "message": {
                            "role": "assistant",
                            "content": "   "
                          },
                          "done": true
                        }
                        """, MediaType.APPLICATION_JSON));

        // Act / Assert
        assertThatThrownBy(() -> client.complete(request()))
                .isInstanceOfSatisfying(LlmProviderException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(LlmProviderErrorCode.UNSUPPORTED_RESPONSE_SHAPE));
        server.verify();
    }

    @Test
    void completeMapsOllamaErrorBodyToProviderError() {
        // Arrange
        server.expect(requestTo("http://ollama.local/api/chat"))
                .andRespond(withSuccess("""
                        {
                          "error": "model unavailable"
                        }
                        """, MediaType.APPLICATION_JSON));

        // Act / Assert
        assertThatThrownBy(() -> client.complete(request()))
                .isInstanceOfSatisfying(LlmProviderException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(LlmProviderErrorCode.NON_SUCCESS_STATUS));
        server.verify();
    }

        @Test
    void completeStripsMarkdownCodeFencesFromContent() {
        // Arrange
        server.expect(requestTo("http://ollama.local/api/chat"))
                .andRespond(withSuccess("""
                        {
                          "model": "llama3.1:8b",
                          "message": {
                            "role": "assistant",
                            "content": "```json\\n{\\"intent\\":\\"GENERATE_SETLIST\\",\\"slots\\":{\\"verseText\\":\\"Ephesians 6:11\\"}}\\n```"
                          },
                          "done": true
                        }
                        """, MediaType.APPLICATION_JSON));

        // Act
        LlmResponse response = client.complete(request());

        // Assert
        assertThat(response.content()).isEqualTo("{\"intent\":\"GENERATE_SETLIST\",\"slots\":{\"verseText\":\"Ephesians 6:11\"}}");
        server.verify();
    }

    @Test
    void stripMarkdownFencesHandlesVariousFormats() {
        // Assert
        assertThat(OllamaLlmClient.stripMarkdownFences("```json\n{\"a\":1}\n```")).isEqualTo("{\"a\":1}");
        assertThat(OllamaLlmClient.stripMarkdownFences("```\n{\"a\":1}\n```")).isEqualTo("{\"a\":1}");
        assertThat(OllamaLlmClient.stripMarkdownFences("{\"a\":1}")).isEqualTo("{\"a\":1}");
        assertThat(OllamaLlmClient.stripMarkdownFences("  {\"a\":1}  ")).isEqualTo("{\"a\":1}");
        assertThat(OllamaLlmClient.stripMarkdownFences("")).isEqualTo("");
        assertThat(OllamaLlmClient.stripMarkdownFences(null)).isNull();
        assertThat(OllamaLlmClient.stripMarkdownFences("```json{\"a\":1}```")).isEqualTo("{\"a\":1}");
    }

    private LlmRequest request() {
        return new LlmRequest(
                "intent-v1",
                "v1",
                "Return JSON only.",
                "Build a setlist from Psalm 100.",
                "",
                "correlation-1",
                properties.generationOptions());
    }
}
