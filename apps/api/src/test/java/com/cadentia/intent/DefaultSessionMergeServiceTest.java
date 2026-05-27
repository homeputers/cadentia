package com.cadentia.intent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultSessionMergeServiceTest {

    private final DefaultSessionMergeService service = new DefaultSessionMergeService();

    @Test
    void mergePrioritizesCurrentTurnUserEditsOverExistingValues() {
        // Arrange
        GenerateSetlistSlots baseline = slots("Psalm 23", List.of("comfort"), "English");
        SessionSlotUpdate update = new SessionSlotUpdate(slots("Psalm 100", List.of("praise"), "Spanish"), SlotValueSource.USER_EDIT, false);

        // Act
        SessionMergeResult result = service.merge(baseline, update);

        // Assert
        assertThat(result.mergedSlots().verseText()).isEqualTo("Psalm 100");
        assertThat(result.mergedSlots().themeHints()).containsExactly("praise");
        assertThat(result.slotSources()).containsEntry("verseText", SlotValueSource.USER_EDIT);
        assertThat(result.conflicts()).isEmpty();
    }

    @Test
    void mergeLetsMenuOverrideFreeTextInferredValuesByDefault() {
        // Arrange
        GenerateSetlistSlots baseline = slots("Psalm 100", List.of("joy"), "English");
        SessionSlotUpdate freeTextUpdate = new SessionSlotUpdate(slots("Psalm 150", List.of("celebration"), "English"), SlotValueSource.FREE_TEXT, false);
        SessionSlotUpdate menuUpdate = new SessionSlotUpdate(slots("Psalm 95", List.of("adoration"), "English"), SlotValueSource.MENU, false);

        // Act
        SessionMergeResult intermediate = service.merge(baseline, freeTextUpdate);
        SessionMergeResult merged = service.merge(intermediate.mergedSlots(), menuUpdate);

        // Assert
        assertThat(merged.mergedSlots().verseText()).isEqualTo("Psalm 95");
        assertThat(merged.slotSources()).containsEntry("verseText", SlotValueSource.MENU);
    }

    @Test
    void mergeUsesFreeTextOnlyToFillMissingSlotsWhenConflictsExist() {
        // Arrange
        GenerateSetlistSlots baseline = slots("Psalm 100", List.of(), "English");
        SessionSlotUpdate update = new SessionSlotUpdate(slots("Psalm 150", List.of("thanksgiving"), ""), SlotValueSource.FREE_TEXT, false);

        // Act
        SessionMergeResult result = service.merge(baseline, update);

        // Assert
        assertThat(result.mergedSlots().verseText()).isEqualTo("Psalm 100");
        assertThat(result.mergedSlots().themeHints()).containsExactly("thanksgiving");
        assertThat(result.conflicts()).extracting(SessionMergeConflict::slotPath).contains("verseText");
    }

    @Test
    void mergeAppliesDefaultsOnlyAfterUserProvidedValuesAreExhausted() {
        // Arrange
        GenerateSetlistSlots baseline = new GenerateSetlistSlots("", List.of(), List.of(), null, null, null, null, null, List.of(), null);
        SessionSlotUpdate update = new SessionSlotUpdate(
                new GenerateSetlistSlots("", List.of(), List.of(), null, null, null, null, null, List.of(), null),
                SlotValueSource.FREE_TEXT,
                false);

        // Act
        SessionMergeResult result = service.merge(baseline, update);

        // Assert
        assertThat(result.mergedSlots().counts()).isEqualTo(new Counts(10, 5));
        assertThat(result.mergedSlots().keyPolicy()).isEqualTo(new IntentKeyPolicy(true, true, 2));
        assertThat(result.mergedSlots().tempoPolicy()).isEqualTo(new IntentTempoPolicy(12));
        assertThat(result.slotSources())
                .containsEntry("counts", SlotValueSource.DEFAULT)
                .containsEntry("keyPolicy", SlotValueSource.DEFAULT)
                .containsEntry("tempoPolicy", SlotValueSource.DEFAULT);
    }

    private static GenerateSetlistSlots slots(String verseText, List<String> themeHints, String language) {
        return new GenerateSetlistSlots(
                verseText,
                List.of(),
                themeHints,
                new Counts(10, 5),
                new IntentKeyPolicy(true, true, 2),
                new IntentTempoPolicy(12),
                language,
                null,
                List.of(),
                null);
    }
}
