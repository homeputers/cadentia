package com.cadentia.rehearsal;

import com.cadentia.catalog.entity.Arrangement;
import com.cadentia.catalog.model.KeyMode;
import com.cadentia.catalog.service.ArrangementRetrievalResult;
import com.cadentia.catalog.service.CatalogService;
import com.cadentia.catalog.transposition.MusicalKey;
import com.cadentia.rehearsal.RehearsalWorkflowModels.ArrangementOverrideRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.EffectiveArrangementProvenance;
import com.cadentia.rehearsal.RehearsalWorkflowModels.EffectiveArrangementRendering;
import com.cadentia.rehearsal.RehearsalWorkflowModels.EffectiveArrangementValue;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EffectiveArrangementRenderingService {

    private final RehearsalWorkflowRepository repository;
    private final CatalogService catalogService;
    private final RehearsalWorkflowAuthorizationPolicy authorizationPolicy;

    public EffectiveArrangementRenderingService(
            RehearsalWorkflowRepository repository,
            CatalogService catalogService,
            RehearsalWorkflowAuthorizationPolicy authorizationPolicy) {
        this.repository = repository;
        this.catalogService = catalogService;
        this.authorizationPolicy = authorizationPolicy;
    }

    public EffectiveArrangementRendering renderEffectiveArrangement(
            UUID servicePlanId,
            UUID servicePlanBlockId,
            UUID setlistVersionItemId,
            UUID arrangementId) {
        authorizationPolicy.requireWorkflowRead();
        if (servicePlanId == null) {
            throw new IllegalArgumentException("servicePlanId is required");
        }
        if (arrangementId == null) {
            throw new IllegalArgumentException("arrangementId is required");
        }
        Optional<ArrangementOverrideRecord> override = selectOverride(
                servicePlanId, servicePlanBlockId, setlistVersionItemId, arrangementId);
        ArrangementRetrievalResult baseResult = catalogService.retrieveArrangement(arrangementId, Optional.empty())
                .orElseThrow(() -> new RehearsalWorkflowException("Source arrangement not found: " + arrangementId));
        Arrangement arrangement = baseResult.arrangement();
        Optional<MusicalKey> requestedKey = override
                .flatMap(overrideRecord -> requestedKey(arrangement, overrideRecord));
        ArrangementRetrievalResult renderedResult = requestedKey.isPresent()
                ? catalogService.retrieveArrangement(arrangementId, requestedKey)
                        .orElseThrow(() -> new RehearsalWorkflowException("Source arrangement not found: " + arrangementId))
                : baseResult;
        return buildRendering(servicePlanId, servicePlanBlockId, setlistVersionItemId, renderedResult, override.orElse(null));
    }

    private Optional<ArrangementOverrideRecord> selectOverride(
            UUID servicePlanId,
            UUID servicePlanBlockId,
            UUID setlistVersionItemId,
            UUID arrangementId) {
        return repository.listArrangementOverrides(servicePlanId).stream()
                .filter(override -> servicePlanId.equals(override.servicePlanId()))
                .filter(override -> arrangementId.equals(override.sourceArrangementId()))
                .filter(override -> matchesScope(override.servicePlanBlockId(), servicePlanBlockId))
                .filter(override -> matchesScope(override.setlistVersionItemId(), setlistVersionItemId))
                .max(Comparator
                        .comparingInt((ArrangementOverrideRecord override) -> specificity(override, servicePlanBlockId, setlistVersionItemId))
                        .thenComparing(override -> override.arrangementOverrideId().toString()));
    }

    private boolean matchesScope(UUID overrideScopeId, UUID requestedScopeId) {
        return overrideScopeId == null || overrideScopeId.equals(requestedScopeId);
    }

    private int specificity(ArrangementOverrideRecord override, UUID servicePlanBlockId, UUID setlistVersionItemId) {
        int score = 0;
        if (override.servicePlanBlockId() != null && override.servicePlanBlockId().equals(servicePlanBlockId)) {
            score += 2;
        }
        if (override.setlistVersionItemId() != null && override.setlistVersionItemId().equals(setlistVersionItemId)) {
            score += 4;
        }
        return score;
    }

    private Optional<MusicalKey> requestedKey(Arrangement arrangement, ArrangementOverrideRecord override) {
        if (blank(override.effectiveKey())) {
            return Optional.empty();
        }
        KeyMode keyMode = blank(override.effectiveMode())
                ? arrangement.keyMode()
                : KeyMode.valueOf(override.effectiveMode());
        return Optional.of(new MusicalKey(override.effectiveKey(), keyMode));
    }

    private EffectiveArrangementRendering buildRendering(
            UUID servicePlanId,
            UUID servicePlanBlockId,
            UUID setlistVersionItemId,
            ArrangementRetrievalResult renderedResult,
            ArrangementOverrideRecord override) {
        Arrangement arrangement = renderedResult.arrangement();
        EffectiveArrangementProvenance provenance = new EffectiveArrangementProvenance(
                arrangement.id(),
                override == null ? null : override.sourceArrangementVersionRef(),
                override == null ? null : override.arrangementOverrideId(),
                override == null ? null : override.provenanceNote(),
                override == null ? null : override.rationale(),
                override == null ? null : override.createdBy(),
                override == null ? null : override.updatedBy(),
                override == null ? "catalog:arrangement:" + arrangement.id()
                        : "service-arrangement-override:" + override.arrangementOverrideId());
        return new EffectiveArrangementRendering(
                servicePlanId,
                servicePlanBlockId,
                setlistVersionItemId,
                arrangement.id(),
                arrangement.name(),
                EffectiveArrangementValue.from(arrangement.musicalKey(), override == null ? null : override.effectiveKey()),
                EffectiveArrangementValue.from(arrangement.keyMode().name(), override == null ? null : override.effectiveMode()),
                EffectiveArrangementValue.from(arrangement.tempoBpm(), override == null ? null : override.effectiveTempoBpm()),
                EffectiveArrangementValue.from(arrangement.timeSignature(), override == null ? null : override.effectiveTimeSignature()),
                EffectiveArrangementValue.from(arrangement.durationSeconds(), override == null ? null : override.effectiveDurationSeconds()),
                EffectiveArrangementValue.from(arrangement.energyLevel(), override == null ? null : override.effectiveEnergyLevel()),
                EffectiveArrangementValue.from(arrangement.difficultyLevel(), override == null ? null : override.effectiveDifficultyLevel()),
                EffectiveArrangementValue.from(null, override == null ? null : override.effectiveNotes()),
                EffectiveArrangementValue.from(null, override == null ? null : override.capoFret()),
                EffectiveArrangementValue.from(renderedResult.transpositionInterval(),
                        override == null ? null : override.transpositionSemitones()),
                EffectiveArrangementValue.from(null, override == null ? null : override.chartAnnotations()),
                EffectiveArrangementValue.from(null, override == null ? null : override.sectionOrderNotes()),
                EffectiveArrangementValue.from(null, override == null ? null : override.transitionCues()),
                EffectiveArrangementValue.from(null, override == null ? null : override.instrumentationNotes()),
                EffectiveArrangementValue.from(null, override == null ? null : override.assetSelectionNotes()),
                renderedResult.transpositionInterval(),
                renderedResult.transpositionSource().name(),
                renderedResult.lyricsContent(),
                renderedResult.chordMapJson(),
                provenance);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
