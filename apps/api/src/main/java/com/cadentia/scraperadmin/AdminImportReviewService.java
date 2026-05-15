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
import com.cadentia.catalog.repository.SongRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminImportReviewService {

    private static final BigDecimal REVIEWED_IMPORT_CONFIDENCE = new BigDecimal("1.0000");

    private final SongRepository songRepository;
    private final TitleNormalizer titleNormalizer;

    public AdminImportReviewService(SongRepository songRepository) {
        this(songRepository, new TitleNormalizer());
    }

    AdminImportReviewService(SongRepository songRepository, TitleNormalizer titleNormalizer) {
        this.songRepository = songRepository;
        this.titleNormalizer = titleNormalizer;
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

        ProvenanceRecord provenanceRecord = createSongProvenance(candidate, targetSong.id(), command.sourceSystem(),
                command.sourceUri(), command.sourceLabel(), command.licenseType(), command.licenseNotes(),
                command.importMethod());
        songRepository.markImportCandidateMerged(candidate.id(), targetSong.id())
                .orElseThrow(() -> new IllegalStateException("Import candidate disappeared during merge"));
        return new AdminMergeResult(targetSong, null, List.of(provenanceRecord), List.of(), false);
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
}
