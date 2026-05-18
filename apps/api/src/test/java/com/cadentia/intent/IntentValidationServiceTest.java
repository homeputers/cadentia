package com.cadentia.intent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class IntentValidationServiceTest {

    private final IntentValidationService validationService = new IntentValidationService(new ObjectMapper());

    @Test
    void validateAppliesBackendDefaultsWhenOptionalGenerateSetlistSlotsAreMissing() {
        // Arrange
        String llmJson = """
                {
                  "intent": "GENERATE_SETLIST",
                  "slots": {}
                }
                """;

        // Act
        IntentValidationResult result = validationService.validate(llmJson);

        // Assert
        assertThat(result.isAccepted()).isTrue();
        assertThat(result.intent()).isInstanceOfSatisfying(GenerateSetlistIntent.class, intent -> {
            assertThat(intent.contractVersion()).isEqualTo("v1");
            assertThat(intent.slots().verseText()).isEmpty();
            assertThat(intent.slots().scriptureReferences()).isEmpty();
            assertThat(intent.slots().themeHints()).isEmpty();
            assertThat(intent.slots().counts()).isEqualTo(new Counts(10, 5));
            assertThat(intent.slots().keyPolicy()).isEqualTo(new IntentKeyPolicy(true, true, 2));
            assertThat(intent.slots().tempoPolicy()).isEqualTo(new IntentTempoPolicy(12));
            assertThat(intent.slots().language()).isNull();
            assertThat(intent.slots().energyArc()).isNull();
            assertThat(intent.slots().excludedSongs()).isEmpty();
            assertThat(intent.slots().serviceMoment()).isNull();
        });
    }

    @Test
    void validateAppliesBackendDefaultsWhenOptionalPolicyFieldsAreMissing() {
        // Arrange
        String llmJson = """
                {
                  "intent": "GENERATE_SETLIST",
                  "slots": {
                    "counts": { "praise": 4 },
                    "keyPolicy": { "preferSameKey": false },
                    "tempoPolicy": {}
                  }
                }
                """;

        // Act
        IntentValidationResult result = validationService.validate(llmJson);

        // Assert
        assertThat(result.isAccepted()).isTrue();
        assertThat(result.intent()).isInstanceOfSatisfying(GenerateSetlistIntent.class, intent -> {
            assertThat(intent.slots().counts()).isEqualTo(new Counts(4, 5));
            assertThat(intent.slots().keyPolicy()).isEqualTo(new IntentKeyPolicy(false, true, 2));
            assertThat(intent.slots().tempoPolicy()).isEqualTo(new IntentTempoPolicy(12));
        });
    }

    @Test
    void validateRejectsMalformedJsonWithoutReturningIntent() {
        // Arrange
        String llmJson = """
                {
                  "intent": "GENERATE_SETLIST",
                  "slots": {
                """;

        // Act
        IntentValidationResult result = validationService.validate(llmJson);

        // Assert
        assertThat(result.isAccepted()).isFalse();
        assertThat(result.errors())
                .extracting(IntentValidationError::code)
                .containsExactly(IntentValidationErrorCode.MALFORMED_JSON);
        assertThatThrownBy(result::intent)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Rejected validation results");
    }

    @Test
    void validateRejectsUnknownTopLevelFields() {
        // Arrange
        String llmJson = """
                {
                  "intent": "GENERATE_SETLIST",
                  "slots": {},
                  "selectedSongs": []
                }
                """;

        // Act
        IntentValidationResult result = validationService.validate(llmJson);

        // Assert
        assertThat(result.isAccepted()).isFalse();
        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.code()).isEqualTo(IntentValidationErrorCode.UNSUPPORTED_FIELD);
            assertThat(error.path()).isEqualTo("$.selectedSongs");
        });
    }

    @Test
    void validateRejectsUnknownSlotFields() {
        // Arrange
        String llmJson = """
                {
                  "intent": "GENERATE_SETLIST",
                  "slots": {
                    "verseText": "Psalm 100",
                    "selectedSongs": ["Invented Song"]
                  }
                }
                """;

        // Act
        IntentValidationResult result = validationService.validate(llmJson);

        // Assert
        assertThat(result.isAccepted()).isFalse();
        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.code()).isEqualTo(IntentValidationErrorCode.UNSUPPORTED_FIELD);
            assertThat(error.path()).isEqualTo("$.slots.selectedSongs");
        });
    }

    @Test
    void validateRejectsOutOfBoundsCountsInsteadOfReplacingInvalidValues() {
        // Arrange
        String llmJson = """
                {
                  "intent": "GENERATE_SETLIST",
                  "slots": {
                    "counts": { "praise": 26, "worship": 5 }
                  }
                }
                """;

        // Act
        IntentValidationResult result = validationService.validate(llmJson);

        // Assert
        assertThat(result.isAccepted()).isFalse();
        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.code()).isEqualTo(IntentValidationErrorCode.OUT_OF_RANGE);
            assertThat(error.path()).isEqualTo("$.slots.counts.praise");
        });
    }

    @Test
    void validateRejectsUnsupportedEnumsWithActionableCode() {
        // Arrange
        String llmJson = """
                {
                  "intent": "GENERATE_SETLIST",
                  "slots": {
                    "serviceMoment": "offertory"
                  }
                }
                """;

        // Act
        IntentValidationResult result = validationService.validate(llmJson);

        // Assert
        assertThat(result.isAccepted()).isFalse();
        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.code()).isEqualTo(IntentValidationErrorCode.UNSUPPORTED_ENUM);
            assertThat(error.path()).isEqualTo("$.slots.serviceMoment");
        });
    }

    @Test
    void validateAcceptsClarifyRequestWithoutInvokingRecommendationShape() {
        // Arrange
        String llmJson = """
                {
                  "intent": "CLARIFY_REQUEST",
                  "reasonCode": "MISSING_REQUIRED_INFORMATION",
                  "clarificationQuestion": "Which scripture or theme should the setlist focus on?",
                  "missingSlots": ["verseText", "scriptureReferences"]
                }
                """;

        // Act
        IntentValidationResult result = validationService.validate(llmJson);

        // Assert
        assertThat(result.isAccepted()).isTrue();
        assertThat(result.intent()).isInstanceOfSatisfying(ClarifyRequestIntent.class, intent -> {
            assertThat(intent.reasonCode()).isEqualTo("MISSING_REQUIRED_INFORMATION");
            assertThat(intent.missingSlots()).containsExactly("verseText", "scriptureReferences");
        });
    }

    @Test
    void validateAcceptsUnsupportedRequestWithoutGenerateSlots() {
        // Arrange
        String llmJson = """
                {
                  "intent": "UNSUPPORTED_REQUEST",
                  "reasonCode": "UNSUPPORTED_ACTION",
                  "safeMessage": "I cannot approve songs or update catalog records."
                }
                """;

        // Act
        IntentValidationResult result = validationService.validate(llmJson);

        // Assert
        assertThat(result.isAccepted()).isTrue();
        assertThat(result.intent()).isInstanceOfSatisfying(UnsupportedRequestIntent.class, intent -> {
            assertThat(intent.reasonCode()).isEqualTo("UNSUPPORTED_ACTION");
            assertThat(intent.safeMessage()).contains("cannot approve songs");
        });
    }
}
