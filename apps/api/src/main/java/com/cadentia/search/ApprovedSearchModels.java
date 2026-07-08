package com.cadentia.search;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ApprovedSearchModels {

    private ApprovedSearchModels() {
    }

    public enum SuggestionType { TITLE, TAG, SCRIPTURE, CONTRIBUTOR, ARRANGEMENT }

    public enum SearchVisibilityPolicy { PUBLIC, RESTRICTED }

    public enum SearchProjectionEventReason {
        APPROVAL_STATE_CHANGED,
        ACTIVE_STATUS_CHANGED,
        LICENSING_CHANGED,
        PACKAGE_VISIBILITY_CHANGED,
        INSTANCE_VISIBILITY_CHANGED,
        CATALOG_GOVERNANCE_CHANGED,
        SOURCE_METADATA_CHANGED,
        REBUILD_REQUESTED
    }

    public record SearchActor(String actorId, UUID instanceId, Set<String> roles, Set<String> governanceBypassCodes, boolean authenticated) {
        public SearchActor {
            roles = roles == null ? Set.of() : Set.copyOf(roles);
            governanceBypassCodes = governanceBypassCodes == null ? Set.of() : Set.copyOf(governanceBypassCodes);
        }
    }

    public record SearchEligibilityDecision(boolean eligible, String denialReason) {
        public static SearchEligibilityDecision allow() {
            return new SearchEligibilityDecision(true, null);
        }

        public static SearchEligibilityDecision deny(String reason) {
            return new SearchEligibilityDecision(false, reason);
        }
    }

    public record SearchProjectionInvalidation(UUID sourceEntityId, SearchProjectionEventReason reason, Instant dueAt) {
    }

    public record ApprovedSearchDocument(
            UUID songId,
            UUID arrangementId,
            UUID instanceId,
            String title,
            List<String> alternateTitles,
            List<NormalizedScriptureReference> scriptureReferences,
            List<TagFacet> tags,
            List<String> contributors,
            String musicalKey,
            Integer bpm,
            String arrangementLabel,
            List<String> arrangementMetadata,
            List<String> lyricsMetadata,
            boolean active,
            boolean approved,
            boolean visible,
            boolean licensed,
            boolean packageVisible,
            SearchVisibilityPolicy visibilityPolicy,
            Set<String> authorizedRoles,
            Set<String> governanceRestrictionCodes,
            Double semanticSimilarity,
            Double familiaritySignal,
            Double popularitySignal,
            Instant catalogUpdatedAt,
            boolean starterPackagePreferred) {
        public ApprovedSearchDocument {
            alternateTitles = alternateTitles == null ? List.of() : List.copyOf(alternateTitles);
            scriptureReferences = scriptureReferences == null ? List.of() : List.copyOf(scriptureReferences);
            tags = tags == null ? List.of() : List.copyOf(tags);
            contributors = contributors == null ? List.of() : List.copyOf(contributors);
            arrangementMetadata = arrangementMetadata == null ? List.of() : List.copyOf(arrangementMetadata);
            lyricsMetadata = lyricsMetadata == null ? List.of() : List.copyOf(lyricsMetadata);
            packageVisible = packageVisible || (visibilityPolicy == null && authorizedRoles == null && governanceRestrictionCodes == null);
            visibilityPolicy = visibilityPolicy == null ? SearchVisibilityPolicy.PUBLIC : visibilityPolicy;
            authorizedRoles = authorizedRoles == null ? Set.of() : Set.copyOf(authorizedRoles);
            governanceRestrictionCodes = governanceRestrictionCodes == null ? Set.of() : Set.copyOf(governanceRestrictionCodes);
            semanticSimilarity = clampNullable(semanticSimilarity);
            familiaritySignal = clampNullable(familiaritySignal);
            popularitySignal = clampNullable(popularitySignal);
        }

        public ApprovedSearchDocument(
                UUID songId,
                UUID arrangementId,
                UUID instanceId,
                String title,
                List<String> alternateTitles,
                List<NormalizedScriptureReference> scriptureReferences,
                List<TagFacet> tags,
                List<String> contributors,
                String musicalKey,
                Integer bpm,
                String arrangementLabel,
                List<String> arrangementMetadata,
                List<String> lyricsMetadata,
                boolean active,
                boolean approved,
                boolean visible,
                boolean licensed,
                boolean packageVisible,
                SearchVisibilityPolicy visibilityPolicy,
                Set<String> authorizedRoles,
                Set<String> governanceRestrictionCodes) {
            this(songId, arrangementId, instanceId, title, alternateTitles, scriptureReferences, tags, contributors, musicalKey, bpm,
                    arrangementLabel, arrangementMetadata, lyricsMetadata, active, approved, visible, licensed, packageVisible,
                    visibilityPolicy, authorizedRoles, governanceRestrictionCodes, null, null, null, null, false);
        }

        public ApprovedSearchDocument(
                UUID songId,
                UUID arrangementId,
                UUID instanceId,
                String title,
                List<String> alternateTitles,
                List<NormalizedScriptureReference> scriptureReferences,
                List<TagFacet> tags,
                List<String> contributors,
                String musicalKey,
                Integer bpm,
                String arrangementLabel,
                List<String> arrangementMetadata,
                List<String> lyricsMetadata,
                boolean active,
                boolean approved,
                boolean visible,
                boolean licensed) {
            this(songId, arrangementId, instanceId, title, alternateTitles, scriptureReferences, tags, contributors, musicalKey, bpm,
                    arrangementLabel, arrangementMetadata, lyricsMetadata, active, approved, visible, licensed, true,
                    SearchVisibilityPolicy.PUBLIC, Set.of(), Set.of(), null, null, null, null, false);
        }

        private static Double clampNullable(Double value) {
            if (value == null || value.isNaN()) {
                return null;
            }
            return Math.max(0.0d, Math.min(1.0d, value));
        }

        boolean instanceVisible() {
            return visible;
        }
    }

    public record TagFacet(String code, String label) {
    }

    public record SearchQuery(
            UUID instanceId,
            String text,
            String title,
            String scripture,
            String tag,
            String contributor,
            String musicalKey,
            Integer minBpm,
            Integer maxBpm,
            String arrangement) {
    }

    public record SearchRankingProfile(
            String version,
            boolean familiarityEnabled,
            boolean recencyPreferenceEnabled,
            boolean packagePreferenceEnabled,
            Map<String, Double> weights) {
        public SearchRankingProfile {
            version = version == null || version.isBlank() ? "search-ranking-v1" : version;
            weights = weights == null ? Map.of() : Map.copyOf(weights);
        }
    }

    public record RankingFactor(String code, double contribution, String visibility) {
    }

    public record SearchResult(
            UUID songId,
            UUID arrangementId,
            String title,
            String arrangementLabel,
            double score,
            List<RankingFactor> rankingFactors,
            String rankingVersion) {
        public SearchResult(UUID songId, UUID arrangementId, String title, String arrangementLabel, double score) {
            this(songId, arrangementId, title, arrangementLabel, score, List.of(), "legacy");
        }

        public SearchResult {
            rankingFactors = rankingFactors == null ? List.of() : List.copyOf(rankingFactors);
        }
    }

    public enum SemanticDiscoveryMode { ENABLED, DISABLED }

    public record SemanticIndexDocument(
            UUID songId,
            UUID arrangementId,
            UUID instanceId,
            boolean packageVisible,
            boolean approved,
            boolean active,
            boolean visible,
            boolean licensed,
            List<String> allowedFields,
            String sourceHash) {
        public SemanticIndexDocument {
            allowedFields = allowedFields == null ? List.of() : List.copyOf(allowedFields);
        }
    }

    public record SemanticEmbeddingRecord(
            UUID embeddingId,
            UUID songId,
            UUID arrangementId,
            UUID instanceId,
            boolean packageVisible,
            String providerIdentifier,
            String modelVersion,
            String sourceHash,
            Instant generatedAt,
            int projectionVersion,
            boolean approved,
            boolean active,
            boolean visible,
            boolean licensed,
            double[] vector) {
        public SemanticEmbeddingRecord {
            vector = vector == null ? new double[0] : vector.clone();
        }

        @Override
        public double[] vector() {
            return vector.clone();
        }
    }

    public record SearchExplanation(UUID songId, UUID arrangementId, List<String> factors) {
    }

    public record SearchDiagnostics(
            String rankingVersion,
            int eligibleCandidateCount,
            int returnedResultCount,
            Map<String, Long> factorCounts,
            boolean queryTextRedacted) {
    }

    public record AutocompleteSuggestion(SuggestionType type, String value, String matchedText, UUID songId, UUID arrangementId) {
    }
}
