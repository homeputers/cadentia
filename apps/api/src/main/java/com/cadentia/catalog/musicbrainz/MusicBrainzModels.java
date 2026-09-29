package com.cadentia.catalog.musicbrainz;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MusicBrainzModels {
    private MusicBrainzModels() {
    }

    public enum EntityType {
        ARTIST,
        RELEASE_GROUP,
        RELEASE,
        RECORDING
    }

    public enum ProposalStatus {
        PROPOSED,
        ACCEPTED,
        REJECTED,
        SUPERSEDED
    }

    public enum Field {
        CANONICAL_TITLE,
        ORIGINAL_ARTIST_DISPLAY,
        ARRANGEMENT_NAME,
        MUSICBRAINZ_LINKAGE
    }

    public record SearchRequest(
            String title,
            String artist,
            String album,
            UUID recordingMbid,
            int limit) {
    }

    public record Artist(
            UUID mbid,
            String name,
            String sortName,
            String type,
            String country) {
    }

    public record ReleaseGroup(
            UUID mbid,
            String title,
            String primaryType,
            String firstReleaseDate) {
    }

    public record Release(
            UUID mbid,
            String title,
            String status,
            String date,
            String country) {
    }

    public record Recording(
            UUID mbid,
            String title,
            Integer lengthMilliseconds,
            Integer discNumber,
            Integer trackNumber,
            List<String> isrcs,
            String sourceUri) {
    }

    public record Candidate(
            UUID proposalId,
            ProposalStatus status,
            BigDecimal score,
            Artist artist,
            ReleaseGroup releaseGroup,
            Release release,
            Recording recording,
            List<String> warnings,
            List<String> sourceReferences) {
    }

    public record Proposal(
            UUID id,
            UUID songId,
            UUID arrangementId,
            UUID artistMbid,
            UUID releaseGroupMbid,
            UUID releaseMbid,
            UUID recordingMbid,
            BigDecimal score,
            String matchMethod,
            ProposalStatus status,
            String sourceUri,
            String sourcePayload,
            String createdBy,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record EntitySnapshot(
            EntityType entityType,
            UUID mbid,
            String displayName,
            String normalizedKey,
            String sourceUri,
            String metadataJson,
            Instant fetchedAt,
            String responseHash) {
    }

    public record Link(
            UUID id,
            UUID songId,
            UUID arrangementId,
            UUID proposalId,
            ProposalStatus status,
            BigDecimal score,
            String matchMethod,
            UUID artistMbid,
            UUID releaseGroupMbid,
            UUID releaseMbid,
            UUID recordingMbid,
            String selectedFields,
            String reviewer,
            String rationale,
            Instant acceptedAt) {
    }

    public record SearchResult(boolean enabled, boolean cacheHit, List<Candidate> proposals) {
    }

    public record State(boolean enabled, UUID acceptedProposalId, List<Candidate> proposals) {
    }

    public record FieldDiff(Field field, String current, String proposed, boolean conflict) {
    }

    public record Preview(
            UUID proposalId,
            UUID arrangementId,
            List<Field> selectedFields,
            List<FieldDiff> fields,
            List<String> warnings,
            String etag) {
    }
}
