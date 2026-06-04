package com.cadentia.scraperadmin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StarterCatalogPackageImportService {

    private static final String IMPORT_METHOD = "STARTER_PACKAGE_IMPORT";

    private final ImportBatchIngestionService ingestionService;
    private final ObjectMapper objectMapper;

    @Autowired
    public StarterCatalogPackageImportService(ImportBatchIngestionService ingestionService, ObjectMapper objectMapper) {
        this.ingestionService = ingestionService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ImportBatchIngestionResult importPackage(StarterCatalogPackageImportCommand command) {
        List<ImportCandidateRecord> candidates = command.songs().stream()
                .map(song -> toCandidate(command, song))
                .toList();
        return ingestionService.ingest(new ImportBatchIngestionCommand(
                sourceSystem(command),
                command.initiatedBy(),
                candidates));
    }

    private ImportCandidateRecord toCandidate(StarterCatalogPackageImportCommand command, StarterCatalogSeedSong song) {
        return new ImportCandidateRecord(
                song.externalSongId(),
                song.externalSongId(),
                requireText(song.title(), "song.title"),
                song.artistName(),
                writeJson(song.artistMetadata()),
                song.ccliNumber(),
                song.lyricsHash(),
                sourcePayload(command, song),
                IMPORT_METHOD,
                sourceReference(command, song),
                command.collectedAt().toString(),
                command.initiatedBy(),
                requireText(song.licenseType(), "song.licenseType"),
                song.licenseEvidence());
    }

    private String sourcePayload(StarterCatalogPackageImportCommand command, StarterCatalogSeedSong song) {
        Map<String, Object> seedPackage = seedPackagePayload(command);
        Map<String, Object> seedSong = seedSongPayload(command, song);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", song.title());
        payload.put("seedOrigin", "STARTER_CATALOG_PACKAGE");
        payload.put("seedPackage", seedPackage);
        payload.put("seedSong", seedSong);
        payload.put("requiresLocalApproval", true);
        payload.put("recommendableBeforeLocalApproval", false);
        return writeJson(payload);
    }

    private static Map<String, Object> seedPackagePayload(StarterCatalogPackageImportCommand command) {
        Map<String, Object> seedPackage = new LinkedHashMap<>();
        seedPackage.put("scope", command.scope().name());
        seedPackage.put("name", command.packageName());
        seedPackage.put("version", command.packageVersion());
        seedPackage.put("sourceUri", command.packageSourceUri());
        seedPackage.put("denomination", command.denomination());
        return seedPackage;
    }

    private static Map<String, Object> seedSongPayload(
            StarterCatalogPackageImportCommand command, StarterCatalogSeedSong song) {
        Map<String, Object> seedSong = new LinkedHashMap<>();
        seedSong.put("externalSongId", song.externalSongId());
        seedSong.put("title", song.title());
        seedSong.put("artistName", song.artistName());
        seedSong.put("artistMetadata", song.artistMetadata());
        seedSong.put("ccliNumber", song.ccliNumber());
        seedSong.put("lyricsHash", song.lyricsHash());
        seedSong.put("sourceReference", sourceReference(command, song));
        seedSong.put("sourceMetadata", song.sourceMetadata());
        seedSong.put("tagSlugs", song.tagSlugs());
        seedSong.put("arrangements", song.arrangements());
        return seedSong;
    }

    private static String sourceSystem(StarterCatalogPackageImportCommand command) {
        return "starter-package:" + command.scope().name().toLowerCase() + ":" + command.packageName()
                + ":" + command.packageVersion();
    }

    private static String sourceReference(StarterCatalogPackageImportCommand command, StarterCatalogSeedSong song) {
        if (song.sourceReference() != null && !song.sourceReference().isBlank()) {
            return song.sourceReference().trim();
        }
        return command.packageSourceUri() + "#song=" + song.externalSongId();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("starter package metadata must be JSON serializable", ex);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
