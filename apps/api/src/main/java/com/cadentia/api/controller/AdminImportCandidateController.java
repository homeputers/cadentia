package com.cadentia.api.controller;

import com.cadentia.catalog.entity.ApprovalRecord;
import com.cadentia.catalog.entity.Arrangement;
import com.cadentia.catalog.entity.ImportCandidate;
import com.cadentia.catalog.entity.ImportCandidateReview;
import com.cadentia.catalog.entity.LyricsDocument;
import com.cadentia.catalog.entity.ProposedDuplicateMatch;
import com.cadentia.catalog.entity.ProvenanceRecord;
import com.cadentia.catalog.entity.Song;
import com.cadentia.catalog.entity.Tag;
import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.catalog.model.ApprovalType;
import com.cadentia.catalog.model.ArrangementSourceType;
import com.cadentia.catalog.model.CreateArrangementCommand;
import com.cadentia.catalog.model.CreateLyricsDocumentCommand;
import com.cadentia.catalog.model.ImportCandidateReviewDecision;
import com.cadentia.catalog.model.ImportCandidateStatus;
import com.cadentia.catalog.model.KeyMode;
import com.cadentia.catalog.model.LicenseType;
import com.cadentia.catalog.model.LyricsFormat;
import com.cadentia.catalog.model.SongStatus;
import com.cadentia.catalog.model.UpdateArrangementCommand;
import com.cadentia.catalog.model.UpdateLyricsDocumentCommand;
import com.cadentia.catalog.model.UpdateSongCommand;
import com.cadentia.catalog.repository.SongRepository;
import com.cadentia.generated.api.AdminReviewApi;
import com.cadentia.generated.model.AdminApprovalState;
import com.cadentia.generated.model.AdminApprovalStateStatusesInner;
import com.cadentia.generated.model.AdminAuditEventSearchResponse;
import com.cadentia.generated.model.AdminAuditHistoryItem;
import com.cadentia.generated.model.AdminCatalogApprovalRecord;
import com.cadentia.generated.model.AdminCatalogArrangement;
import com.cadentia.generated.model.AdminCatalogArrangementUpdateRequest;
import com.cadentia.generated.model.AdminCatalogLyricsDocument;
import com.cadentia.generated.model.AdminCatalogLyricsUpdateRequest;
import com.cadentia.generated.model.AdminCatalogProvenanceRecord;
import com.cadentia.generated.model.AdminCatalogSongDetailResponse;
import com.cadentia.generated.model.AdminCatalogSongListResponse;
import com.cadentia.generated.model.AdminCatalogSongSummary;
import com.cadentia.generated.model.AdminCatalogSongUpdateRequest;
import com.cadentia.generated.model.AdminCatalogTag;
import com.cadentia.generated.model.AdminDuplicateMatch;
import com.cadentia.generated.model.AdminDuplicateSummary;
import com.cadentia.generated.model.AdminImportCandidateDetailResponse;
import com.cadentia.generated.model.AdminImportCandidateQueueItem;
import com.cadentia.generated.model.AdminImportCandidateQueueResponse;
import com.cadentia.generated.model.AdminManualSongImportRequest;
import com.cadentia.generated.model.AdminParserEvidence;
import com.cadentia.generated.model.AdminProvenanceReference;
import com.cadentia.generated.model.AdminReviewHistoryItem;
import com.cadentia.generated.model.AdminReviewNote;
import com.cadentia.generated.model.AdminSongImportCandidateSummary;
import com.cadentia.generated.model.AdminSongImportMethod;
import com.cadentia.generated.model.AdminSongImportResponse;
import com.cadentia.generated.model.AdminSongImportValidationError;
import com.cadentia.generated.model.AdminSongLicenseType;
import com.cadentia.generated.model.AdminSongResource;
import com.cadentia.generated.model.AllowedImportCandidateAction;
import com.cadentia.generated.model.BulkActionRequest;
import com.cadentia.generated.model.BulkActionResponse;
import com.cadentia.generated.model.BulkActionResult;
import com.cadentia.generated.model.BulkActionType;
import com.cadentia.scraperadmin.ModerationFlagSeverity;
import com.cadentia.scraperadmin.ModerationFlagType;
import com.cadentia.generated.model.ApprovalActionRequest;
import com.cadentia.generated.model.ApprovalReadiness;
import com.cadentia.generated.model.AssignModerationFlagRequest;
import com.cadentia.generated.model.CommitMergeRequest;
import com.cadentia.generated.model.CreateAdminReviewNoteRequest;
import com.cadentia.generated.model.CreateRollbackPreviewRequest;
import com.cadentia.generated.model.DuplicateConfidence;
import com.cadentia.generated.model.EscalateModerationFlagRequest;
import com.cadentia.generated.model.ExecuteRollbackRequest;
import com.cadentia.generated.model.ModerationFlagResponse;
import com.cadentia.generated.model.ModerationFlagStatus;
import com.cadentia.generated.model.ModerationState;
import com.cadentia.generated.model.MergeDecisionRequest;
import com.cadentia.generated.model.OpenModerationFlagRequest;
import com.cadentia.generated.model.ParserSeverity;
import com.cadentia.generated.model.ProvenanceStatus;
import com.cadentia.generated.model.ResolveModerationFlagRequest;
import com.cadentia.generated.model.ReviewPriority;
import com.cadentia.generated.model.RollbackExecutionResponse;
import com.cadentia.generated.model.RollbackImpactedRecord;
import com.cadentia.generated.model.RollbackPreviewResponse;
import com.cadentia.scraperadmin.AdminAuditEvent;
import com.cadentia.scraperadmin.AdminImportCandidateDetail;
import com.cadentia.scraperadmin.AdminImportReviewService;
import com.cadentia.scraperadmin.AdminSongImportService;
import com.cadentia.scraperadmin.AdminSongImportService.AdminSongImportResult;
import com.cadentia.scraperadmin.AdminSongImportService.CsvSongImportCommand;
import com.cadentia.scraperadmin.AdminSongImportService.ManualSongImportCommand;
import com.cadentia.scraperadmin.AdminSongImportService.SongResource;
import com.cadentia.scraperadmin.ApplyApprovalActionCommand;
import com.cadentia.scraperadmin.ApprovalReviewAction;
import com.cadentia.scraperadmin.MergeIntoExistingSongCommand;
import com.cadentia.scraperadmin.ModerationFlag;
import com.cadentia.scraperadmin.ModerationFlagSeverity;
import com.cadentia.scraperadmin.ModerationFlagType;
import com.cadentia.scraperadmin.RollbackExecutionResult;
import com.cadentia.scraperadmin.RollbackPreview;
import com.cadentia.scraperadmin.RollbackTargetType;
import com.cadentia.scraperadmin.StructuredReviewNote;
import com.cadentia.scraperadmin.TitleNormalizer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

@RestController
public class AdminImportCandidateController implements AdminReviewApi {

    private final AdminImportReviewService reviewService;
    private final AdminSongImportService songImportService;
    private final SongRepository songRepository;
    private final TitleNormalizer titleNormalizer = new TitleNormalizer();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminImportCandidateController(
            AdminImportReviewService reviewService,
            AdminSongImportService songImportService,
            SongRepository songRepository) {
        this.reviewService = reviewService;
        this.songImportService = songImportService;
        this.songRepository = songRepository;
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<AdminAuditEventSearchResponse> searchAdminAuditEvents(
            @RequestParam(required = false) String event,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(required = false) String importBatchId,
            @RequestParam(required = false) String candidateId,
            @RequestParam(required = false) String songId,
            @RequestParam(required = false) String arrangementId,
            @RequestParam(required = false) String moderationFlagId,
            @RequestParam(required = false) String rollbackRequestId) {
        List<AdminAuditHistoryItem> items = reviewService.searchAuditEvents(
                        parseUuid(event),
                        blankToNull(entityType),
                        parseUuid(entityId),
                        blankToNull(actor),
                        blankToNull(action),
                        from == null ? null : from.toInstant(),
                        to == null ? null : to.toInstant(),
                        parseUuid(importBatchId),
                        parseUuid(candidateId),
                        parseUuid(songId),
                        parseUuid(arrangementId),
                        parseUuid(moderationFlagId),
                        parseUuid(rollbackRequestId))
                .stream()
                .map(AdminImportCandidateController::toRedactedAuditHistoryItem)
                .toList();
        return ResponseEntity.ok(new AdminAuditEventSearchResponse()
                .items(items)
                .page(1)
                .totalItems(items.size())
                .totalPages(items.isEmpty() ? 0 : 1));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<AdminSongImportResponse> createAdminManualSongImport(
            @RequestBody AdminManualSongImportRequest request) {
        AdminSongImportResult result = songImportService.importManualSong(new ManualSongImportCommand(
                request.getActor(),
                request.getTitle(),
                request.getAuthor(),
                request.getArtist(),
                request.getCcliNumber(),
                request.getCopyright(),
                request.getPublisher(),
                request.getLanguage(),
                request.getKey(),
                request.getBpm(),
                request.getTimeSignature(),
                request.getEnergy(),
                request.getDifficulty(),
                request.getThemes(),
                request.getScriptureReferences(),
                request.getLyrics(),
                request.getChordChart(),
                request.getArrangementNotes(),
                request.getSourceReference(),
                LicenseType.valueOf(request.getLicenseType().getValue()),
                request.getLicenseEvidence(),
                request.getResources().stream().map(AdminImportCandidateController::toSongResource).toList()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toSongImportResponse(result));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<AdminSongImportResponse> createAdminCsvSongImport(
            @RequestParam String actor,
            @RequestPart MultipartFile file,
            @RequestParam AdminSongLicenseType licenseType,
            @RequestParam(required = false) String licenseEvidence) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV file is required");
        }
        AdminSongImportResult result = songImportService.importCsv(new CsvSongImportCommand(
                actor,
                readCsvFile(file),
                file.getOriginalFilename(),
                LicenseType.valueOf(licenseType.getValue()),
                licenseEvidence));
        return ResponseEntity.status(HttpStatus.CREATED).body(toSongImportResponse(result));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<AdminCatalogSongListResponse> listAdminCatalogSongs(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "TITLE") String sort,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "25") Integer pageSize) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safePageSize = pageSize == null || pageSize < 1 ? 25 : Math.min(pageSize, 100);
        String safeStatus = validateSongStatus(status);
        String safeSort = "UPDATED_AT".equals(sort) ? "UPDATED_AT" : "TITLE";
        int offset = (safePage - 1) * safePageSize;
        List<Song> songs = songRepository.findReviewedSongs(query, safeStatus, safeSort, safePageSize, offset);
        int totalItems = songRepository.countReviewedSongs(query, safeStatus);
        List<AdminCatalogSongSummary> items = songs.stream()
                .map(this::toSongSummary)
                .toList();
        return ResponseEntity.ok(new AdminCatalogSongListResponse()
                .items(items)
                .page(safePage)
                .pageSize(safePageSize)
                .totalItems(totalItems)
                .totalPages(totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / safePageSize))
                .sort(safeSort));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<AdminCatalogSongDetailResponse> getAdminCatalogSong(@PathVariable UUID songId) {
        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Song not found"));
        return ResponseEntity.ok(toCatalogSongDetail(song));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<AdminCatalogSongDetailResponse> updateAdminCatalogSong(
            @PathVariable UUID songId,
            @RequestBody AdminCatalogSongUpdateRequest request) {
        Song existing = songRepository.findById(songId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Song not found"));
        String normalizedTitle = request.getNormalizedTitle() == null || request.getNormalizedTitle().isBlank()
                ? titleNormalizer.normalize(request.getCanonicalTitle())
                : request.getNormalizedTitle().trim();
        Song updated = songRepository.updateSong(songId, new UpdateSongCommand(
                        request.getCanonicalTitle(),
                        normalizedTitle,
                        request.getPrimaryLanguage(),
                        blankToNull(request.getOriginalArtistDisplay()),
                        blankToNull(request.getComposerCredits()),
                        blankToNull(request.getCcliNumber()),
                        request.getYearWritten(),
                        SongStatus.valueOf(request.getSongStatus().getValue()),
                        blankToNull(request.getDoctrinalNotes())))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Song not found"));

        for (AdminCatalogArrangementUpdateRequest arrangement : emptyIfNull(request.getArrangements())) {
            String normalizedName = arrangement.getNormalizedName() == null || arrangement.getNormalizedName().isBlank()
                    ? titleNormalizer.normalize(arrangement.getName())
                    : arrangement.getNormalizedName().trim();
            if (arrangement.getArrangementId() == null) {
                songRepository.createArrangement(new CreateArrangementCommand(
                        songId,
                        arrangement.getName(),
                        normalizedName,
                        ArrangementSourceType.valueOf(arrangement.getSourceType().getValue()),
                        arrangement.getLanguage(),
                        blankToNull(arrangement.getMusicalKey()),
                        arrangement.getKeyMode() == null ? null : KeyMode.valueOf(arrangement.getKeyMode().getValue()),
                        arrangement.getTempoBpm(),
                        blankToNull(arrangement.getTimeSignature()),
                        arrangement.getDurationSeconds(),
                        arrangement.getEnergyLevel(),
                        arrangement.getDifficultyLevel(),
                        Boolean.TRUE.equals(arrangement.getDefaultForSong()),
                        Boolean.TRUE.equals(arrangement.getActive())));
            } else {
                Arrangement existingArrangement = songRepository.findArrangementById(arrangement.getArrangementId())
                        .filter(candidate -> candidate.songId().equals(songId))
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Arrangement not found"));
                songRepository.updateArrangement(existingArrangement.id(), new UpdateArrangementCommand(
                                arrangement.getName(),
                                normalizedName,
                                ArrangementSourceType.valueOf(arrangement.getSourceType().getValue()),
                                arrangement.getLanguage(),
                                blankToNull(arrangement.getMusicalKey()),
                                arrangement.getKeyMode() == null ? null : KeyMode.valueOf(arrangement.getKeyMode().getValue()),
                                arrangement.getTempoBpm(),
                                blankToNull(arrangement.getTimeSignature()),
                                arrangement.getDurationSeconds(),
                                arrangement.getEnergyLevel(),
                                arrangement.getDifficultyLevel(),
                                Boolean.TRUE.equals(arrangement.getDefaultForSong()),
                                Boolean.TRUE.equals(arrangement.getActive())))
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Arrangement not found"));
            }
        }

        for (AdminCatalogLyricsUpdateRequest lyrics : emptyIfNull(request.getLyricsDocuments())) {
            if (lyrics.getLyricsDocumentId() == null) {
                if (lyrics.getArrangementId() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "arrangementId is required when creating a new lyrics document");
                }
                songRepository.findArrangementById(lyrics.getArrangementId())
                        .filter(candidate -> candidate.songId().equals(songId))
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Arrangement not found for song"));
                songRepository.createLyricsDocument(new CreateLyricsDocumentCommand(
                        lyrics.getArrangementId(),
                        LyricsFormat.fromDeclaredValue(lyrics.getFormat().getValue()),
                        lyrics.getContent(),
                        sha256(lyrics.getContent()),
                        1,
                        true,
                        Boolean.TRUE.equals(lyrics.getContainsChords()),
                        Boolean.TRUE.equals(lyrics.getContainsSections()),
                        lyrics.getSourceReference(),
                        request.getActor()));
            } else {
                LyricsDocument existingLyrics = songRepository.findLyricsDocumentById(lyrics.getLyricsDocumentId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lyrics document not found"));
                songRepository.findArrangementById(existingLyrics.arrangementId())
                        .filter(candidate -> candidate.songId().equals(songId))
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lyrics arrangement not found"));
                songRepository.updateLyricsDocument(existingLyrics.id(), new UpdateLyricsDocumentCommand(
                                LyricsFormat.fromDeclaredValue(lyrics.getFormat().getValue()),
                                lyrics.getContent(),
                                sha256(lyrics.getContent()),
                                Boolean.TRUE.equals(lyrics.getContainsChords()),
                                Boolean.TRUE.equals(lyrics.getContainsSections()),
                                lyrics.getSourceReference(),
                                request.getActor()))
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lyrics document not found"));
            }
        }

        songRepository.appendPrivilegedActionAuditEvent(new AdminAuditEvent(
                UUID.randomUUID(),
                songId,
                "SONG",
                "ADMIN_CATALOG_SONG_UPDATE",
                request.getActor(),
                java.time.Instant.now(),
                "Admin catalog metadata update",
                Map.of("canonicalTitle", existing.canonicalTitle(), "songStatus", existing.songStatus().name()),
                Map.of("canonicalTitle", updated.canonicalTitle(), "songStatus", updated.songStatus().name())));
        return ResponseEntity.ok(toCatalogSongDetail(updated));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<AdminImportCandidateQueueResponse> listAdminImportCandidates(
            @RequestParam(required = false) com.cadentia.generated.model.ImportCandidateStatus status,
            @RequestParam(required = false) String connectorKey,
            @RequestParam(required = false) UUID batchId,
            @RequestParam(required = false) LocalDate submittedFrom,
            @RequestParam(required = false) LocalDate submittedTo,
            @RequestParam(required = false) String assignedReviewerId,
            @RequestParam(required = false) ParserSeverity parserSeverity,
            @RequestParam(required = false) ProvenanceStatus provenanceStatus,
            @RequestParam(required = false) DuplicateConfidence duplicateConfidence,
            @RequestParam(required = false) ModerationState moderationState,
            @RequestParam(required = false) ReviewPriority reviewPriority,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "25") Integer pageSize) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safePageSize = pageSize == null || pageSize < 1 ? 25 : Math.min(pageSize, 100);
        ImportCandidateStatus domainStatus =
                status == null ? null : ImportCandidateStatus.valueOf(status.getValue());
        List<ImportCandidate> candidates = candidatesForQueue(
                domainStatus,
                batchId,
                safePage,
                safePageSize);
        int totalItems = totalCandidatesForQueue(domainStatus, batchId, candidates);
        List<AdminImportCandidateQueueItem> items = candidates
                .stream()
                .skip(domainStatus == null || batchId != null ? (long) (safePage - 1) * safePageSize : 0L)
                .limit(safePageSize)
                .map(candidate -> {
                    ProvenanceStatus candidateProvenanceStatus = provenanceStatusFor(candidate);
                    ApprovalReadiness candidateApprovalReadiness = approvalReadinessFor(candidate);
                    return new AdminImportCandidateQueueItem()
                        .candidateId(candidate.id())
                        .importBatchId(candidate.importBatchId())
                        .connectorKey("import")
                        .rawTitle(candidate.rawTitle())
                        .normalizedTitle(candidate.normalizedTitle())
                        .sourceArtistName(candidate.sourceArtistName())
                        .status(com.cadentia.generated.model.ImportCandidateStatus.fromValue(candidate.status().name()))
                        .submittedAt(OffsetDateTime.ofInstant(candidate.createdAt(), ZoneOffset.UTC))
                        .updatedAt(OffsetDateTime.ofInstant(candidate.updatedAt(), ZoneOffset.UTC))
                        .parserSeverity(ParserSeverity.NONE)
                        .parserWarningCount(0)
                        .provenanceStatus(candidateProvenanceStatus)
                        .provenanceSummary(provenanceSummaryFor(candidate, candidateProvenanceStatus))
                        .duplicateConfidence(DuplicateConfidence.NONE)
                        .duplicateMatchCount(0)
                        .moderationState(ModerationState.CLEAR)
                        .reviewPriority(ReviewPriority.NORMAL)
                        .approvalReadiness(candidateApprovalReadiness)
                        .readinessSummary(readinessSummaryFor(candidate, candidateApprovalReadiness))
                        .allowedActions(List.of(AllowedImportCandidateAction.VIEW_DETAIL));
                })
                .toList();
        AdminImportCandidateQueueResponse response = new AdminImportCandidateQueueResponse()
                .items(items)
                .page(safePage)
                .pageSize(safePageSize)
                .totalItems(totalItems)
                .totalPages(totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / safePageSize))
                .sort(sort == null || sort.isBlank() ? "updatedAt:desc" : sort);
        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<AdminImportCandidateDetailResponse> getAdminImportCandidateDetail(@PathVariable UUID candidateId) {
        AdminImportCandidateDetail detail = reviewService.getCandidateDetail(candidateId);
        return ResponseEntity.ok(toDetail(detail));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<AdminReviewNote> createAdminImportCandidateReviewNote(
            @PathVariable UUID candidateId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody CreateAdminReviewNoteRequest request) {
        AdminImportCandidateDetail detail = reviewService.getCandidateDetail(candidateId);
        String expectedEtag = etagFor(detail);
        if (!expectedEtag.equals(ifMatch)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Candidate version is stale");
        }
        ImportCandidateReview review = reviewService.addStructuredNote(
                candidateId,
                request.getActor(),
                new StructuredReviewNote(
                        request.getCategory() == null ? "GENERAL" : request.getCategory(),
                        request.getBody(),
                        null));
        return ResponseEntity.ok(toReviewNote(review));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<List<AdminDuplicateMatch>> getAdminImportCandidateDuplicates(@PathVariable UUID candidateId) {
        AdminImportCandidateDetail detail = reviewService.getCandidateDetail(candidateId);
        return ResponseEntity.ok(detail.duplicateMatches().stream().map(AdminImportCandidateController::toDuplicate).toList());
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<List<AdminAuditHistoryItem>> getAdminImportCandidateAuditHistory(@PathVariable UUID candidateId) {
        return ResponseEntity.ok(reviewService.getAuditHistory(candidateId).stream()
                .map(AdminImportCandidateController::toAuditHistoryItem)
                .toList());
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN, 'catalog.admin.review', 'catalog.admin.approve')")
    public ResponseEntity<AdminImportCandidateDetailResponse> submitAdminImportCandidateMergeDecision(
            @PathVariable UUID candidateId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody MergeDecisionRequest request) {
        requireFreshCandidate(candidateId, ifMatch);
        reviewService.submitMergeDecision(
                candidateId,
                request.getActor(),
                request.getDecision().getValue(),
                request.getDuplicateMatchId(),
                request.getRationale());
        return ResponseEntity.ok(toDetail(reviewService.getCandidateDetail(candidateId)));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN, 'catalog.admin.review', 'catalog.admin.approve')")
    public ResponseEntity<AdminImportCandidateDetailResponse> commitAdminImportCandidateMerge(
            @PathVariable UUID candidateId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody CommitMergeRequest request) {
        requireFreshCandidate(candidateId, ifMatch);
        reviewService.commitCandidateMerge(
                candidateId,
                request.getActor(),
                request.getAction().getValue(),
                request.getTargetSongId(),
                selectedFields(request),
                request.getRationale());
        return ResponseEntity.ok(toDetail(reviewService.getCandidateDetail(candidateId)));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN, 'catalog.admin.review', 'catalog.admin.approve')")
    public ResponseEntity<AdminImportCandidateDetailResponse> submitAdminImportCandidateApprovalAction(
            @PathVariable UUID candidateId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody ApprovalActionRequest request) {
        AdminImportCandidateDetail detail = requireFreshCandidate(candidateId, ifMatch);
        if (detail.candidate().mergedSongId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Candidate must be merged before approval actions");
        }
        reviewService.applyApprovalAction(new ApplyApprovalActionCommand(
                detail.candidate().mergedSongId(),
                null,
                null,
                ApprovalType.valueOf(request.getApprovalType()),
                approvalActionFor(request.getAction()),
                request.getActor(),
                request.getRationale()));
        return ResponseEntity.ok(toDetail(reviewService.getCandidateDetail(candidateId)));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN, 'catalog.admin.review', 'catalog.admin.approve')")
    public ResponseEntity<ModerationFlagResponse> openAdminModerationFlag(
            @PathVariable UUID candidateId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody OpenModerationFlagRequest request) {
        requireFreshCandidate(candidateId, ifMatch);
        ModerationFlag flag = reviewService.openModerationFlag(
                candidateId,
                ModerationFlagType.valueOf(request.getType().getValue()),
                Boolean.TRUE.equals(request.getExcludeFromRecommendation()) ? ModerationFlagSeverity.HIGH : ModerationFlagSeverity.WARNING,
                request.getOpenedBy(),
                request.getReason());
        return ResponseEntity.ok(toModerationFlagResponse(flag));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN, 'catalog.admin.review', 'catalog.admin.approve')")
    public ResponseEntity<ModerationFlagResponse> assignAdminModerationFlag(
            @PathVariable UUID flagId,
            @RequestBody AssignModerationFlagRequest request) {
        return ResponseEntity.ok(toModerationFlagResponse(
                reviewService.assignModerationFlag(flagId, request.getAssignedTo(), request.getActor(), request.getReason())));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN, 'catalog.admin.review', 'catalog.admin.approve')")
    public ResponseEntity<ModerationFlagResponse> resolveAdminModerationFlag(
            @PathVariable UUID flagId,
            @RequestBody ResolveModerationFlagRequest request) {
        return ResponseEntity.ok(toModerationFlagResponse(
                reviewService.resolveModerationFlag(flagId, request.getActor(), request.getResolutionNotes())));
    }

    @Override
    @PreAuthorize("hasAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<ModerationFlagResponse> escalateAdminModerationFlag(
            @PathVariable UUID flagId,
            @RequestBody EscalateModerationFlagRequest request) {
        return ResponseEntity.ok(toModerationFlagResponse(
                reviewService.escalateModerationFlag(flagId, request.getActor(), request.getReason())));
    }

    @Override
    @PreAuthorize("hasAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<RollbackPreviewResponse> createAdminRollbackPreview(@RequestBody CreateRollbackPreviewRequest request) {
        RollbackPreview preview = reviewService.previewRollback(
                RollbackTargetType.valueOf(request.getTargetType().getValue()),
                request.getTargetId(),
                request.getActor(),
                request.getImportBatchId());
        RollbackPreviewResponse response = new RollbackPreviewResponse()
                .rollbackRequestId(preview.rollbackRequestId())
                .targetType(com.cadentia.generated.model.RollbackTargetType.fromValue(preview.targetType().name()))
                .targetId(preview.targetId())
                .importBatchId(preview.importBatchId())
                .eligibilityImpacted(preview.eligibilityImpacted())
                .blockers(preview.blockingCodes())
                .impactedRecords(preview.impactedRecords().stream().map(record -> new RollbackImpactedRecord()
                        .entityType(String.valueOf(record.get("entityType")))
                        .entityId(String.valueOf(record.get("entityId")))
                        .status(String.valueOf(record.get("status"))))
                        .toList());
        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("hasAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<RollbackExecutionResponse> executeAdminRollback(@RequestBody ExecuteRollbackRequest request) {
        RollbackExecutionResult result =
                reviewService.executeRollback(request.getRollbackRequestId(), request.getActor(), request.getReason());
        return ResponseEntity.ok(new RollbackExecutionResponse()
                .rollbackRequestId(result.rollbackRequestId())
                .action(result.action())
                .auditEventId(result.auditEventId()));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<BulkActionResponse> bulkActionAdminImportCandidates(
            @RequestBody BulkActionRequest request) {
        List<BulkActionResult> results = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;
        for (UUID candidateId : request.getCandidateIds()) {
            try {
                switch (request.getActionType()) {
                    case ASSIGN_REVIEWER -> reviewService.assignReviewer(candidateId, request.getAssignedReviewerId());
                    case REJECT_DUPLICATE -> reviewService.rejectDuplicate(candidateId, request.getActor(), request.getRationale());
                    case REJECT_NOT_PERMITTED -> reviewService.rejectNotPermitted(candidateId, request.getActor(), request.getRationale());
                    case DEFER -> reviewService.deferCandidate(candidateId, request.getActor(), request.getRationale());
                    case OPEN_MODERATION_FLAG -> reviewService.openModerationFlag(
                            candidateId,
                            ModerationFlagType.valueOf(request.getFlagType()),
                            request.getFlagPolicy() != null && request.getFlagPolicy().equals("BLOCK_UNTIL_RESOLVED")
                                    ? ModerationFlagSeverity.HIGH
                                    : ModerationFlagSeverity.WARNING,
                            request.getActor(),
                            request.getFlagReason());
                    default -> throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "Unsupported bulk action type: " + request.getActionType());
                }
                results.add(new BulkActionResult()
                        .candidateId(candidateId)
                        .success(true));
                successCount++;
            } catch (Exception ex) {
                String message = ex instanceof ResponseStatusException rs ? rs.getReason() : ex.getMessage();
                results.add(new BulkActionResult()
                        .candidateId(candidateId)
                        .success(false)
                        .errorMessage(message));
                failureCount++;
            }
        }
        BulkActionResponse response = new BulkActionResponse()
                .actionType(request.getActionType())
                .processedCount(results.size())
                .successCount(successCount)
                .failureCount(failureCount)
                .results(results);
        return ResponseEntity.ok(response);
    }

    private static String readCsvFile(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read uploaded CSV file", ex);
        }
    }

    private List<ImportCandidate> candidatesForQueue(
            ImportCandidateStatus status,
            UUID batchId,
            int page,
            int pageSize) {
        if (batchId != null) {
            return filterDefaultQueueCandidates(reviewService.findCandidatesForBatch(batchId, status), status);
        }
        if (status != null) {
            return reviewService.findCandidates(status, pageSize, (page - 1) * pageSize);
        }
        return filterDefaultQueueCandidates(reviewService.findCandidates(null, 10_000, 0), null);
    }

    private int totalCandidatesForQueue(
            ImportCandidateStatus status,
            UUID batchId,
            List<ImportCandidate> currentCandidates) {
        if (status != null && batchId == null) {
            return reviewService.countCandidates(status);
        }
        return currentCandidates.size();
    }

    private List<ImportCandidate> filterDefaultQueueCandidates(
            List<ImportCandidate> candidates,
            ImportCandidateStatus status) {
        if (status != null) {
            return candidates;
        }
        return candidates.stream()
                .filter(candidate -> !isCompletedQueueCandidate(candidate))
                .toList();
    }

    private boolean isCompletedQueueCandidate(ImportCandidate candidate) {
        return candidate.status() == ImportCandidateStatus.MERGED
                && approvalReadinessFor(candidate) == ApprovalReadiness.READY
                && provenanceStatusFor(candidate) == ProvenanceStatus.VERIFIED;
    }

    private AdminImportCandidateDetailResponse toDetail(AdminImportCandidateDetail detail) {
        List<AdminReviewNote> reviewNotes = detail.reviewHistory().stream()
                .filter(review -> review.decision() == ImportCandidateReviewDecision.NEEDS_MORE_INFO)
                .map(this::toReviewNote)
                .toList();
        double topDuplicateScore = detail.duplicateMatches().stream()
                .map(ProposedDuplicateMatch::matchScore)
                .filter(score -> score != null)
                .mapToDouble(score -> score.doubleValue())
                .max()
                .orElse(0.0);
        DuplicateConfidence duplicateConfidence = topDuplicateScore >= 0.9
                ? DuplicateConfidence.HIGH
                : topDuplicateScore >= 0.75 ? DuplicateConfidence.MEDIUM : DuplicateConfidence.NONE;
        return new AdminImportCandidateDetailResponse()
                .candidateId(detail.candidate().id())
                .importBatchId(detail.candidate().importBatchId())
                .connectorKey("import")
                .rawTitle(detail.candidate().rawTitle())
                .normalizedTitle(detail.candidate().normalizedTitle())
                .sourceArtistName(detail.candidate().sourceArtistName())
                .sourcePayloadRedacted(true)
                .sourcePayloadJson(null)
                .rawSourceReference(detail.rawSourceReference())
                .parserName(detail.parserName())
                .parserVersion(detail.parserVersion())
                .parserConfidence(detail.parserConfidence())
                .parserWarnings(detail.parserWarnings())
                .status(com.cadentia.generated.model.ImportCandidateStatus.fromValue(detail.candidate().status().name()))
                .allowedActions(allowedActions(detail))
                .version(versionFor(detail))
                .etag(etagFor(detail))
                .eligibilityBlockers(eligibilityBlockers(detail))
                .duplicateSummary(new AdminDuplicateSummary()
                        .confidence(duplicateConfidence)
                        .matchCount(detail.duplicateMatches().size())
                        .topScore(topDuplicateScore == 0.0 ? null : topDuplicateScore)
                        .summary(detail.duplicateMatches().isEmpty() ? "No duplicate detected" : "Backend duplicate signals require review"))
                .provenanceReferences(List.of(new AdminProvenanceReference()
                        .label("import")
                        .sourceReference(sourceReferenceFor(detail))
                        .fingerprint(detail.candidate().lyricsHash())
                        .status(provenanceStatusFor(detail.candidate()))))
                .parserEvidence(new AdminParserEvidence()
                        .parserName(detail.parserName())
                        .parserVersion(detail.parserVersion())
                        .confidence(parseDouble(detail.parserConfidence()))
                        .severity(detail.parserWarnings().isEmpty() ? ParserSeverity.NONE : ParserSeverity.WARNING)
                        .warnings(detail.parserWarnings())
                        .evidenceReferences(List.of()))
                .reviewNotes(reviewNotes)
                .relatedAuditReferences(List.of())
                .duplicateMatches(detail.duplicateMatches().stream().map(AdminImportCandidateController::toDuplicate).toList())
                .reviewHistory(detail.reviewHistory().stream().map(AdminImportCandidateController::toHistory).toList())
                .approvalState(approvalState(detail))
                .moderationFlags(reviewService.listModerationFlagsForCandidate(detail.candidate().id()).stream()
                        .map(AdminImportCandidateController::toModerationFlagResponse)
                        .toList());
    }

    private AdminImportCandidateDetail requireFreshCandidate(UUID candidateId, String ifMatch) {
        AdminImportCandidateDetail detail = reviewService.getCandidateDetail(candidateId);
        String expectedEtag = etagFor(detail);
        if (!expectedEtag.equals(ifMatch)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Candidate version is stale");
        }
        return detail;
    }

    private static SongResource toSongResource(AdminSongResource resource) {
        return new SongResource(
                resource.getResourceType().getValue(),
                resource.getTitle(),
                resource.getUrl(),
                resource.getAssetId(),
                resource.getNotes());
    }

    private static AdminSongImportResponse toSongImportResponse(AdminSongImportResult result) {
        List<AdminSongImportValidationError> validationErrors = result.validationErrors().stream()
                .map(error -> new AdminSongImportValidationError()
                        .rowIdentifier(error.candidateIdentifier())
                        .field(error.field())
                        .message(error.message()))
                .toList();
        return new AdminSongImportResponse()
                .importBatchId(result.importBatchId())
                .status(AdminSongImportResponse.StatusEnum.fromValue(result.status()))
                .method(AdminSongImportMethod.fromValue(result.method().name()))
                .acceptedCount(result.acceptedCount())
                .validationErrorCount(validationErrors.size())
                .candidateIds(result.candidateIds())
                .candidates(result.candidates().stream()
                        .map(candidate -> new AdminSongImportCandidateSummary()
                                .candidateId(candidate.candidateId())
                                .rawTitle(candidate.rawTitle())
                                .normalizedTitle(candidate.normalizedTitle())
                                .sourceArtistName(candidate.sourceArtistName())
                                .status(com.cadentia.generated.model.ImportCandidateStatus.fromValue(candidate.status().name())))
                        .toList())
                .validationErrors(validationErrors);
    }

    private List<AllowedImportCandidateAction> allowedActions(AdminImportCandidateDetail detail) {
        List<AllowedImportCandidateAction> actions = new ArrayList<>();
        actions.add(AllowedImportCandidateAction.VIEW_DETAIL);
        actions.add(AllowedImportCandidateAction.ADD_REVIEW_NOTE);
        actions.add(AllowedImportCandidateAction.OPEN_MODERATION_FLAG);
        if (detail.candidate().status() == ImportCandidateStatus.STAGED
                || detail.candidate().status() == ImportCandidateStatus.DEDUPLICATION_REVIEW) {
            actions.add(AllowedImportCandidateAction.MERGE_DECISION_DEFER);
            actions.add(AllowedImportCandidateAction.MERGE_DECISION_CREATE_NEW);
            if (!detail.duplicateMatches().isEmpty()) {
                actions.add(AllowedImportCandidateAction.MERGE_DECISION_MERGE_EXISTING);
                actions.add(AllowedImportCandidateAction.MERGE_DECISION_REJECT_DUPLICATE);
            }
            actions.add(AllowedImportCandidateAction.MERGE_DECISION_REJECT_NOT_PERMITTED);
        }
        if (detail.candidate().mergedSongId() != null) {
            List<ApprovalRecord> approvals = reviewService.findApprovalRecordsForSong(detail.candidate().mergedSongId());
            if (!pendingApprovalTypes(approvals).isEmpty()) {
                actions.add(AllowedImportCandidateAction.SUBMIT_APPROVAL_ACTION);
            }
            if (approvals.stream().anyMatch(record -> record.status() == ApprovalStatus.APPROVED)) {
                actions.add(AllowedImportCandidateAction.REVERSE_APPROVAL);
            }
        } else if (detail.candidate().status() == ImportCandidateStatus.READY_TO_MERGE) {
            actions.add(AllowedImportCandidateAction.COMMIT_MERGE);
        }
        return actions;
    }

    private static Set<MergeIntoExistingSongCommand.MergeField> selectedFields(CommitMergeRequest request) {
        if (request.getSelectedFields() == null) {
            return Set.of();
        }
        return request.getSelectedFields().stream()
                .map(field -> MergeIntoExistingSongCommand.MergeField.valueOf(field.getValue()))
                .collect(Collectors.toSet());
    }

    private AdminApprovalState approvalState(AdminImportCandidateDetail detail) {
        List<ApprovalRecord> approvals = detail.candidate().mergedSongId() == null
                ? List.of()
                : reviewService.findApprovalRecordsForSong(detail.candidate().mergedSongId());
        List<String> requiredTypes = requiredApprovalTypes().stream().map(ApprovalType::name).toList();
        List<AdminApprovalStateStatusesInner> statuses = approvals.stream()
                .map(record -> new AdminApprovalStateStatusesInner()
                        .type(record.approvalType().name())
                        .status(record.status().name())
                        .actor(record.reviewer()))
                .toList();
        List<String> pendingTypes = pendingApprovalTypes(approvals).stream().map(ApprovalType::name).toList();
        List<String> blockers = detail.candidate().mergedSongId() == null
                ? List.of("Candidate must be merged before approval actions")
                : pendingTypes.isEmpty()
                        ? List.of()
                        : List.of("Pending catalog approvals: " + String.join(", ", pendingTypes));
        return new AdminApprovalState()
                .requiredTypes(requiredTypes)
                .statuses(statuses)
                .blockers(blockers)
                .allowedTransitions(pendingTypes.isEmpty() ? List.of("REVERSE_APPROVAL") : List.of("APPROVE", "REJECT_APPROVAL", "REVERSE_APPROVAL"))
                .eligibilityImpact(blockers.isEmpty()
                        ? "Backend approval gates may permit recommendation eligibility when provenance and moderation also pass."
                        : "Backend approval gates currently block recommendation eligibility.");
    }

    private ProvenanceStatus provenanceStatusFor(ImportCandidate candidate) {
        if (candidate.mergedSongId() == null) {
            return ProvenanceStatus.NEEDS_REVIEW;
        }
        return reviewService.findProvenanceRecordsForSong(candidate.mergedSongId()).isEmpty()
                ? ProvenanceStatus.NEEDS_REVIEW
                : ProvenanceStatus.VERIFIED;
    }

    private String provenanceSummaryFor(
            ImportCandidate candidate,
            ProvenanceStatus provenanceStatus) {
        if (provenanceStatus == ProvenanceStatus.VERIFIED) {
            return "Reviewed import provenance captured during catalog commit.";
        }
        return candidate.mergedSongId() == null
                ? "Commit the candidate to capture catalog provenance."
                : "No catalog provenance record was found for the merged song.";
    }

    private ApprovalReadiness approvalReadinessFor(ImportCandidate candidate) {
        if (candidate.mergedSongId() == null) {
            return ApprovalReadiness.NEEDS_REVIEW;
        }
        return pendingApprovalTypes(reviewService.findApprovalRecordsForSong(candidate.mergedSongId())).isEmpty()
                ? ApprovalReadiness.READY
                : ApprovalReadiness.NEEDS_REVIEW;
    }

    private String readinessSummaryFor(
            ImportCandidate candidate,
            ApprovalReadiness approvalReadiness) {
        if (candidate.mergedSongId() == null) {
            return "Open detail to review and commit this candidate.";
        }
        if (approvalReadiness == ApprovalReadiness.READY) {
            return "Required catalog approvals are complete.";
        }
        List<String> pendingTypes = pendingApprovalTypes(reviewService.findApprovalRecordsForSong(candidate.mergedSongId()))
                .stream()
                .map(ApprovalType::name)
                .toList();
        return "Pending catalog approvals: " + String.join(", ", pendingTypes);
    }

    private static List<ApprovalType> requiredApprovalTypes() {
        return List.of(ApprovalType.EDITORIAL, ApprovalType.LICENSING);
    }

    private static List<ApprovalType> pendingApprovalTypes(List<ApprovalRecord> approvals) {
        return requiredApprovalTypes().stream()
                .filter(type -> approvals.stream()
                        .noneMatch(record -> record.approvalType() == type && record.status() == ApprovalStatus.APPROVED))
                .toList();
    }

    private static ApprovalReviewAction approvalActionFor(ApprovalActionRequest.ActionEnum action) {
        return switch (action) {
            case APPROVE -> ApprovalReviewAction.APPROVE;
            case REJECT_APPROVAL -> ApprovalReviewAction.REJECT;
            case REVERSE_APPROVAL -> ApprovalReviewAction.REVOKE;
        };
    }

    private static String sourceReferenceFor(AdminImportCandidateDetail detail) {
        if (detail.rawSourceReference() != null && !detail.rawSourceReference().isBlank()) {
            return detail.rawSourceReference();
        }
        if (detail.candidate().externalCandidateId() != null && !detail.candidate().externalCandidateId().isBlank()) {
            return detail.candidate().externalCandidateId();
        }
        return detail.candidate().id().toString();
    }

    private AdminCatalogSongDetailResponse toCatalogSongDetail(Song song) {
        List<Arrangement> arrangements = songRepository.findArrangementsBySongId(song.id());
        return new AdminCatalogSongDetailResponse()
                .song(toSongSummary(song, arrangements.size()))
                .doctrinalNotes(song.doctrinalNotes())
                .arrangements(arrangements.stream().map(this::toArrangement).toList())
                .provenance(songRepository.findProvenanceRecordsForSong(song.id()).stream()
                        .map(AdminImportCandidateController::toProvenance)
                        .toList())
                .approvals(songRepository.findApprovalRecordsForSong(song.id()).stream()
                        .map(AdminImportCandidateController::toApproval)
                        .toList())
                .tags(songRepository.findTagsBySongId(song.id()).stream()
                        .map(AdminImportCandidateController::toTag)
                        .toList());
    }

    private AdminCatalogSongSummary toSongSummary(Song song) {
        return toSongSummary(song, songRepository.findArrangementsBySongId(song.id()).size());
    }

    private static AdminCatalogSongSummary toSongSummary(Song song, int arrangementCount) {
        return new AdminCatalogSongSummary()
                .songId(song.id())
                .canonicalTitle(song.canonicalTitle())
                .normalizedTitle(song.normalizedTitle())
                .primaryLanguage(song.primaryLanguage())
                .originalArtistDisplay(song.originalArtistDisplay())
                .composerCredits(song.composerCredits())
                .ccliNumber(song.ccliNumber())
                .yearWritten(song.yearWritten())
                .songStatus(song.songStatus().name())
                .updatedAt(OffsetDateTime.ofInstant(song.updatedAt(), ZoneOffset.UTC))
                .arrangementCount(arrangementCount);
    }

    private AdminCatalogArrangement toArrangement(Arrangement arrangement) {
        return new AdminCatalogArrangement()
                .arrangementId(arrangement.id())
                .songId(arrangement.songId())
                .name(arrangement.name())
                .normalizedName(arrangement.normalizedName())
                .sourceType(arrangement.sourceType().name())
                .language(arrangement.language())
                .musicalKey(arrangement.musicalKey())
                .keyMode(arrangement.keyMode() == null ? null : arrangement.keyMode().name())
                .tempoBpm(arrangement.tempoBpm())
                .timeSignature(arrangement.timeSignature())
                .durationSeconds(arrangement.durationSeconds())
                .energyLevel(arrangement.energyLevel())
                .difficultyLevel(arrangement.difficultyLevel())
                .defaultForSong(arrangement.defaultForSong())
                .active(arrangement.active())
                .updatedAt(OffsetDateTime.ofInstant(arrangement.updatedAt(), ZoneOffset.UTC))
                .lyricsDocuments(songRepository.findLyricsDocumentsByArrangementId(arrangement.id()).stream()
                        .map(AdminImportCandidateController::toLyricsDocument)
                        .toList());
    }

    private static AdminCatalogLyricsDocument toLyricsDocument(LyricsDocument lyricsDocument) {
        return new AdminCatalogLyricsDocument()
                .lyricsDocumentId(lyricsDocument.id())
                .arrangementId(lyricsDocument.arrangementId())
                .format(lyricsDocument.format().storageValue())
                .content(lyricsDocument.content())
                .contentHash(lyricsDocument.contentHash())
                .versionNumber(lyricsDocument.versionNumber())
                .current(lyricsDocument.current())
                .containsChords(lyricsDocument.containsChords())
                .containsSections(lyricsDocument.containsSections())
                .sourceReference(lyricsDocument.sourceReference())
                .createdBy(lyricsDocument.createdBy())
                .createdAt(OffsetDateTime.ofInstant(lyricsDocument.createdAt(), ZoneOffset.UTC))
                .parseStatus(lyricsDocument.parseStatus().name())
                .parseError(lyricsDocument.parseError());
    }

    private static AdminCatalogProvenanceRecord toProvenance(ProvenanceRecord provenanceRecord) {
        return new AdminCatalogProvenanceRecord()
                .provenanceId(provenanceRecord.id())
                .arrangementId(provenanceRecord.arrangementId())
                .lyricsDocumentId(provenanceRecord.lyricsDocumentId())
                .sourceSystem(provenanceRecord.sourceSystem())
                .sourceUri(provenanceRecord.sourceUri())
                .sourceLabel(provenanceRecord.sourceLabel())
                .licenseType(provenanceRecord.licenseType().name())
                .licenseNotes(provenanceRecord.licenseNotes())
                .importMethod(provenanceRecord.importMethod().name())
                .confidenceScore(provenanceRecord.confidenceScore())
                .capturedAt(OffsetDateTime.ofInstant(provenanceRecord.capturedAt(), ZoneOffset.UTC));
    }

    private static AdminCatalogApprovalRecord toApproval(ApprovalRecord approvalRecord) {
        return new AdminCatalogApprovalRecord()
                .approvalId(approvalRecord.id())
                .arrangementId(approvalRecord.arrangementId())
                .lyricsDocumentId(approvalRecord.lyricsDocumentId())
                .approvalType(approvalRecord.approvalType().name())
                .status(approvalRecord.status().name())
                .reviewer(approvalRecord.reviewer())
                .reviewNotes(approvalRecord.reviewNotes())
                .reviewedAt(approvalRecord.reviewedAt() == null
                        ? null
                        : OffsetDateTime.ofInstant(approvalRecord.reviewedAt(), ZoneOffset.UTC))
                .createdAt(OffsetDateTime.ofInstant(approvalRecord.createdAt(), ZoneOffset.UTC));
    }

    private static AdminCatalogTag toTag(Tag tag) {
        return new AdminCatalogTag()
                .tagId(tag.id())
                .tagType(tag.tagType().name())
                .name(tag.name())
                .slug(tag.slug())
                .description(tag.description())
                .active(tag.active());
    }

    private static String validateSongStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        try {
            SongStatus songStatus = SongStatus.valueOf(status);
            if (songStatus != SongStatus.APPROVED && songStatus != SongStatus.IN_REVIEW) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid catalog song status");
            }
            return songStatus.name();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid song status");
        }
    }

    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static <T> List<T> emptyIfNull(List<T> values) {
        return values == null ? List.of() : values;
    }

    private List<String> eligibilityBlockers(AdminImportCandidateDetail detail) {
        if (detail.candidate().status() == ImportCandidateStatus.REJECTED
                || detail.candidate().status() == ImportCandidateStatus.FAILED) {
            return List.of("Candidate status is " + detail.candidate().status().name());
        }
        if (!detail.parserWarnings().isEmpty()) {
            return List.of("Parser warnings require reviewer acknowledgement");
        }
        return List.of();
    }

    private static long versionFor(AdminImportCandidateDetail detail) {
        return detail.candidate().updatedAt().toEpochMilli();
    }

    private static String etagFor(AdminImportCandidateDetail detail) {
        return "\"candidate-" + detail.candidate().id() + "-v" + versionFor(detail) + "\"";
    }

    private static Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static AdminDuplicateMatch toDuplicate(ProposedDuplicateMatch match) {
        return new AdminDuplicateMatch()
                .id(match.id())
                .candidateSongId(match.candidateSongId())
                .matchScore(match.matchScore() == null ? null : match.matchScore().doubleValue())
                .matchSignalsJson(match.matchSignalsJson())
                .status(match.status().name());
    }

    private static AdminReviewHistoryItem toHistory(ImportCandidateReview review) {
        return new AdminReviewHistoryItem()
                .id(review.id())
                .proposedDuplicateMatchId(review.proposedDuplicateMatchId())
                .decision(review.decision().name())
                .reviewer(review.reviewer())
                .reviewNotes(review.reviewNotes())
                .reviewedAt(OffsetDateTime.ofInstant(review.reviewedAt(), ZoneOffset.UTC));
    }

    private AdminReviewNote toReviewNote(ImportCandidateReview review) {
        Map<String, Object> note = parseNote(review.reviewNotes());
        return new AdminReviewNote()
                .noteId(review.id())
                .authorId(review.reviewer())
                .authorDisplayName(review.reviewer())
                .category(String.valueOf(note.getOrDefault("category", review.decision().name())))
                .body(String.valueOf(note.getOrDefault("body", review.reviewNotes())))
                .createdAt(OffsetDateTime.ofInstant(review.reviewedAt(), ZoneOffset.UTC));
    }

    private Map<String, Object> parseNote(String reviewNotes) {
        try {
            return objectMapper.readValue(reviewNotes == null ? "{}" : reviewNotes, new TypeReference<>() {});
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static AdminAuditHistoryItem toRedactedAuditHistoryItem(AdminAuditEvent event) {
        return new AdminAuditHistoryItem()
                .id(event.id())
                .entityId(event.entityId())
                .entityType(event.entityType())
                .action(event.action())
                .actor(event.actor())
                .occurredAt(OffsetDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC))
                .reason(event.reason() == null || event.reason().isBlank() ? null : "Redacted reason retained by audit store")
                .beforeState(null)
                .afterState(Map.of("summary", "Redacted audit payload available to authorized backend processes only"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid UUID filter");
        }
    }

    private static AdminAuditHistoryItem toAuditHistoryItem(AdminAuditEvent event) {
        return new AdminAuditHistoryItem()
                .id(event.id())
                .entityId(event.entityId())
                .entityType(event.entityType())
                .action(event.action())
                .actor(event.actor())
                .occurredAt(OffsetDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC))
                .reason(event.reason())
                .beforeState(event.beforeState())
                .afterState(event.afterState());
    }

    private static ModerationFlagResponse toModerationFlagResponse(ModerationFlag flag) {
        return new ModerationFlagResponse()
                .id(flag.id())
                .importCandidateId(flag.importCandidateId())
                .type(com.cadentia.generated.model.ModerationFlagType.fromValue(flag.type().name()))
                .status(ModerationFlagStatus.fromValue(flag.status().name()))
                .openedBy(flag.openedBy())
                .assignedTo(flag.assignedTo())
                .resolutionNotes(flag.resolutionNotes())
                .scope("IMPORT_CANDIDATE")
                .reason(flag.resolutionNotes())
                .eligibilityImpactPolicy(flag.excludeFromRecommendation()
                        ? "BLOCK_UNTIL_RESOLVED"
                        : "REVIEW_ONLY")
                .excludeFromRecommendation(flag.excludeFromRecommendation())
                .openedAt(OffsetDateTime.ofInstant(flag.openedAt(), ZoneOffset.UTC))
                .updatedAt(OffsetDateTime.ofInstant(flag.updatedAt(), ZoneOffset.UTC));
    }
}
