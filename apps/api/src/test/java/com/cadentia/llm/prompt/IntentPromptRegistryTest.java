package com.cadentia.llm.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IntentPromptRegistryTest {

    private final IntentPromptRegistry registry = new IntentPromptRegistry();

    @Test
    void intentV1PromptMapsToSchemaV1() {
        // Arrange / Act
        IntentPromptTemplate template = registry.get("intent-v1");

        // Assert
        assertThat(template.promptVersion()).isEqualTo("intent-v1");
        assertThat(template.schemaVersion()).isEqualTo("v1");
        assertThat(registry.getForSchemaVersion("v1")).isSameAs(template);
    }

    @Test
    void registryRejectsUnknownPromptAndSchemaVersions() {
        // Arrange / Act / Assert
        assertThatThrownBy(() -> registry.get("intent-v2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported intent prompt version");
        assertThatThrownBy(() -> registry.getForSchemaVersion("v2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No intent prompt template registered");
    }

    @Test
    void intentV1PromptRequiresJsonOnlyAndForbidsProseOrMarkdown() {
        // Arrange
        String prompt = registry.get("intent-v1").systemPrompt();

        // Assert
        assertThat(prompt).contains("Return JSON only.");
        assertThat(prompt).contains("Do not produce prose, Markdown, code fences, explanations, or commentary.");
    }

    @Test
    void intentV1PromptContainsAdr012HallucinationGuardrails() {
        // Arrange
        String prompt = registry.get("intent-v1").systemPrompt();

        // Assert
        assertThat(prompt).contains(
                "Do not select songs.",
                "Do not output selected songs.",
                "Do not invent songs.",
                "Do not infer recommendability.",
                "Do not infer catalog availability.",
                "Do not claim a song exists in the catalog.",
                "Do not claim source, license, approval, or provenance records exist.",
                "Do not claim approvals.",
                "Do not claim or invent BPM, keys, tags, or CCLI numbers as catalog facts.",
                "Do not output arrangement identifiers, approval decisions, provenance records, or database write instructions.");
    }

    @Test
    void intentV1PromptDoesNotEmbedProviderSpecificSecretsOrModelNames() {
        // Arrange
        String prompt = registry.get("intent-v1").systemPrompt();

        // Assert
        assertThat(prompt).doesNotContain(
                "OPENAI_API_KEY",
                "OPENROUTER_API_KEY",
                "ANTHROPIC_API_KEY",
                "gpt-",
                "claude",
                "gemini");
    }

    @Test
    void intentV1PromptPreservesBackendValidationBoundary() {
        // Arrange
        String prompt = registry.get("intent-v1").systemPrompt();

        // Assert
        assertThat(prompt).contains(
                "Do not override backend validation.",
                "Backend validation and deterministic defaults are authoritative.",
                "Map only supported constraints into allowed slots.",
                "Return CLARIFY_REQUEST when required information is missing or ambiguous.",
                "Return UNSUPPORTED_REQUEST when the user asks for actions outside this contract.");
    }
}
