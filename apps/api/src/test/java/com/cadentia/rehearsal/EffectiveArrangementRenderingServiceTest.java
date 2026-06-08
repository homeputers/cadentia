package com.cadentia.rehearsal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cadentia.api.security.RbacAuthorities;
import com.cadentia.catalog.entity.Arrangement;
import com.cadentia.catalog.model.ArrangementSourceType;
import com.cadentia.catalog.model.KeyMode;
import com.cadentia.catalog.service.ArrangementRetrievalResult;
import com.cadentia.catalog.service.ArrangementTranspositionSource;
import com.cadentia.catalog.service.CatalogService;
import com.cadentia.catalog.transposition.MusicalKey;
import com.cadentia.rehearsal.RehearsalWorkflowModels.ArrangementOverrideRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.EffectiveArrangementRendering;
import com.cadentia.rehearsal.RehearsalWorkflowModels.EffectiveArrangementValueSource;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class EffectiveArrangementRenderingServiceTest {

    @Mock
    private RehearsalWorkflowRepository repository;

    private FakeCatalogService catalogService;

    private EffectiveArrangementRenderingService service;
    private UUID servicePlanId;
    private UUID arrangementId;
    private Arrangement arrangement;

    @BeforeEach
    void setUp() {
        catalogService = new FakeCatalogService();
        service = new EffectiveArrangementRenderingService(
                repository, catalogService, new RehearsalWorkflowAuthorizationPolicy());
        servicePlanId = UUID.randomUUID();
        arrangementId = UUID.randomUUID();
        arrangement = arrangement(arrangementId, "C", KeyMode.MAJOR, 72);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                "planner", "n/a", RbacAuthorities.ROLE_WORSHIP_LEADER));
        catalogService.add(arrangementId, Optional.empty(), result(arrangement, "C", KeyMode.MAJOR, 0, "[C]Alpha"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rendersSourceAndServiceOverrideValuesWithoutMutatingCanonicalArrangement() {
        // Arrange
        UUID blockId = UUID.randomUUID();
        UUID setlistItemId = UUID.randomUUID();
        ArrangementOverrideRecord override = overrideWithCapoAndNotes(servicePlanId, blockId, setlistItemId, arrangementId, "D",
                2, "Repeat bridge once", "V1 C V2 C B C", "pad under prayer");
        when(repository.listArrangementOverrides(servicePlanId)).thenReturn(List.of(override));
        catalogService.add(arrangementId, Optional.of(new MusicalKey("D", KeyMode.MAJOR)),
                result(arrangement, "D", KeyMode.MAJOR, 2, "[D]Alpha"));

        // Act
        EffectiveArrangementRendering rendering = service.renderEffectiveArrangement(
                servicePlanId, blockId, setlistItemId, arrangementId);

        // Assert
        assertThat(rendering.hasServiceOverride()).isTrue();
        assertThat(rendering.musicalKey().sourceValue()).isEqualTo("C");
        assertThat(rendering.musicalKey().overrideValue()).isEqualTo("D");
        assertThat(rendering.musicalKey().effectiveValue()).isEqualTo("D");
        assertThat(rendering.musicalKey().valueSource()).isEqualTo(EffectiveArrangementValueSource.SERVICE_OVERRIDE);
        assertThat(rendering.tempoBpm().sourceValue()).isEqualTo(72);
        assertThat(rendering.tempoBpm().effectiveValue()).isEqualTo(72);
        assertThat(rendering.capoFret().effectiveValue()).isEqualTo(2);
        assertThat(rendering.chartAnnotations().effectiveValue()).isEqualTo("Repeat bridge once");
        assertThat(rendering.sectionOrderNotes().effectiveValue()).isEqualTo("V1 C V2 C B C");
        assertThat(rendering.transitionCues().effectiveValue()).isEqualTo("pad under prayer");
        assertThat(rendering.renderedLyricsContent()).isEqualTo("[D]Alpha");
        assertThat(rendering.provenance().arrangementOverrideId()).isEqualTo(override.arrangementOverrideId());
        assertThat(rendering.provenance().auditReference()).contains("service-arrangement-override");
        assertThat(arrangement.musicalKey()).isEqualTo("C");
        assertThat(arrangement.tempoBpm()).isEqualTo(72);
    }

    @Test
    void archiveRemovesOverrideFromEffectiveRenderingAndFallsBackToCatalogValues() {
        // Arrange
        UUID archivedOverrideId = UUID.randomUUID();
        when(repository.listArrangementOverrides(servicePlanId)).thenReturn(List.of());

        // Act
        EffectiveArrangementRendering rendering = service.renderEffectiveArrangement(
                servicePlanId, null, null, arrangementId);

        // Assert
        assertThat(rendering.hasServiceOverride()).isFalse();
        assertThat(rendering.musicalKey().effectiveValue()).isEqualTo("C");
        assertThat(rendering.musicalKey().valueSource()).isEqualTo(EffectiveArrangementValueSource.CATALOG);
        assertThat(rendering.provenance().arrangementOverrideId()).isNull();
        verify(repository, never()).archiveArrangementOverride(servicePlanId, archivedOverrideId, "planner");
    }

    @Test
    void selectsMostSpecificOverrideDeterministicallyForConcurrentUpdates() {
        // Arrange
        UUID blockId = UUID.randomUUID();
        UUID setlistItemId = UUID.randomUUID();
        ArrangementOverrideRecord serviceWide = override(servicePlanId, null, null, arrangementId, "D");
        ArrangementOverrideRecord blockSpecific = override(servicePlanId, blockId, null, arrangementId, "E");
        ArrangementOverrideRecord itemSpecific = override(servicePlanId, blockId, setlistItemId, arrangementId, "F");
        when(repository.listArrangementOverrides(servicePlanId)).thenReturn(List.of(serviceWide, itemSpecific, blockSpecific));
        catalogService.add(arrangementId, Optional.of(new MusicalKey("F", KeyMode.MAJOR)),
                result(arrangement, "F", KeyMode.MAJOR, 5, "[F]Alpha"));

        // Act
        EffectiveArrangementRendering rendering = service.renderEffectiveArrangement(
                servicePlanId, blockId, setlistItemId, arrangementId);

        // Assert
        assertThat(rendering.provenance().arrangementOverrideId()).isEqualTo(itemSpecific.arrangementOverrideId());
        assertThat(rendering.musicalKey().effectiveValue()).isEqualTo("F");
    }

    @Test
    void doesNotLeakOverridesIntoUnrelatedServicesOrSetlistItems() {
        // Arrange
        UUID requestedSetlistItemId = UUID.randomUUID();
        UUID otherServiceId = UUID.randomUUID();
        ArrangementOverrideRecord otherService = override(otherServiceId, null, null, arrangementId, "D");
        ArrangementOverrideRecord otherItem = override(servicePlanId, null, UUID.randomUUID(), arrangementId, "E");
        when(repository.listArrangementOverrides(servicePlanId)).thenReturn(List.of(otherService, otherItem));

        // Act
        EffectiveArrangementRendering rendering = service.renderEffectiveArrangement(
                servicePlanId, null, requestedSetlistItemId, arrangementId);

        // Assert
        assertThat(rendering.hasServiceOverride()).isFalse();
        assertThat(rendering.musicalKey().effectiveValue()).isEqualTo("C");
        assertThat(rendering.renderedTranspositionInterval()).isZero();
    }

    private Arrangement arrangement(UUID id, String key, KeyMode mode, int tempo) {
        return new Arrangement(
                id,
                UUID.randomUUID(),
                "Canonical Arrangement",
                "canonical-arrangement",
                ArrangementSourceType.CUSTOM,
                "en",
                key,
                mode,
                tempo,
                "4/4",
                300,
                3,
                2,
                true,
                true,
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-01T00:00:00Z"));
    }

    private ArrangementRetrievalResult result(
            Arrangement arrangement, String requestedKey, KeyMode mode, int interval, String lyricsContent) {
        return new ArrangementRetrievalResult(
                arrangement,
                null,
                new MusicalKey(arrangement.musicalKey(), arrangement.keyMode()),
                new MusicalKey(requestedKey, mode),
                interval,
                interval != 0,
                interval == 0 ? ArrangementTranspositionSource.NONE : ArrangementTranspositionSource.CHORD_SHEET_CONTENT,
                lyricsContent,
                null);
    }

    private ArrangementOverrideRecord override(
            UUID servicePlanId, UUID blockId, UUID setlistItemId, UUID sourceArrangementId, String effectiveKey) {
        return overrideWithCapoAndNotes(servicePlanId, blockId, setlistItemId, sourceArrangementId, effectiveKey,
                null, null, null, null);
    }

    private ArrangementOverrideRecord overrideWithCapoAndNotes(
            UUID servicePlanId,
            UUID blockId,
            UUID setlistItemId,
            UUID sourceArrangementId,
            String effectiveKey,
            Integer capoFret,
            String chartAnnotations,
            String sectionOrderNotes,
            String transitionCues) {
        return new ArrangementOverrideRecord(UUID.randomUUID(), servicePlanId, blockId, setlistItemId, sourceArrangementId,
                "arr-v1", effectiveKey, "MAJOR", null, null, null, null, null, null, capoFret, null, chartAnnotations,
                sectionOrderNotes, transitionCues, null, null, "service adaptation",
                "approved source arrangement arr-v1", "planner", "planner");
    }
    private static final class FakeCatalogService extends CatalogService {
        private final Map<String, ArrangementRetrievalResult> arrangements = new LinkedHashMap<>();

        private FakeCatalogService() {
            super(null);
        }

        private void add(UUID arrangementId, Optional<MusicalKey> requestedTargetKey, ArrangementRetrievalResult result) {
            arrangements.put(key(arrangementId, requestedTargetKey), result);
        }

        @Override
        public Optional<ArrangementRetrievalResult> retrieveArrangement(
                UUID arrangementId, Optional<MusicalKey> requestedTargetKey) {
            return Optional.ofNullable(arrangements.get(key(arrangementId, requestedTargetKey)));
        }

        private String key(UUID arrangementId, Optional<MusicalKey> requestedTargetKey) {
            return arrangementId + ":" + requestedTargetKey.map(MusicalKey::toString).orElse("base");
        }
    }
}
