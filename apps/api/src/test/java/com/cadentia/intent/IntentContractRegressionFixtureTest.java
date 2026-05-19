package com.cadentia.intent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class IntentContractRegressionFixtureTest {

    private static final Path ROOT_FIXTURE_DIR = Path.of(
            "packages/intent-contracts/fixtures/v1/regression");
    private static final Path MODULE_FIXTURE_DIR = Path.of(
            "../../packages/intent-contracts/fixtures/v1/regression");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IntentValidationService validationService = new IntentValidationService(objectMapper);

    @Test
    void regressionFixturesMatchExpectedBackendValidationOutcomes() throws IOException {
        // Arrange
        List<Path> fixturePaths = fixturePaths();

        // Act / Assert
        assertThat(fixturePaths).hasSizeGreaterThanOrEqualTo(6);
        for (Path fixturePath : fixturePaths) {
            RegressionFixture fixture = readFixture(fixturePath);
            IntentValidationResult result = validationService.validate(fixture.llmOutput());

            assertThat(fixture.scenario())
                    .as("%s documents the user scenario", fixturePath.getFileName())
                    .contains(" ");
            assertThat(fixture.userRequest())
                    .as("%s documents the representative user request", fixturePath.getFileName())
                    .contains(" ");
            assertThat(result.isAccepted())
                    .as("%s accepted outcome", fixturePath.getFileName())
                    .isEqualTo(fixture.expected().accepted());

            if (fixture.expected().accepted()) {
                assertAcceptedFixture(fixturePath, fixture, result);
            } else {
                assertRejectedFixture(fixturePath, fixture, result);
            }
        }
    }

    @Test
    void positiveFixturesNeverContainRecommendationOrCatalogDecisionFields() throws IOException {
        // Arrange
        List<RegressionFixture> positiveFixtures = fixturePaths().stream()
                .filter(path -> path.getFileName().toString().startsWith("positive-"))
                .map(this::readFixtureUnchecked)
                .toList();

        // Act / Assert
        assertThat(positiveFixtures).hasSize(2);
        for (RegressionFixture fixture : positiveFixtures) {
            JsonNode payload = objectMapper.readTree(fixture.llmOutput());
            String serializedPayload = payload.toString();

            assertThat(payload.get("intent").asText()).isEqualTo("GENERATE_SETLIST");
            assertThat(serializedPayload).doesNotContain(
                    "selectedSongs",
                    "approvalDecision",
                    "catalogFacts",
                    "arrangementIds",
                    "provenanceRecords",
                    "databaseWrites");
        }
    }

    private void assertAcceptedFixture(
            Path fixturePath,
            RegressionFixture fixture,
            IntentValidationResult result) {
        assertThat(result.errors()).as("%s has no errors", fixturePath.getFileName()).isEmpty();
        assertThat(result.intent().intentType().name())
                .as("%s intent", fixturePath.getFileName())
                .isEqualTo(fixture.expected().intent());
    }

    private void assertRejectedFixture(
            Path fixturePath,
            RegressionFixture fixture,
            IntentValidationResult result) {
        assertThat(result.errors())
                .as("%s error codes", fixturePath.getFileName())
                .extracting(error -> error.code().name())
                .containsExactlyElementsOf(fixture.expected().errorCodes());
        assertThat(result.errors())
                .as("%s error paths", fixturePath.getFileName())
                .extracting(IntentValidationError::path)
                .containsExactlyElementsOf(fixture.expected().errorPaths());
    }

    private RegressionFixture readFixtureUnchecked(Path fixturePath) {
        try {
            return readFixture(fixturePath);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private RegressionFixture readFixture(Path fixturePath) throws IOException {
        JsonNode root = objectMapper.readTree(Files.readString(fixturePath, StandardCharsets.UTF_8));
        JsonNode expected = root.get("expected");
        return new RegressionFixture(
                root.get("scenario").asText(),
                root.get("userRequest").asText(),
                root.get("llmOutput").asText(),
                new ExpectedOutcome(
                        expected.get("accepted").asBoolean(),
                        textOrNull(expected.get("intent")),
                        textArray(expected.get("errorCodes")),
                        textArray(expected.get("errorPaths"))));
    }

    private List<Path> fixturePaths() throws IOException {
        Path fixtureDir = Files.isDirectory(ROOT_FIXTURE_DIR) ? ROOT_FIXTURE_DIR : MODULE_FIXTURE_DIR;
        try (Stream<Path> paths = Files.list(fixtureDir)) {
            return paths
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static List<String> textArray(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        return Stream.iterate(0, index -> index + 1)
                .limit(node.size())
                .map(index -> node.get(index).asText())
                .toList();
    }

    private record RegressionFixture(
            String scenario,
            String userRequest,
            String llmOutput,
            ExpectedOutcome expected) {
    }

    private record ExpectedOutcome(
            boolean accepted,
            String intent,
            List<String> errorCodes,
            List<String> errorPaths) {
    }
}
