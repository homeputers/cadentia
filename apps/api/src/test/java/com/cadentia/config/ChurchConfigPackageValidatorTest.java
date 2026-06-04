package com.cadentia.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ChurchConfigPackageValidatorTest {
    private static final Path FIXTURE_ROOT = Path.of(
            "..",
            "..",
            "packages",
            "intent-contracts",
            "fixtures",
            "church-config",
            "v1");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChurchConfigPackageValidator validator = new ChurchConfigPackageValidator();

    @Test
    void acceptsCompletePackageBeforeStartup() throws IOException {
        // Arrange
        JsonNode payload = readFixture("valid/complete-package.json");

        // Act / Assert
        assertThatCode(() -> validator.validate(payload, "0.1.0")).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingMandatoryPolicySectionBeforeStartup() throws IOException {
        // Arrange
        JsonNode payload = readFixture("invalid/missing-policies.json");

        // Act / Assert
        assertThatThrownBy(() -> validator.validate(payload, "0.1.0"))
                .isInstanceOf(ChurchConfigValidationException.class)
                .hasMessageContaining("/policies");
    }

    @Test
    void rejectsUnknownCriticalSectionBeforeStartup() throws IOException {
        // Arrange
        JsonNode payload = readFixture("invalid/unknown-critical-section.json");

        // Act / Assert
        assertThatThrownBy(() -> validator.validate(payload, "0.1.0"))
                .isInstanceOf(ChurchConfigValidationException.class)
                .hasMessageContaining("unknown critical section");
    }

    @Test
    void rejectsMalformedIntegrationAndPluginReferencesBeforeStartup() throws IOException {
        // Arrange
        JsonNode malformedIntegration = readFixture("invalid/malformed-integration-reference.json");
        JsonNode malformedPlugin = readFixture("invalid/malformed-plugin-reference.json");

        // Act / Assert
        assertThatThrownBy(() -> validator.validate(malformedIntegration, "0.1.0"))
                .isInstanceOf(ChurchConfigValidationException.class)
                .hasMessageContaining("malformed integration reference");
        assertThatThrownBy(() -> validator.validate(malformedPlugin, "0.1.0"))
                .isInstanceOf(ChurchConfigValidationException.class)
                .hasMessageContaining("malformed plugin reference");
    }

    @Test
    void rejectsApplicationVersionOutsidePackageCompatibilityRange() throws IOException {
        // Arrange
        JsonNode payload = readFixture("invalid/incompatible-application-version.json");

        // Act / Assert
        assertThatThrownBy(() -> validator.validate(payload, "0.1.0"))
                .isInstanceOf(ChurchConfigValidationException.class)
                .hasMessageContaining("outside supported range");
    }

    private JsonNode readFixture(String name) throws IOException {
        return objectMapper.readTree(Files.readString(FIXTURE_ROOT.resolve(name)));
    }
}
