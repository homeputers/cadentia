package com.cadentia.scraperadmin;

import com.cadentia.catalog.entity.ApprovalRecord;
import com.cadentia.catalog.entity.Arrangement;
import com.cadentia.catalog.entity.ImportCandidate;
import com.cadentia.catalog.entity.ImportCandidateReview;
import com.cadentia.catalog.entity.ProposedDuplicateMatch;
import com.cadentia.catalog.entity.ProvenanceRecord;
import com.cadentia.catalog.entity.Song;
import com.cadentia.catalog.model.ApprovalStatus;

import com.cadentia.catalog.model.ApprovalStatusTransition;
import com.cadentia.catalog.model.UpdateApprovalRecordCommand;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminImportReviewService {

    private static final BigDecimal REVIEWED_IMPORT_CONFIDENCE = new BigDecimal("1.0000");
    private static final Map<String, List<AdminAuthorizationRole>> AUTHORIZATION_MATRIX = Map.of(
            "REVIEW_RECORD", List.of(AdminAuthorizationRole.REVIEWER, AdminAuthorizationRole.APPROVER),
            "MERGE_CANDIDATE", List.of(AdminAuthorizationRole.REVIEWER, AdminAuthorizationRole.APPROVER),
            "CREATE_CANONICAL", List.of(AdminAuthorizationRole.REVIEWER, AdminAuthorizationRole.APPROVER),
            "APPLY_APPROVAL", List.of(AdminAuthorizationRole.APPROVER),
            "OPEN_MODERATION", List.of(AdminAuthorizationRole.REVIEWER, AdminAuthorizationRole.APPROVER),
            "ASSIGN_MODERATION", List.of(AdminAuthorizationRole.REVIEWER, AdminAuthorizationRole.APPROVER),
            "RESOLVE_MODERATION", List.of(AdminAuthorizationRole.REVIEWER, AdminAuthorizationRole.APPROVER),
            "ESCALATE_MODERATION", List.of(AdminAuthorizationRole.APPROVER),
            "PREVIEW_ROLLBACK", List.of(AdminAuthorizationRole.ROLLBACK_ADMIN),
            "EXECUTE_ROLLBACK", List.of(AdminAuthorizationRole.ROLLBACK_ADMIN));

    private final SongRepository songRepository;
    private final TitleNormalizer titleNormalizer;
    private final ObjectMapper objectMapper;
    private final Map<UUID, ModerationFlag> moderationFlagsById = new ConcurrentHashMap<>();
    private final Map<UUID, List<AdminAuditEvent>> auditEventsByEntityId = new ConcurrentHashMap<>();
    private final Map<UUID, RollbackPreview> rollbackPreviewsById = new ConcurrentHashMap<>();

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


    

    @Transactional(readOnly = true)
    public RollbackPreview previewRollback(RollbackTargetType targetType, UUID targetId, String actor, UUID importBatchId) {
        requireAuthorized("PREVIEW_ROLLBACK", actor, targetId, "IMPORT_CANDIDATE");
        List<String> blockers = new ArrayList<>();
        List<Map<String, Object>> impacted = new ArrayList<>();
        boolean eligibilityImpacted = false;

        if (targetType == RollbackTargetType.IMPORT_CANDIDATE) {
            ImportCandidate candidate = requireCandidate(targetId);
            if (importBatchId != null && !importBatchId.equals(candidate.importBatchId())) {
                blockers.add("target candidate does not belong to selected import batch");
            }
            impacted.add(Map.of("entityType", "IMPORT_CANDIDATE", "entityId", candidate.id().toString(), "status", candidate.status().name()));
            eligibilityImpacted = candidate.status() == ImportCandidateStatus.REJECTED || candidate.status() == ImportCandidateStatus.MERGED;
        } else if (targetType == RollbackTargetType.MODERATION_FLAG) {
            ModerationFlag flag = requireFlag(targetId);
            ImportCandidate candidate = requireCandidate(flag.importCandidateId());
            if (importBatchId != null && !importBatchId.equals(candidate.importBatchId())) {
                blockers.add("moderation flag candidate does not belong to selected import batch");
            }
            impacted.add(Map.of("entityType", "MODERATION_FLAG", "entityId", flag.id().toString(), "status", flag.status().name()));
            impacted.add(Map.of("entityType", "IMPORT_CANDIDATE", "entityId", candidate.id().toString(), "status", candidate.status().name()));
            eligibilityImpacted = flag.excludeFromRecommendation();
        } else {
            blockers.add("approval rollback preview is not yet supported");
        }

        RollbackPreview preview = new RollbackPreview(UUID.randomUUID(), targetType, targetId, importBatchId, eligibilityImpacted, impacted, blockers);
        rollbackPreviewsById.put(preview.rollbackRequestId(), preview);
        return preview;
    }

    @Transactional
    public RollbackExecutionResult executeRollback(UUID rollbackRequestId, String actor, String reason) {
        requireAuthorized("EXECUTE_ROLLBACK", actor, rollbackRequestId, "ROLLBACK_REQUEST");
        RollbackPreview preview = rollbackPreviewsById.get(rollbackRequestId);
        if (preview == null) {
            throw new IllegalArgumentException("Unknown rollback request: " + rollbackRequestId);
        }
        if (!preview.blockers().isEmpty()) {
            throw new IllegalStateException("Rollback blocked: " + String.join("; ", preview.blockers()));
        }

        String action;
        UUID entityId;
        if (preview.targetType() == RollbackTargetType.IMPORT_CANDIDATE) {
            ImportCandidate candidate = requireCandidate(preview.targetId());
            songRepository.updateImportCandidateStatus(candidate.id(), ImportCandidateStatus.DEDUPLICATION_REVIEW);
            action = "ROLLBACK_IMPORT_CANDIDATE_STATUS";
            entityId = candidate.id();
        } else if (preview.targetType() == RollbackTargetType.MODERATION_FLAG) {
            ModerationFlag flag = requireFlag(preview.targetId());
            ModerationFlag updated = new ModerationFlag(
                    flag.id(),
                    flag.importCandidateId(),
                    flag.type(),
                    ModerationFlagStatus.OPEN,
                    flag.openedBy(),
                    flag.assignedTo(),
                    flag.resolutionNotes(),
                    false,
                    flag.openedAt(),
                    Instant.now());
            moderationFlagsById.put(flag.id(), updated);
            songRepository.updateImportCandidateStatus(flag.importCandidateId(), ImportCandidateStatus.DEDUPLICATION_REVIEW);
            action = "ROLLBACK_MODERATION_FLAG";
            entityId = flag.importCandidateId();
        } else {
            throw new IllegalStateException("Rollback target type not supported: " + preview.targetType());
        }

        AdminAuditEvent event = addAuditEvent(entityId, "IMPORT_CANDIDATE", action, actor, reason,
                Map.of("rollbackRequestId", rollbackRequestId.toString()),
                Map.of("rollbackExecuted", true, "targetType", preview.targetType().name(), "targetId", preview.targetId().toString()));
        return new RollbackExecutionResult(rollbackRequestId, action, event.id());
    }
@Transactional(readOnly = true)
    public List<AdminAuditEvent> getAuditHistory(UUID entityId) {
        return List.copyOf(auditEventsByEntityId.getOrDefault(entityId, List.of()));
    }

    @Transactional
    public ModerationFlag openModerationFlag(
            UUID importCandidateId,
            ModerationFlagType type,
            String openedBy,
            String reason,
            boolean excludeFromRecommendation) {
        requireAuthorized("OPEN_MODERATION", openedBy, importCandidateId, "IMPORT_CANDIDATE");
        requireCandidate(importCandidateId);
        Instant now = Instant.now();
        ModerationFlag flag = new ModerationFlag(
                UUID.randomUUID(),
                importCandidateId,
                type,
                ModerationFlagStatus.OPEN,
                openedBy,
                null,
                null,
                excludeFromRecommendation,
                now,
                now);
        moderationFlagsById.put(flag.id(), flag);
        addAuditEvent(importCandidateId, "IMPORT_CANDIDATE", "MODERATION_FLAG_OPENED", openedBy, reason, Map.of(), Map.of(
                "flagId", flag.id().toString(),
                "type", flag.type().name(),
                "status", flag.status().name(),
                "excludeFromRecommendation", flag.excludeFromRecommendation()));
        if (excludeFromRecommendation) {
            songRepository.updateImportCandidateStatus(importCandidateId, ImportCandidateStatus.REJECTED);
        }
        return flag;
    }

    @Transactional
    public ModerationFlag assignModerationFlag(UUID flagId, String assignedTo, String actor, String reason) {
        requireAuthorized("ASSIGN_MODERATION", actor, flagId, "MODERATION_FLAG");
        ModerationFlag flag = requireFlag(flagId);
        ModerationFlag updated = new ModerationFlag(
                flag.id(),
                flag.importCandidateId(),
                flag.type(),
                ModerationFlagStatus.ASSIGNED,
                flag.openedBy(),
                assignedTo,
                flag.resolutionNotes(),
                flag.excludeFromRecommendation(),
                flag.openedAt(),
                Instant.now());
        moderationFlagsById.put(flagId, updated);
        addAuditEvent(flag.importCandidateId(), "IMPORT_CANDIDATE", "MODERATION_FLAG_ASSIGNED", actor, reason,
                Map.of("status", flag.status().name(), "assignedTo", String.valueOf(flag.assignedTo())),
                Map.of("status", updated.status().name(), "assignedTo", assignedTo));
        return updated;
    }

    @Transactional
    public ModerationFlag resolveModerationFlag(UUID flagId, String actor, String resolutionNotes) {
        requireAuthorized("RESOLVE_MODERATION", actor, flagId, "MODERATION_FLAG");
        ModerationFlag flag = requireFlag(flagId);
        ModerationFlag updated = new ModerationFlag(
                flag.id(),
                flag.importCandidateId(),
                flag.type(),
                ModerationFlagStatus.RESOLVED,
                flag.openedBy(),
                flag.assignedTo(),
                resolutionNotes,
                flag.excludeFromRecommendation(),
                flag.openedAt(),
                Instant.now());
        moderationFlagsById.put(flagId, updated);
        addAuditEvent(flag.importCandidateId(), "IMPORT_CANDIDATE", "MODERATION_FLAG_RESOLVED", actor, resolutionNotes,
                Map.of("status", flag.status().name()),
                Map.of("status", updated.status().name(), "resolutionNotes", resolutionNotes));
        return updated;
    }

    @Transactional
    public ModerationFlag escalateModerationFlag(UUID flagId, String actor, String reason) {
        requireAuthorized("ESCALATE_MODERATION", actor, flagId, "MODERATION_FLAG");
        ModerationFlag flag = requireFlag(flagId);
        ModerationFlag updated = new ModerationFlag(
                flag.id(),
                flag.importCandidateId(),
                flag.type(),
                ModerationFlagStatus.ESCALATED,
                flag.openedBy(),
                flag.assignedTo(),
                flag.resolutionNotes(),
                true,
                flag.openedAt(),
                Instant.now());
        moderationFlagsById.put(flagId, updated);
        songRepository.updateImportCandidateStatus(flag.importCandidateId(), ImportCandidateStatus.REJECTED);
        addAuditEvent(flag.importCandidateId(), "IMPORT_CANDIDATE", "MODERATION_FLAG_ESCALATED", actor, reason,
                Map.of("status", flag.status().name(), "excludeFromRecommendation", flag.excludeFromRecommendation()),
                Map.of("status", updated.status().name(), "excludeFromRecommendation", true));
        return updated;
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
        requireAuthorized("REVIEW_RECORD", command.reviewer(), command.importCandidateId(), "IMPORT_CANDIDATE");
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
        requireAuthorized("MERGE_CANDIDATE", command.reviewer(), command.importCandidateId(), "IMPORT_CANDIDATE");
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
    public ApprovalRecord applyApprovalAction(ApplyApprovalActionCommand command) {
        UUID approvalEntityId = command.songId() == null
                ? (command.arrangementId() == null ? command.lyricsDocumentId() : command.arrangementId())
                : command.songId();
        requireAuthorized("APPLY_APPROVAL", command.reviewer(), approvalEntityId, "APPROVAL_TARGET");
        ApprovalStatus nextStatus = statusFor(command.action());
        return songRepository
                .findApprovalRecord(command.songId(), command.arrangementId(), command.lyricsDocumentId(), command.approvalType())
                .map(existingRecord -> {
                    ApprovalStatusTransition.requireAllowed(existingRecord.status(), nextStatus);
                    return songRepository.updateApprovalRecord(
                                    existingRecord.id(),
                                    new UpdateApprovalRecordCommand(nextStatus, command.reviewer(), command.reviewNotes()))
                            .orElseThrow(() -> new IllegalStateException(
                                    "Approval record disappeared during update: " + existingRecord.id()));
                })
                .orElseGet(() -> {
                    if (nextStatus != ApprovalStatus.PENDING) {
                        throw new IllegalArgumentException(
                                "Cannot apply " + command.action() + " without an existing approval record");
                    }
                    return songRepository.createApprovalRecord(new CreateApprovalRecordCommand(
                            command.songId(),
                            command.arrangementId(),
                            command.lyricsDocumentId(),
                            command.approvalType(),
                            nextStatus,
                            command.reviewer(),
                            command.reviewNotes()));
                });
    }

    @Transactional
    public AdminMergeResult createNewCanonicalSong(CreateCanonicalSongFromImportCandidateCommand command) {
        requireAuthorized("CREATE_CANONICAL", command.reviewer(), command.importCandidateId(), "IMPORT_CANDIDATE");
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

    private ModerationFlag requireFlag(UUID flagId) {
        ModerationFlag flag = moderationFlagsById.get(flagId);
        if (flag == null) {
            throw new IllegalArgumentException("Moderation flag not found: " + flagId);
        }
        return flag;
    }

    private AdminAuditEvent addAuditEvent(
            UUID entityId,
            String entityType,
            String action,
            String actor,
            String reason,
            Map<String, Object> beforeState,
            Map<String, Object> afterState) {
        AdminAuditEvent event = new AdminAuditEvent(
                UUID.randomUUID(),
                entityId,
                entityType,
                action,
                actor,
                Instant.now(),
                reason,
                beforeState,
                afterState);
        auditEventsByEntityId.computeIfAbsent(entityId, ignored -> new ArrayList<>()).add(event);
        return event;
    }

    private void requireAuthorized(String action, String actor, UUID entityId, String entityType) {
        AdminAuthorizationRole role = AdminAuthorizationRole.fromActor(actor);
        List<AdminAuthorizationRole> allowedRoles = AUTHORIZATION_MATRIX.getOrDefault(action, List.of());
        if (allowedRoles.contains(role)) {
            return;
        }
        addAuditEvent(entityId, entityType, action + "_DENIED", actor, "authorization denied",
                Map.of("requiredRoles", allowedRoles.toString()), Map.of("actualRole", role.name()));
        throw new IllegalStateException("Authorization denied for action " + action + " with role " + role.name());
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


    private static ApprovalStatus statusFor(ApprovalReviewAction action) {
        return switch (action) {
            case APPROVE -> ApprovalStatus.APPROVED;
            case REJECT -> ApprovalStatus.REJECTED;
            case NEEDS_CHANGES, REVOKE -> ApprovalStatus.NEEDS_REVIEW;
        };
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
