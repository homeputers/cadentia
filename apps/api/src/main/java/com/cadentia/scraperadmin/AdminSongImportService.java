package com.cadentia.scraperadmin;

import com.cadentia.catalog.model.ImportMethod;
import com.cadentia.catalog.model.LicenseType;
import com.cadentia.catalog.model.ImportCandidateStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AdminSongImportService {

    private static final int MAX_CSV_ROWS = 500;

    private final ImportBatchIngestionService ingestionService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public AdminSongImportService(ImportBatchIngestionService ingestionService, ObjectMapper objectMapper) {
        this(ingestionService, objectMapper, Clock.systemUTC());
    }

    AdminSongImportService(ImportBatchIngestionService ingestionService, ObjectMapper objectMapper, Clock clock) {
        this.ingestionService = ingestionService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public AdminSongImportResult importManualSong(ManualSongImportCommand command) {
        ImportBatchIngestionResult result = ingestionService.ingest(new ImportBatchIngestionCommand(
                "admin-manual-entry",
                command.actor(),
                List.of(toCandidateRecord("manual-1", command))));
        return AdminSongImportResult.from(result, ImportMethod.MANUAL_ENTRY);
    }

    public AdminSongImportResult importCsv(CsvSongImportCommand command) {
        List<Map<String, String>> rows = parseCsv(command.csvContent());
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("csvContent must include a header row and at least one data row");
        }
        if (rows.size() > MAX_CSV_ROWS) {
            throw new IllegalArgumentException("csvContent exceeds maximum row count of " + MAX_CSV_ROWS);
        }
        List<ImportCandidateRecord> candidates = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            candidates.add(toCandidateRecord(index + 1, rows.get(index), command));
        }
        ImportBatchIngestionResult result = ingestionService.ingest(new ImportBatchIngestionCommand(
                "admin-csv-import:" + safeFileName(command.fileName()),
                command.actor(),
                candidates));
        return AdminSongImportResult.from(result, ImportMethod.CSV_IMPORT);
    }

    private ImportCandidateRecord toCandidateRecord(String rowIdentifier, ManualSongImportCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("metadata", nullableMap(
                "title", command.title(),
                "author", command.author(),
                "artist", command.artist(),
                "ccliNumber", command.ccliNumber(),
                "copyright", command.copyright(),
                "publisher", command.publisher(),
                "language", command.language()));
        payload.put("suggestionDetails", nullableMap(
                "key", command.key(),
                "bpm", command.bpm(),
                "timeSignature", command.timeSignature(),
                "energy", command.energy(),
                "difficulty", command.difficulty(),
                "themes", command.themes(),
                "scriptureReferences", command.scriptureReferences()));
        payload.put("songDetails", nullableMap(
                "lyrics", command.lyrics(),
                "chordChart", command.chordChart(),
                "arrangementNotes", command.arrangementNotes()));
        payload.put("resources", command.resources());
        payload.put("licenseEvidence", command.licenseEvidence());

        return new ImportCandidateRecord(
                rowIdentifier,
                UUID.randomUUID().toString(),
                command.title(),
                firstPresent(command.artist(), command.author()),
                writeJson(nullableMap(
                        "author", command.author(),
                        "artist", command.artist(),
                        "publisher", command.publisher(),
                        "copyright", command.copyright(),
                        "language", command.language())),
                command.ccliNumber(),
                null,
                writeJson(payload),
                ImportMethod.MANUAL_ENTRY.name(),
                firstPresent(command.sourceReference(), "admin-manual-entry:" + rowIdentifier),
                Instant.now(clock).toString(),
                command.actor(),
                command.licenseType().name(),
                command.licenseEvidence());
    }

    private ImportCandidateRecord toCandidateRecord(
            int rowNumber,
            Map<String, String> row,
            CsvSongImportCommand command) {
        String rowIdentifier = "csv-row-" + rowNumber;
        String title = value(row, "title");
        String author = value(row, "author", "writer", "writers");
        String artist = value(row, "artist");
        String sourceReference = firstPresent(value(row, "sourceReference", "source", "url"),
                safeFileName(command.fileName()) + "#" + rowIdentifier);
        List<String> themes = splitList(value(row, "themes", "tags"));
        List<String> scripture = splitList(value(row, "scriptureReferences", "scripture"));
        List<SongResource> resources = resourcesFromCsv(row);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("metadata", nullableMap(
                "title", title,
                "author", author,
                "artist", artist,
                "ccliNumber", value(row, "ccliNumber", "ccli"),
                "copyright", value(row, "copyright"),
                "publisher", value(row, "publisher"),
                "language", value(row, "language")));
        payload.put("suggestionDetails", nullableMap(
                "key", value(row, "key"),
                "bpm", value(row, "bpm", "tempo"),
                "timeSignature", value(row, "timeSignature", "meter"),
                "energy", value(row, "energy"),
                "difficulty", value(row, "difficulty"),
                "themes", themes,
                "scriptureReferences", scripture));
        payload.put("songDetails", nullableMap(
                "lyrics", value(row, "lyrics"),
                "chordChart", value(row, "chordChart", "chords"),
                "arrangementNotes", value(row, "arrangementNotes", "notes")));
        payload.put("resources", resources);
        payload.put("licenseEvidence", firstPresent(value(row, "licenseEvidence"), command.licenseEvidence()));

        return new ImportCandidateRecord(
                rowIdentifier,
                firstPresent(value(row, "externalCandidateId", "id"), UUID.randomUUID().toString()),
                title,
                firstPresent(artist, author),
                writeJson(nullableMap(
                        "author", author,
                        "artist", artist,
                        "publisher", value(row, "publisher"),
                        "copyright", value(row, "copyright"),
                        "language", value(row, "language"))),
                value(row, "ccliNumber", "ccli"),
                null,
                writeJson(payload),
                ImportMethod.CSV_IMPORT.name(),
                sourceReference,
                Instant.now(clock).toString(),
                command.actor(),
                firstPresent(value(row, "licenseType"), command.licenseType().name()),
                firstPresent(value(row, "licenseEvidence"), command.licenseEvidence()));
    }

    private List<Map<String, String>> parseCsv(String csvContent) {
        List<List<String>> records = readCsvRecords(csvContent);
        if (records.size() < 2) {
            return List.of();
        }
        List<String> headers = records.get(0).stream().map(AdminSongImportService::normalizeHeader).toList();
        List<Map<String, String>> rows = new ArrayList<>();
        for (int index = 1; index < records.size(); index++) {
            List<String> record = records.get(index);
            if (record.stream().allMatch(value -> value == null || value.isBlank())) {
                continue;
            }
            Map<String, String> row = new HashMap<>();
            for (int column = 0; column < headers.size(); column++) {
                String header = headers.get(column);
                if (!header.isBlank()) {
                    row.put(header, column < record.size() ? record.get(column).trim() : "");
                }
            }
            rows.add(row);
        }
        return rows;
    }

    private static List<List<String>> readCsvRecords(String csvContent) {
        List<List<String>> records = new ArrayList<>();
        List<String> currentRecord = new ArrayList<>();
        StringBuilder currentCell = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < csvContent.length(); index++) {
            char ch = csvContent.charAt(index);
            if (ch == '"') {
                if (quoted && index + 1 < csvContent.length() && csvContent.charAt(index + 1) == '"') {
                    currentCell.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                currentRecord.add(currentCell.toString());
                currentCell.setLength(0);
            } else if ((ch == '\n' || ch == '\r') && !quoted) {
                if (ch == '\r' && index + 1 < csvContent.length() && csvContent.charAt(index + 1) == '\n') {
                    index++;
                }
                currentRecord.add(currentCell.toString());
                records.add(currentRecord);
                currentRecord = new ArrayList<>();
                currentCell.setLength(0);
            } else {
                currentCell.append(ch);
            }
        }
        currentRecord.add(currentCell.toString());
        records.add(currentRecord);
        return records;
    }

    private static List<SongResource> resourcesFromCsv(Map<String, String> row) {
        String raw = value(row, "resources", "resourceUrls", "resourceUrl");
        if (raw == null) {
            return List.of();
        }
        return splitList(raw).stream()
                .map(url -> new SongResource("OTHER", "CSV resource", url, null, null))
                .toList();
    }

    private static String value(Map<String, String> row, String... keys) {
        for (String key : keys) {
            String value = row.get(normalizeHeader(key));
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static List<String> splitList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String item : raw.split("[;|]")) {
            if (!item.isBlank()) {
                values.add(item.trim());
            }
        }
        return values;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize admin song import payload", ex);
        }
    }

    private static String normalizeHeader(String header) {
        return header == null ? "" : header.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private static String safeFileName(String fileName) {
        return fileName == null || fileName.isBlank() ? "pasted.csv" : fileName.trim();
    }

    private static String firstPresent(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private static Map<String, Object> nullableMap(Object... keysAndValues) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < keysAndValues.length; index += 2) {
            values.put((String) keysAndValues[index], keysAndValues[index + 1]);
        }
        return values;
    }

    public record ManualSongImportCommand(
            String actor,
            String title,
            String author,
            String artist,
            String ccliNumber,
            String copyright,
            String publisher,
            String language,
            String key,
            Integer bpm,
            String timeSignature,
            Integer energy,
            Integer difficulty,
            List<String> themes,
            List<String> scriptureReferences,
            String lyrics,
            String chordChart,
            String arrangementNotes,
            String sourceReference,
            LicenseType licenseType,
            String licenseEvidence,
            List<SongResource> resources) {

        public ManualSongImportCommand {
            themes = List.copyOf(themes == null ? List.of() : themes);
            scriptureReferences = List.copyOf(scriptureReferences == null ? List.of() : scriptureReferences);
            resources = List.copyOf(resources == null ? List.of() : resources);
        }
    }

    public record CsvSongImportCommand(
            String actor,
            String csvContent,
            String fileName,
            LicenseType licenseType,
            String licenseEvidence) {
    }

    public record SongResource(
            String resourceType,
            String title,
            String url,
            UUID assetId,
            String notes) {
    }

    public record AdminSongImportResult(
            UUID importBatchId,
            String status,
            ImportMethod method,
            int acceptedCount,
            List<UUID> candidateIds,
            List<CandidateSummary> candidates,
            List<ImportCandidateValidationError> validationErrors) {

        public static AdminSongImportResult from(ImportBatchIngestionResult result, ImportMethod method) {
            return new AdminSongImportResult(
                    result.importBatch().id(),
                    result.importBatch().status().name(),
                    method,
                    result.acceptedCandidates().size(),
                    result.acceptedCandidates().stream().map(candidate -> candidate.id()).toList(),
                    result.acceptedCandidates().stream()
                            .map(candidate -> new CandidateSummary(
                                    candidate.id(),
                                    candidate.rawTitle(),
                                    candidate.normalizedTitle(),
                                    candidate.sourceArtistName(),
                                    candidate.status()))
                            .toList(),
                    result.validationErrors());
        }
    }

    public record CandidateSummary(
            UUID candidateId,
            String rawTitle,
            String normalizedTitle,
            String sourceArtistName,
            ImportCandidateStatus status) {
    }
}
