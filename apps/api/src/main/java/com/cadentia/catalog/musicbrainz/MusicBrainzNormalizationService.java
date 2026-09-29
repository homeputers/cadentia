package com.cadentia.catalog.musicbrainz;

import com.cadentia.catalog.entity.Arrangement;
import com.cadentia.catalog.entity.Song;
import com.cadentia.catalog.model.CreateImportBatchCommand;
import com.cadentia.catalog.model.CreateProvenanceRecordCommand;
import com.cadentia.catalog.model.ImportBatchStatus;
import com.cadentia.catalog.model.ImportMethod;
import com.cadentia.catalog.model.LicenseType;
import com.cadentia.catalog.model.UpdateArrangementCommand;
import com.cadentia.catalog.model.UpdateSongCommand;
import com.cadentia.catalog.musicbrainz.MusicBrainzModels.Artist;
import com.cadentia.catalog.musicbrainz.MusicBrainzModels.Candidate;
import com.cadentia.catalog.musicbrainz.MusicBrainzModels.EntitySnapshot;
import com.cadentia.catalog.musicbrainz.MusicBrainzModels.EntityType;
import com.cadentia.catalog.musicbrainz.MusicBrainzModels.Field;
import com.cadentia.catalog.musicbrainz.MusicBrainzModels.FieldDiff;
import com.cadentia.catalog.musicbrainz.MusicBrainzModels.Preview;
import com.cadentia.catalog.musicbrainz.MusicBrainzModels.Proposal;
import com.cadentia.catalog.musicbrainz.MusicBrainzModels.ProposalStatus;
import com.cadentia.catalog.repository.SongRepository;
import com.cadentia.scraperadmin.AdminAuditEvent;
import com.cadentia.scraperadmin.TitleNormalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MusicBrainzNormalizationService {
    private static final String SOURCE_SYSTEM = "MUSICBRAINZ";
    private static final String SOURCE_LABEL = "MusicBrainz metadata";

    private final SongRepository songRepository;
    private final MusicBrainzRepository musicBrainzRepository;
    private final MusicBrainzApiClient client;
    private final TitleNormalizer titleNormalizer;
    private final ObjectMapper objectMapper;

    public MusicBrainzNormalizationService(
            SongRepository songRepository,
            MusicBrainzRepository musicBrainzRepository,
            MusicBrainzApiClient client,
            ObjectMapper objectMapper) {
        this.songRepository = songRepository;
        this.musicBrainzRepository = musicBrainzRepository;
        this.client = client;
        this.titleNormalizer = new TitleNormalizer();
        this.objectMapper = objectMapper;
    }

    public MusicBrainzModels.State getState(UUID songId) {
        requireSong(songId);
        UUID acceptedProposalId = musicBrainzRepository.findAcceptedLink(songId).map(MusicBrainzModels.Link::proposalId).orElse(null);
        return new MusicBrainzModels.State(client.enabled(), acceptedProposalId,
                musicBrainzRepository.findProposals(songId).stream().map(this::toCandidate).toList());
    }

    public MusicBrainzModels.SearchResult search(UUID songId, MusicBrainzModels.SearchRequest request, String actor) {
        Song song = requireSong(songId);
        validateSearch(request);
        MusicBrainzApiClient.SearchResponse response = client.search(request);
        List<Candidate> candidates = response.candidates().stream()
                .map(remote -> stageProposal(song, remote, actor))
                .sorted((left, right) -> right.score().compareTo(left.score()))
                .map(this::toCandidate)
                .toList();
        return new MusicBrainzModels.SearchResult(response.enabled(), response.cacheHit(), candidates);
    }

    public Preview preview(
            UUID songId,
            UUID proposalId,
            UUID arrangementId,
            List<Field> selectedFields) {
        Song song = requireSong(songId);
        Proposal proposal = requireProposal(songId, proposalId);
        Arrangement arrangement = arrangementId == null ? null : requireArrangement(songId, arrangementId);
        Set<Field> fields = normalizeFields(selectedFields);
        Candidate candidate = toCandidate(proposal);
        List<FieldDiff> diffs = new ArrayList<>();
        if (fields.contains(Field.CANONICAL_TITLE)) {
            diffs.add(diff(Field.CANONICAL_TITLE, song.canonicalTitle(), candidate.recording().title()));
        }
        if (fields.contains(Field.ORIGINAL_ARTIST_DISPLAY)) {
            diffs.add(diff(Field.ORIGINAL_ARTIST_DISPLAY, song.originalArtistDisplay(),
                    candidate.artist() == null ? null : candidate.artist().name()));
        }
        if (fields.contains(Field.ARRANGEMENT_NAME)) {
            diffs.add(diff(Field.ARRANGEMENT_NAME, arrangement == null ? null : arrangement.name(),
                    candidate.recording().title()));
        }
        if (fields.contains(Field.MUSICBRAINZ_LINKAGE)) {
            String proposed = "recording=" + candidate.recording().mbid()
                    + "; release=" + (candidate.release() == null ? "none" : candidate.release().mbid())
                    + "; release-group=" + (candidate.releaseGroup() == null ? "none" : candidate.releaseGroup().mbid());
            diffs.add(new FieldDiff(Field.MUSICBRAINZ_LINKAGE, null, proposed, false));
        }
        return new Preview(proposalId, arrangementId, List.copyOf(fields), List.copyOf(diffs), candidate.warnings(), etag(song));
    }

    @Transactional
    public MusicBrainzModels.State accept(
            UUID songId,
            UUID proposalId,
            UUID arrangementId,
            List<Field> selectedFields,
            String actor,
            String rationale,
            String ifMatch) {
        Song song = requireSong(songId);
        requireEtag(song, ifMatch);
        Proposal proposal = requireProposal(songId, proposalId);
        if (proposal.status() != ProposalStatus.PROPOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "MusicBrainz proposal is no longer actionable");
        }
        Arrangement arrangement = arrangementId == null ? null : requireArrangement(songId, arrangementId);
        Set<Field> fields = normalizeFields(selectedFields);
        requireText(actor, "actor");
        requireText(rationale, "rationale");
        Candidate candidate = toCandidate(proposal);

        if (fields.contains(Field.CANONICAL_TITLE) || fields.contains(Field.ORIGINAL_ARTIST_DISPLAY)) {
            songRepository.updateSong(songId, new UpdateSongCommand(
                    fields.contains(Field.CANONICAL_TITLE) ? candidate.recording().title() : song.canonicalTitle(),
                    fields.contains(Field.CANONICAL_TITLE)
                            ? titleNormalizer.normalize(candidate.recording().title()) : song.normalizedTitle(),
                    song.primaryLanguage(),
                    fields.contains(Field.ORIGINAL_ARTIST_DISPLAY) && candidate.artist() != null
                            ? candidate.artist().name() : song.originalArtistDisplay(),
                    song.composerCredits(), song.ccliNumber(), song.yearWritten(), song.songStatus(), song.doctrinalNotes()));
        }
        if (fields.contains(Field.ARRANGEMENT_NAME)) {
            if (arrangement == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "arrangementId is required for ARRANGEMENT_NAME");
            }
            songRepository.updateArrangement(arrangement.id(), new UpdateArrangementCommand(
                    candidate.recording().title(), titleNormalizer.normalize(candidate.recording().title()),
                    arrangement.sourceType(), arrangement.language(), arrangement.musicalKey(), arrangement.keyMode(),
                    arrangement.tempoBpm(), arrangement.timeSignature(), arrangement.durationSeconds(),
                    arrangement.energyLevel(), arrangement.difficultyLevel(), arrangement.defaultForSong(), arrangement.active()));
        }

        for (Proposal existing : musicBrainzRepository.findProposals(songId)) {
            if (existing.status() == ProposalStatus.ACCEPTED) {
                musicBrainzRepository.updateProposalStatus(songId, existing.id(), ProposalStatus.SUPERSEDED);
                musicBrainzRepository.updateLinkStatus(songId, existing.id(), ProposalStatus.SUPERSEDED);
            }
        }
        musicBrainzRepository.updateProposalStatus(songId, proposalId, ProposalStatus.ACCEPTED);
        musicBrainzRepository.createLink(new MusicBrainzModels.Link(
                UUID.randomUUID(), songId, arrangementId, proposalId, ProposalStatus.ACCEPTED, proposal.score(),
                proposal.matchMethod(), proposal.artistMbid(), proposal.releaseGroupMbid(), proposal.releaseMbid(),
                proposal.recordingMbid(), writeFields(fields), actor, rationale, Instant.now()));

        var batch = songRepository.createImportBatch(new CreateImportBatchCommand(
                SOURCE_SYSTEM, actor, ImportBatchStatus.RUNNING,
                "{\"proposalId\":\"" + proposalId + "\",\"source\":\"musicbrainz\"}"));
        boolean songFieldSelected = fields.contains(Field.CANONICAL_TITLE)
                || fields.contains(Field.ORIGINAL_ARTIST_DISPLAY);
        UUID provenanceSongId = songFieldSelected || arrangementId == null ? songId : null;
        UUID provenanceArrangementId = provenanceSongId == null ? arrangementId : null;
        songRepository.createProvenanceRecord(new CreateProvenanceRecordCommand(
                provenanceSongId,
                provenanceArrangementId,
                null, batch.id(), SOURCE_SYSTEM, proposal.sourceUri(), SOURCE_LABEL,
                LicenseType.NOT_APPLICABLE, "External metadata identity; not a lyrics or licensing source.",
                ImportMethod.API_IMPORT, proposal.score()));
        songRepository.updateImportBatch(batch.id(), new com.cadentia.catalog.model.UpdateImportBatchCommand(
                ImportBatchStatus.COMPLETED, "{\"acceptedProposalId\":\"" + proposalId + "\"}", true));
        songRepository.appendPrivilegedActionAuditEvent(new AdminAuditEvent(
                UUID.randomUUID(), songId, "CATALOG_SONG", "MUSICBRAINZ_NORMALIZATION_ACCEPTED", actor,
                Instant.now(), rationale, Map.of("proposalId", proposalId.toString()),
                Map.of("selectedFields", fields.toString(), "recordingMbid", proposal.recordingMbid().toString())));
        return getState(songId);
    }

    @Transactional
    public MusicBrainzModels.State reject(UUID songId, UUID proposalId, String actor, String rationale, String ifMatch) {
        Song song = requireSong(songId);
        requireEtag(song, ifMatch);
        Proposal proposal = requireProposal(songId, proposalId);
        if (proposal.status() != ProposalStatus.PROPOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "MusicBrainz proposal is no longer actionable");
        }
        requireText(actor, "actor");
        requireText(rationale, "rationale");
        musicBrainzRepository.updateProposalStatus(songId, proposalId, ProposalStatus.REJECTED);
        songRepository.appendPrivilegedActionAuditEvent(new AdminAuditEvent(
                UUID.randomUUID(), songId, "CATALOG_SONG", "MUSICBRAINZ_NORMALIZATION_REJECTED", actor,
                Instant.now(), rationale, Map.of("proposalId", proposalId.toString()), Map.of("status", "REJECTED")));
        return getState(songId);
    }

    private Proposal stageProposal(Song song, MusicBrainzApiClient.RemoteCandidate remote, String actor) {
        UUID proposalId = UUID.randomUUID();
        String payload = payload(remote);
        saveEntity(remote, EntityType.RECORDING, remote.recording().mbid(), remote.recording().title(), payload);
        if (remote.artist() != null) saveEntity(remote, EntityType.ARTIST, remote.artist().mbid(), remote.artist().name(), payload);
        if (remote.releaseGroup() != null) saveEntity(remote, EntityType.RELEASE_GROUP, remote.releaseGroup().mbid(), remote.releaseGroup().title(), payload);
        if (remote.release() != null) saveEntity(remote, EntityType.RELEASE, remote.release().mbid(), remote.release().title(), payload);
        BigDecimal score = score(song, remote).setScale(4, RoundingMode.HALF_UP);
        return musicBrainzRepository.createProposal(new Proposal(
                proposalId, song.id(), null, remote.artist() == null ? null : remote.artist().mbid(),
                remote.releaseGroup() == null ? null : remote.releaseGroup().mbid(),
                remote.release() == null ? null : remote.release().mbid(), remote.recording().mbid(), score,
                "TITLE_ARTIST_ALBUM_DETERMINISTIC_V1", ProposalStatus.PROPOSED, remote.recording().sourceUri(),
                payload, actor, Instant.now(), Instant.now()));
    }

    private void saveEntity(MusicBrainzApiClient.RemoteCandidate remote, EntityType type, UUID mbid,
            String displayName, String payload) {
        musicBrainzRepository.saveEntity(new EntitySnapshot(type, mbid, displayName, titleNormalizer.normalize(displayName),
                "https://musicbrainz.org/" + type.name().toLowerCase().replace('_', '-') + "/" + mbid,
                payload, Instant.now(), sha256(payload)));
    }

    private Candidate toCandidate(Proposal proposal) {
        try {
            JsonNode node = objectMapper.readTree(proposal.sourcePayload());
            Artist artist = node.hasNonNull("artist") ? objectMapper.treeToValue(node.get("artist"), Artist.class) : null;
            var releaseGroup = node.hasNonNull("releaseGroup")
                    ? objectMapper.treeToValue(node.get("releaseGroup"), MusicBrainzModels.ReleaseGroup.class) : null;
            var release = node.hasNonNull("release")
                    ? objectMapper.treeToValue(node.get("release"), MusicBrainzModels.Release.class) : null;
            var recording = objectMapper.treeToValue(node.get("recording"), MusicBrainzModels.Recording.class);
            return new Candidate(proposal.id(), proposal.status(), proposal.score(), artist, releaseGroup, release,
                    recording, strings(node.get("warnings")), strings(node.get("sourceReferences")));
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "MusicBrainz proposal is unreadable");
        }
    }

    private String payload(MusicBrainzApiClient.RemoteCandidate remote) {
        ObjectNode node = objectMapper.createObjectNode();
        node.set("artist", objectMapper.valueToTree(remote.artist()));
        node.set("releaseGroup", objectMapper.valueToTree(remote.releaseGroup()));
        node.set("release", objectMapper.valueToTree(remote.release()));
        node.set("recording", objectMapper.valueToTree(remote.recording()));
        node.set("warnings", objectMapper.valueToTree(remote.warnings()));
        node.set("sourceReferences", objectMapper.valueToTree(remote.sourceReferences()));
        node.put("rawJson", remote.rawJson());
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception exception) {
            throw new IllegalStateException("MusicBrainz response could not be stored", exception);
        }
    }

    private BigDecimal score(Song song, MusicBrainzApiClient.RemoteCandidate candidate) {
        double title = similarity(song.normalizedTitle(), titleNormalizer.normalize(candidate.recording().title()));
        double artist = candidate.artist() == null ? 0 : similarity(
                titleNormalizer.normalize(song.originalArtistDisplay() == null ? "" : song.originalArtistDisplay()),
                titleNormalizer.normalize(candidate.artist().name()));
        return BigDecimal.valueOf(Math.min(1, title * 0.60 + artist * 0.25 + candidate.score().doubleValue() * 0.15));
    }

    private double similarity(String left, String right) {
        if (left == null || right == null || left.isBlank() || right.isBlank()) return 0;
        if (left.equals(right)) return 1;
        Set<String> leftTokens = Set.of(left.split("[-\\s]+"));
        Set<String> rightTokens = Set.of(right.split("[-\\s]+"));
        long intersection = leftTokens.stream().filter(rightTokens::contains).count();
        long union = leftTokens.size() + rightTokens.size() - intersection;
        return union == 0 ? 0 : (double) intersection / union;
    }

    private FieldDiff diff(Field field, String current, String proposed) {
        return new FieldDiff(field, current, proposed, current != null && !Objects.equals(current, proposed));
    }

    private Set<Field> normalizeFields(List<Field> fields) {
        if (fields == null || fields.isEmpty()) return EnumSet.of(Field.MUSICBRAINZ_LINKAGE);
        return EnumSet.copyOf(fields);
    }

    private void validateSearch(MusicBrainzModels.SearchRequest request) {
        requireText(request.title(), "title");
        if (request.limit() < 1 || request.limit() > 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 10");
        }
    }

    private Song requireSong(UUID songId) {
        return songRepository.findById(songId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Song not found"));
    }

    private Proposal requireProposal(UUID songId, UUID proposalId) {
        return musicBrainzRepository.findProposal(songId, proposalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MusicBrainz proposal not found"));
    }

    private Arrangement requireArrangement(UUID songId, UUID arrangementId) {
        return songRepository.findArrangementById(arrangementId)
                .filter(arrangement -> arrangement.songId().equals(songId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Arrangement not found"));
    }

    private void requireEtag(Song song, String ifMatch) {
        if (!etag(song).equals(ifMatch)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Catalog version is stale");
        }
    }

    private static String etag(Song song) {
        return "\"song-" + song.id() + "-v" + song.updatedAt().toEpochMilli() + "\"";
    }

    private static String writeFields(Set<Field> fields) {
        return fields.stream().map(field -> "\"" + field.name() + "\"").sorted().reduce((left, right) -> "[" + left + "," + right + "]").orElse("[]");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        return value.trim();
    }

    private static List<String> strings(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        node.forEach(value -> values.add(value.asText()));
        return List.copyOf(values);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
