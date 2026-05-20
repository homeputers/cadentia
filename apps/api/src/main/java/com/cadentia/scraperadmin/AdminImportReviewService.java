package com.cadentia.scraperadmin;

import com.cadentia.catalog.entity.ApprovalRecord;
import com.cadentia.catalog.entity.Arrangement;
import com.cadentia.catalog.entity.ImportCandidate;
import com.cadentia.catalog.entity.ImportCandidateReview;
import com.cadentia.catalog.entity.ProposedDuplicateMatch;
import com.cadentia.catalog.entity.ProvenanceRecord;
import com.cadentia.catalog.entity.Song;
import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.catalog.model.ApprovalType;
import com.cadentia.catalog.model.ArrangementSourceType;
import com.cadentia.catalog.model.CreateApprovalRecordCommand;
import com.cadentia.catalog.model.CreateArrangementCommand;
import com.cadentia.catalog.model.CreateImportCandidateReviewCommand;
import com.cadentia.catalog.model.CreateProvenanceRecordCommand;
import com.cadentia.catalog.model.CreateSongCommand;
import com.cadentia.catalog.model.DuplicateMatchStatus;
import com.cadentia.catalog.model.ImportCandidateReviewDecision;
import com.cadentia.catalog.model.ImportCandidateStatus;
import com.cadentia.catalog.model.ImportMethod;
import com.cadentia.catalog.model.LicenseType;
import com.cadentia.catalog.model.SongStatus;
import com.cadentia.catalog.model.UpdateSongCommand;
import com.cadentia.catalog.repository.SongRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminImportReviewService {

    private static final BigDecimal REVIEWED_IMPORT_CONFIDENCE = new BigDecimal("1.0000");

    private final SongRepository songRepository;
    private final TitleNormalizer titleNormalizer;
    private final ObjectMapper objectMapper;

    @Autowired
    public AdminImportReviewService(SongRepository songRepository) {
        this(songRepository, new TitleNormalizer(), new ObjectMapper());
    }

    AdminImportReviewService(SongRepository songRepository, TitleNormalizer titleNormalizer, ObjectMapper objectMapper) {
        this.songRepository = songRepository;
        this.titleNormalizer = titleNormalizer;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ImportCandidate> findCandidatesForBatch(UUID importBatchId, ImportCandidateStatus status) {
        List<ImportCandidate> candidates = songRepository.findImportCandidatesByBatchId(importBatchId);
        if (status == null) {
            return candidates;
        }
        return candidates.stream().filter(candidate -> candidate.status() == status).toList();
    }

    @Transactional(readOnly = true)
    public AdminImportCandidateDetail getCandidateDetail(UUID importCandidateId) {
        ImportCandidate candidate = requireCandidate(importCandidateId);
        Map<String, Object> payload = parseJsonObject(candidate.sourcePayloadJson());
        Map<String, Object> parserEvidence = nestedMap(payload, "parserEvidence");
        List<String> warnings = parserWarnings(payload, parserEvidence);
        List<ProposedDuplicateMatch> duplicateMatches = songRepository.findProposedDuplicateMatchesByImportCandidateId(importCandidateId);
        List<ImportCandidateReview> reviews = songRepository.findImportCandidateReviewsByImportCandidateId(importCandidateId);
        return new AdminImportCandidateDetail(
                candidate,
                asString(payload.get("sourceReference")),
                asString(parserEvidence.get("parserName")),
                asString(parserEvidence.get("parserVersion")),
                asString(parserEvidence.get("confidence")),
                warnings,
                duplicateMatches,
                reviews);
    }

    @Transactional
    public ImportCandidateReview addStructuredNote(UUID importCandidateId, String reviewer, StructuredReviewNote note) {
        ImportCandidate candidate = requireCandidate(importCandidateId);
        String notesJson = toJson(Map.of(
                "category", note.category(),
                "body", note.body(),
                "followUpAction", note.followUpAction()));
        return songRepository.createImportCandidateReview(new CreateImportCandidateReviewCommand(
                candidate.id(),
                null,
                ImportCandidateReviewDecision.NEEDS_MORE_INFO,
                reviewer,
                notesJson));
    }

    @Transactional
    public ImportCandidateReview recordReview(CreateImportCandidateReviewCommand command) {
        ImportCandidate candidate = requireCandidate(command.importCandidateId());
        ProposedDuplicateMatch proposedMatch = null;
        if (command.proposedDuplicateMatchId() != null) {
            proposedMatch = requireProposedMatch(command.proposedDuplicateMatchId(), candidate.id());
        }

        ImportCandidateReview review = songRepository.createImportCandidateReview(command);
        if (proposedMatch != null) {
            songRepository.updateProposedDuplicateMatchStatus(proposedMatch.id(), DuplicateMatchStatus.REVIEWED);
        }
        songRepository.updateImportCandidateStatus(candidate.id(), statusFor(command.decision()));
        return review;
    }

    @Transactional
    public AdminMergeResult mergeIntoExistingSong(MergeIntoExistingSongCommand command) {
        ImportCandidate candidate = requireCandidate(command.importCandidateId());
        if (candidate.status() == ImportCandidateStatus.MERGED) {
            Song mergedSong = requireSong(candidate.mergedSongId());
            return new AdminMergeResult(mergedSong, null, List.of(), List.of(), true);
        }

        Song targetSong = requireSong(command.targetSongId());
        requirePermittingReview(candidate.id(), ImportCandidateReviewDecision.CONFIRM_MATCH, targetSong.id());

        Song mergedSong = mergeSelectedCandidateFields(candidate, targetSong, command);
        ProvenanceRecord provenanceRecord = createSongProvenance(candidate, targetSong.id(), command.sourceSystem(),
                command.sourceUri(), command.sourceLabel(), command.licenseType(), command.licenseNotes(),
                command.importMethod());
        songRepository.markImportCandidateMerged(candidate.id(), targetSong.id())
                .orElseThrow(() -> new IllegalStateException("Import candidate disappeared during merge"));
        return new AdminMergeResult(mergedSong, null, List.of(provenanceRecord), List.of(), false);
    }

    private Song mergeSelectedCandidateFields(ImportCandidate candidate, Song targetSong, MergeIntoExistingSongCommand command) {
        if (command.selectedFields().isEmpty()) {
            return targetSong;
        }
        Map<String, Object> payload = parseJsonObject(candidate.sourcePayloadJson());
        boolean approvedSong = songRepository.findApprovalRecordsForSong(targetSong.id()).stream()
                .anyMatch(record -> record.status() == ApprovalStatus.APPROVED);
        String importedTitle = stringOrNull(payload.get("title"));
        if (approvedSong && importedTitle != null
                && !importedTitle.equals(targetSong.canonicalTitle())
                && !command.selectedFields().contains(MergeIntoExistingSongCommand.MergeField.CANONICAL_TITLE)) {
            throw new IllegalStateException("Conflict on canonicalTitle requires explicit reviewer field selection");
        }
        UpdateSongCommand update = new UpdateSongCommand(
                command.selectedFields().contains(MergeIntoExistingSongCommand.MergeField.CANONICAL_TITLE) && importedTitle != null
                        ? importedTitle
                        : targetSong.canonicalTitle(),
                command.selectedFields().contains(MergeIntoExistingSongCommand.MergeField.CANONICAL_TITLE) && importedTitle != null
                        ? titleNormalizer.normalize(importedTitle)
                        : targetSong.normalizedTitle(),
                targetSong.primaryLanguage(),
                targetSong.originalArtistDisplay(),
                targetSong.composerCredits(),
                targetSong.ccliNumber(),
                targetSong.yearWritten(),
                targetSong.songStatus(),
                targetSong.doctrinalNotes());
        return songRepository.updateSong(targetSong.id(), update).orElse(targetSong);
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @Transactional
    public AdminMergeResult createNewCanonicalSong(CreateCanonicalSongFromImportCandidateCommand command) {
        ImportCandidate candidate = requireCandidate(command.importCandidateId());
        if (candidate.status() == ImportCandidateStatus.MERGED) {
            Song mergedSong = requireSong(candidate.mergedSongId());
            return new AdminMergeResult(mergedSong, null, List.of(), List.of(), true);
        }

        requirePermittingReview(candidate.id(), ImportCandidateReviewDecision.CREATE_NEW_SONG, null);
        String normalizedTitle = titleNormalizer.normalize(command.canonicalTitle());
        Song song = songRepository.findByNormalizedTitleAndLanguage(normalizedTitle, command.primaryLanguage())
                .orElseGet(() -> songRepository.createSong(new CreateSongCommand(
                        command.canonicalTitle(),
                        normalizedTitle,
                        command.primaryLanguage(),
                        command.originalArtistDisplay(),
                        command.composerCredits(),
                        command.ccliNumber(),
                        command.yearWritten(),
                        SongStatus.IN_REVIEW,
                        command.doctrinalNotes())));

        Arrangement arrangement = ensureImportedDefaultArrangement(song, command);
        ProvenanceRecord songProvenanceRecord = createSongProvenance(candidate, song.id(), command.sourceSystem(),
                command.sourceUri(), command.sourceLabel(), command.licenseType(), command.licenseNotes(),
                command.importMethod());
        ProvenanceRecord arrangementProvenanceRecord = songRepository.createProvenanceRecord(
                new CreateProvenanceRecordCommand(
                        null,
                        arrangement.id(),
                        null,
                        candidate.importBatchId(),
                        command.sourceSystem(),
                        command.sourceUri(),
                        sourceLabel(candidate, command.sourceLabel()),
                        command.licenseType(),
                        command.licenseNotes(),
                        command.importMethod(),
                        REVIEWED_IMPORT_CONFIDENCE));
        ApprovalRecord approvalRecord = songRepository.createApprovalRecord(new CreateApprovalRecordCommand(
                song.id(),
                null,
                null,
                ApprovalType.EDITORIAL,
                ApprovalStatus.PENDING,
                command.reviewer(),
                "Created from reviewed import candidate; approval remains pending."));

        songRepository.markImportCandidateMerged(candidate.id(), song.id())
                .orElseThrow(() -> new IllegalStateException("Import candidate disappeared during create-new merge"));
        return new AdminMergeResult(song, arrangement, List.of(songProvenanceRecord, arrangementProvenanceRecord),
                List.of(approvalRecord), false);
    }

    private Arrangement ensureImportedDefaultArrangement(Song song, CreateCanonicalSongFromImportCandidateCommand command) {
        String arrangementName = command.arrangementName() == null ? song.canonicalTitle() : command.arrangementName();
        String normalizedArrangementName = titleNormalizer.normalize(arrangementName);
        return songRepository.findArrangementsBySongId(song.id()).stream()
                .filter(arrangement -> arrangement.language().equals(command.primaryLanguage()))
                .filter(arrangement -> arrangement.normalizedName().equals(normalizedArrangementName))
                .findFirst()
                .orElseGet(() -> songRepository.createArrangement(new CreateArrangementCommand(
                        song.id(),
                        arrangementName,
                        normalizedArrangementName,
                        ArrangementSourceType.UNKNOWN,
                        command.primaryLanguage(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        true,
                        true)));
    }

    private ProvenanceRecord createSongProvenance(
            ImportCandidate candidate,
            UUID songId,
            String sourceSystem,
            String sourceUri,
            String sourceLabel,
            LicenseType licenseType,
            String licenseNotes,
            ImportMethod importMethod) {
        return songRepository.createProvenanceRecord(new CreateProvenanceRecordCommand(
                songId,
                null,
                null,
                candidate.importBatchId(),
                sourceSystem,
                sourceUri,
                sourceLabel(candidate, sourceLabel),
                licenseType,
                licenseNotes,
                importMethod,
                REVIEWED_IMPORT_CONFIDENCE));
    }

    private void requirePermittingReview(
            UUID importCandidateId,
            ImportCandidateReviewDecision requiredDecision,
            UUID requiredTargetSongId) {
        List<ImportCandidateReview> reviews = songRepository.findImportCandidateReviewsByImportCandidateId(importCandidateId);
        boolean permitted = reviews.stream()
                .filter(review -> review.decision() == requiredDecision)
                .anyMatch(review -> requiredTargetSongId == null || review.proposedDuplicateMatchId() != null
                        && songRepository.findProposedDuplicateMatchById(review.proposedDuplicateMatchId())
                                .map(ProposedDuplicateMatch::candidateSongId)
                                .filter(requiredTargetSongId::equals)
                                .isPresent());
        if (!permitted) {
            throw new IllegalStateException("A permitting admin review decision is required before merge");
        }
    }

    private ImportCandidate requireCandidate(UUID importCandidateId) {
        return songRepository.findImportCandidateById(importCandidateId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown import candidate: " + importCandidateId));
    }

    private Song requireSong(UUID songId) {
        return songRepository.findById(Objects.requireNonNull(songId, "songId is required"))
                .orElseThrow(() -> new IllegalArgumentException("Unknown canonical song: " + songId));
    }

    private ProposedDuplicateMatch requireProposedMatch(UUID proposedDuplicateMatchId, UUID importCandidateId) {
        ProposedDuplicateMatch proposedMatch = songRepository.findProposedDuplicateMatchById(proposedDuplicateMatchId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown proposed duplicate match: "
                        + proposedDuplicateMatchId));
        if (!proposedMatch.importCandidateId().equals(importCandidateId)) {
            throw new IllegalArgumentException("Proposed duplicate match does not belong to import candidate");
        }
        return proposedMatch;
    }

    private static ImportCandidateStatus statusFor(ImportCandidateReviewDecision decision) {
        return switch (decision) {
            case CONFIRM_MATCH, CREATE_NEW_SONG -> ImportCandidateStatus.READY_TO_MERGE;
            case REJECT_CANDIDATE -> ImportCandidateStatus.REJECTED;
            case REJECT_MATCH, NEEDS_MORE_INFO -> ImportCandidateStatus.DEDUPLICATION_REVIEW;
        };
    }

    private static String sourceLabel(ImportCandidate candidate, String sourceLabel) {
        String externalIdentifier = candidate.externalCandidateId() == null ? "no external id" : candidate.externalCandidateId();
        return sourceLabel + " (importCandidateId=" + candidate.id() + ", externalCandidateId=" + externalIdentifier + ")";
    }

    private Map<String, Object> parseJsonObject(String json) {
        try {
            return objectMapper.readValue(json == null ? "{}" : json, new TypeReference<>() {});
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static Map<String, Object> nestedMap(Map<String, Object> root, String key) {
        Object value = root.get(key);
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> casted = (Map<String, Object>) map;
            return casted;
        }
        return Map.of();
    }

    private List<String> parserWarnings(Map<String, Object> payload, Map<String, Object> parserEvidence) {
        List<String> warnings = new ArrayList<>();
        warnings.addAll(stringList(parserEvidence.get("warnings")));
        warnings.addAll(stringList(payload.get("parserWarnings")));
        return warnings;
    }

    private List<String> stringList(Object value) {
        if (value == null) {
            return List.of();
        }
        JsonNode node = objectMapper.valueToTree(value);
        if (!node.isArray()) {
            return List.of();
        }
        List<String> results = new ArrayList<>();
        node.forEach(item -> {
            if (item.isTextual()) {
                results.add(item.asText());
            }
        });
        return results;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize structured review note", exception);
        }
    }
}
