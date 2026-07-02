package com.cadentia.search;

import java.util.List;
import java.util.UUID;

public final class ApprovedSearchModels {

    private ApprovedSearchModels() {
    }

    public enum SuggestionType { TITLE, TAG, SCRIPTURE, CONTRIBUTOR, ARRANGEMENT }

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
            boolean licensed) {
        public ApprovedSearchDocument {
            alternateTitles = alternateTitles == null ? List.of() : List.copyOf(alternateTitles);
            scriptureReferences = scriptureReferences == null ? List.of() : List.copyOf(scriptureReferences);
            tags = tags == null ? List.of() : List.copyOf(tags);
            contributors = contributors == null ? List.of() : List.copyOf(contributors);
            arrangementMetadata = arrangementMetadata == null ? List.of() : List.copyOf(arrangementMetadata);
            lyricsMetadata = lyricsMetadata == null ? List.of() : List.copyOf(lyricsMetadata);
        }

        boolean safeFor(UUID requestedInstanceId) {
            return active && approved && visible && licensed
                    && (instanceId == null || requestedInstanceId == null || instanceId.equals(requestedInstanceId));
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

    public record SearchResult(UUID songId, UUID arrangementId, String title, String arrangementLabel, double score) {
    }

    public record AutocompleteSuggestion(SuggestionType type, String value, String matchedText, UUID songId, UUID arrangementId) {
    }
}
