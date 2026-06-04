package com.cadentia.reng.scoring;

import com.cadentia.generated.model.GenerateSetlistRequest;
import com.cadentia.generated.model.KeyPolicy;
import com.cadentia.generated.model.SetlistCounts;
import com.cadentia.generated.model.TempoPolicy;
import com.cadentia.runtime.InstanceConfiguration;
import com.cadentia.runtime.InstanceConfigurationProvider;
import com.cadentia.runtime.StaticInstanceConfigurationProvider;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ScoringRequestFactory {

    private static final String DEFAULT_LANGUAGE = "en";

    private final InstanceConfigurationProvider configurationProvider;

    public ScoringRequestFactory() {
        this(new StaticInstanceConfigurationProvider(InstanceConfiguration.localDevelopment(
                "local-development",
                "local",
                "cadentia-local-assets",
                "local-development",
                "env:CADENTIA_LOCAL_ASSET_KEY_REF",
                "cadentia:local:development",
                "local.development",
                List.of("local.development.audit-events", "local.development.recommendation-events"))));
    }

    public ScoringRequestFactory(InstanceConfigurationProvider configurationProvider) {
        this.configurationProvider = configurationProvider;
    }

    public ScoringRequest fromValidatedRequest(GenerateSetlistRequest request) {
        InstanceConfiguration.RecommendationPolicy defaults = configurationProvider.current().recommendationPolicy();
        SetlistCounts counts = request.getCounts();
        KeyPolicy keyPolicy = request.getKeyPolicy();
        TempoPolicy tempoPolicy = request.getTempoPolicy();

        boolean countsDefaulted = counts == null;
        boolean keyPolicyDefaulted = keyPolicy == null;
        boolean tempoPolicyDefaulted = tempoPolicy == null;
        boolean languageDefaulted = request.getLanguage() == null || request.getLanguage().isBlank();

        return new ScoringRequest(
                request.getVerseText(),
                request.getThemeHints() == null ? List.of() : request.getThemeHints(),
                countsDefaulted ? defaults.praiseCount() : counts.getPraise(),
                countsDefaulted ? defaults.worshipCount() : counts.getWorship(),
                keyPolicyDefaulted
                        ? new ScoringRequest.KeyPolicy(
                                defaults.keyPolicy().preferSameKey(),
                                defaults.keyPolicy().allowRelativeMajorMinor(),
                                defaults.keyPolicy().maxKeyCenters())
                        : new ScoringRequest.KeyPolicy(
                                Boolean.TRUE.equals(keyPolicy.getPreferSameKey()),
                                Boolean.TRUE.equals(keyPolicy.getAllowRelativeMajorMinor()),
                                keyPolicy.getMaxKeyCenters()),
                tempoPolicyDefaulted
                        ? new ScoringRequest.TempoPolicy(defaults.tempoPolicy().maxJumpBpm())
                        : new ScoringRequest.TempoPolicy(tempoPolicy.getMaxJumpBpm()),
                request.getEnergyArc() == null ? null : request.getEnergyArc().getValue(),
                languageDefaulted ? DEFAULT_LANGUAGE : request.getLanguage(),
                request.getExcludedSongs() == null ? List.of() : request.getExcludedSongs(),
                Boolean.TRUE.equals(request.getIncludeAdminDiagnostics())
                        && request.getExplanationAudience() == GenerateSetlistRequest.ExplanationAudienceEnum.ADMIN,
                new ScoringRequest.DefaultsApplied(
                        countsDefaulted,
                        keyPolicyDefaulted,
                        tempoPolicyDefaulted,
                        languageDefaulted));
    }
}
