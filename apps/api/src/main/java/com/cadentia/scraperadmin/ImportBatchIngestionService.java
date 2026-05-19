package com.cadentia.scraperadmin;

import com.cadentia.catalog.entity.ImportBatch;
import com.cadentia.catalog.entity.ImportCandidate;
import com.cadentia.catalog.entity.ProposedDuplicateMatch;
import com.cadentia.catalog.model.CreateImportBatchCommand;
import com.cadentia.catalog.model.CreateImportCandidateCommand;
import com.cadentia.catalog.model.CreateProposedDuplicateMatchCommand;
import com.cadentia.catalog.model.DuplicateMatchStatus;
import com.cadentia.catalog.model.ImportBatchStatus;
import com.cadentia.catalog.model.ImportCandidateStatus;
import com.cadentia.catalog.model.UpdateImportBatchCommand;
import com.cadentia.catalog.repository.SongRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportBatchIngestionService {

    private final SongRepository songRepository;
    private final SongDeduper songDeduper;
    private final TitleNormalizer titleNormalizer;
    private final ObjectMapper objectMapper;

    @Autowired
    public ImportBatchIngestionService(SongRepository songRepository, ObjectMapper objectMapper) {
        this(songRepository, new DeterministicSongDeduper(), new TitleNormalizer(), objectMapper);
    }

    ImportBatchIngestionService(
            SongRepository songRepository,
            SongDeduper songDeduper,
            TitleNormalizer titleNormalizer,
            ObjectMapper objectMapper) {
        this.songRepository = songRepository;
        this.songDeduper = songDeduper;
        this.titleNormalizer = titleNormalizer;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ImportBatchIngestionResult ingest(ImportBatchIngestionCommand command) {
        ImportBatch importBatch = songRepository.createImportBatch(new CreateImportBatchCommand(
                command.sourceSystem(), command.initiatedBy(), ImportBatchStatus.RUNNING, "{}"));
        List<ImportCandidateValidationError> validationErrors = new ArrayList<>();
        List<ImportCandidate> acceptedCandidates = new ArrayList<>();
        List<ProposedDuplicateMatch> proposedMatches = new ArrayList<>();
        List<CatalogSongCandidate> catalogSongs = songRepository.findCatalogSongCandidatesForDeduplication();

        for (int i = 0; i < command.candidates().size(); i++) {
            ImportCandidateRecord candidateRecord = command.candidates().get(i);
            String candidateIdentifier = candidateRecord.displayIdentifier(i + 1);
            ValidatedCandidate validatedCandidate = validate(candidateRecord, candidateIdentifier, validationErrors);
            if (validatedCandidate == null) {
                continue;
            }

            ImportCandidate importCandidate = songRepository.createImportCandidate(new CreateImportCandidateCommand(
                    importBatch.id(),
                    candidateRecord.externalCandidateId(),
                    candidateRecord.rawTitle(),
                    validatedCandidate.normalizedTitle(),
                    candidateRecord.sourceArtistName(),
                    candidateRecord.sourceArtistMetadataJson(),
                    candidateRecord.ccliNumber(),
                    validatedCandidate.rawContentHash(),
                    candidateRecord.sourcePayloadJson(),
                    ImportCandidateStatus.STAGED));

            List<DuplicateSuggestion> suggestions = songDeduper.suggestDuplicates(
                    new ImportSongCandidate(
                            importCandidate.rawTitle(),
                            importCandidate.normalizedTitle(),
                            importCandidate.sourceArtistName(),
                            importCandidate.ccliNumber(),
                            importCandidate.lyricsHash()),
                    catalogSongs);
            importCandidate = songRepository.updateImportCandidateStatus(
                            importCandidate.id(), ImportCandidateStatus.DEDUPLICATION_REVIEW)
                    .orElse(importCandidate);
            acceptedCandidates.add(importCandidate);

            for (DuplicateSuggestion suggestion : suggestions) {
                proposedMatches.add(songRepository.createProposedDuplicateMatch(
                        new CreateProposedDuplicateMatchCommand(
                                importCandidate.id(),
                                suggestion.songId(),
                                suggestion.score(),
                                writeJson(suggestion.signals()),
                                DuplicateMatchStatus.PROPOSED,
                                DeterministicSongDeduper.RULESET_NAME)));
            }
        }

        ImportBatch completedBatch = completeBatch(importBatch, command.candidates().size(), acceptedCandidates,
                proposedMatches, validationErrors);
        return new ImportBatchIngestionResult(completedBatch, acceptedCandidates, proposedMatches, validationErrors);
    }

    private ValidatedCandidate validate(
            ImportCandidateRecord record,
            String candidateIdentifier,
            List<ImportCandidateValidationError> validationErrors) {
        boolean valid = true;
        if (record.rawTitle() == null) {
            validationErrors.add(new ImportCandidateValidationError(
                    candidateIdentifier, "rawTitle", "rawTitle is required"));
            valid = false;
        }
        if (!isJsonObject(record.sourceArtistMetadataJson())) {
            validationErrors.add(new ImportCandidateValidationError(
                    candidateIdentifier, "sourceArtistMetadataJson", "sourceArtistMetadataJson must be a JSON object"));
            valid = false;
        }
        if (!isJsonObject(record.sourcePayloadJson())) {
            validationErrors.add(new ImportCandidateValidationError(
                    candidateIdentifier, "sourcePayloadJson", "sourcePayloadJson must be a JSON object"));
            valid = false;
        }
        if (record.importMethod() == null) {
            validationErrors.add(new ImportCandidateValidationError(
                    candidateIdentifier, "importMethod", "importMethod is required"));
            valid = false;
        }
        if (record.sourceReference() == null) {
            validationErrors.add(new ImportCandidateValidationError(
                    candidateIdentifier, "sourceReference", "sourceReference is required"));
            valid = false;
        }
        if (record.sourceCollectedAt() == null) {
            validationErrors.add(new ImportCandidateValidationError(
                    candidateIdentifier, "sourceCollectedAt", "sourceCollectedAt is required"));
            valid = false;
        } else {
            try {
                Instant.parse(record.sourceCollectedAt());
            } catch (RuntimeException ex) {
                validationErrors.add(new ImportCandidateValidationError(
                        candidateIdentifier, "sourceCollectedAt", "sourceCollectedAt must be an ISO-8601 timestamp"));
                valid = false;
            }
        }
        if (record.operatorIdentity() == null) {
            validationErrors.add(new ImportCandidateValidationError(
                    candidateIdentifier, "operatorIdentity", "operatorIdentity is required"));
            valid = false;
        }
        LicenseGateResult licenseGateResult = evaluateLicenseGate(record);
        if (!licenseGateResult.allowed()) {
            validationErrors.add(new ImportCandidateValidationError(
                    candidateIdentifier, "licenseType", licenseGateResult.message()));
            valid = false;
        }
        if (!valid) {
            return null;
        }
        try {
            String normalizedTitle = titleNormalizer.normalize(record.rawTitle());
            String rawContentHash = hashHex(record.sourcePayloadJson());
            hashHex(normalizedTitle + "|" + record.sourceArtistName() + "|" + record.ccliNumber());
            return new ValidatedCandidate(normalizedTitle, rawContentHash);
        } catch (IllegalArgumentException ex) {
            validationErrors.add(new ImportCandidateValidationError(candidateIdentifier, "rawTitle", ex.getMessage()));
            return null;
        }
    }

    private LicenseGateResult evaluateLicenseGate(ImportCandidateRecord record) {
        if (record.licenseType() == null) {
            return new LicenseGateResult(false, "licenseType is required");
        }
        return switch (record.licenseType()) {
            case "PUBLIC_DOMAIN", "CCLI", "DIRECT_PERMISSION", "NOT_APPLICABLE" -> new LicenseGateResult(true, null);
            case "UNKNOWN", "FAIR_USE_REFERENCE" -> new LicenseGateResult(
                    false, "licenseType requires manual review before review-ready state");
            case "PROHIBITED" -> new LicenseGateResult(false, "licenseType is prohibited");
            default -> new LicenseGateResult(false, "licenseType is not recognized");
        };
    }

    private String hashHex(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private boolean isJsonObject(String value) {
        try {
            return objectMapper.readTree(value).isObject();
        } catch (JsonProcessingException ex) {
            return false;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize duplicate match signals", ex);
        }
    }

    private ImportBatch completeBatch(
            ImportBatch importBatch,
            int totalCount,
            List<ImportCandidate> acceptedCandidates,
            List<ProposedDuplicateMatch> proposedMatches,
            List<ImportCandidateValidationError> validationErrors) {
        ImportBatchStatus status = acceptedCandidates.isEmpty() ? ImportBatchStatus.FAILED : ImportBatchStatus.COMPLETED;
        String summaryJson = writeJson(new ImportBatchSummary(
                totalCount, acceptedCandidates.size(), validationErrors.size(), proposedMatches.size()));
        return songRepository.updateImportBatch(importBatch.id(), new UpdateImportBatchCommand(status, summaryJson, true))
                .orElse(importBatch);
    }

    private record ValidatedCandidate(String normalizedTitle, String rawContentHash) {
    }

    private record LicenseGateResult(boolean allowed, String message) {
    }

    private record ImportBatchSummary(
            int totalCandidates,
            int acceptedCandidates,
            int validationErrors,
            int proposedMatches) {
    }
}
