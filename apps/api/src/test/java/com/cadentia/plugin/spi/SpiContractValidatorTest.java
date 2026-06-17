package com.cadentia.plugin.spi;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SpiContractValidatorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SpiContractValidator validator = new SpiContractValidator();

    @ParameterizedTest
    @MethodSource("extensionPoints")
    void validatesRepresentativeOutputFixtures(String extensionPoint) throws IOException {
        JsonNode payload = fixture("valid/" + extensionPoint.toLowerCase() + "-output.json");

        assertThatCode(() -> validator.validateOutput(extensionPoint, payload)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @MethodSource("invalidPayloads")
    void rejectsCompatibilityAndPolicyViolations(String extensionPoint, String fixtureName) throws IOException {
        JsonNode payload = fixture("invalid/" + fixtureName);

        assertThatThrownBy(() -> validator.validateOutput(extensionPoint, payload))
                .isInstanceOf(SpiContractValidationException.class);
    }

    static Stream<String> extensionPoints() {
        return Stream.of("IMPORT_CONNECTOR", "METADATA_TRANSFORM", "RECOMMENDATION_CONSTRAINT",
                "SCORING_CONTRIBUTION", "EXPORT_RENDERER", "OUTBOUND_PUBLISH_HOOK");
    }

    static Stream<Arguments> invalidPayloads() {
        List<String> extensionPoints = extensionPoints().toList();
        Stream<Arguments> common = extensionPoints.stream().flatMap(extensionPoint -> Stream.of(
                Arguments.of(extensionPoint, extensionPoint.toLowerCase() + "-missing-field.json"),
                Arguments.of(extensionPoint, extensionPoint.toLowerCase() + "-extra-field.json"),
                Arguments.of(extensionPoint, extensionPoint.toLowerCase() + "-invalid-enum.json"),
                Arguments.of(extensionPoint, extensionPoint.toLowerCase() + "-unsupported-version.json")));
        return Stream.concat(common, Stream.of(
                Arguments.of("RECOMMENDATION_CONSTRAINT", "recommendation_constraint-policy-unsafe.json"),
                Arguments.of("SCORING_CONTRIBUTION", "scoring_contribution-nondeterministic-range.json")));
    }

    private JsonNode fixture(String name) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/plugin-spi/v1/" + name)) {
            return objectMapper.readTree(stream);
        }
    }
}
