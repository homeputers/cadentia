package com.cadentia.catalog.lyrics;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ParserRolloutOrchestrator {

    private final Map<String, ParserRecalcBatchResult> history = new LinkedHashMap<>();

    public ParserRecalcBatchResult runBatch(ParserRecalcBatchRequest request, ParserRecalcRunner runner) {
        Objects.requireNonNull(request, "request is required");
        Objects.requireNonNull(runner, "runner is required");
        String batchId = request.batchIdentity();

        ParserRecalcBatchResult previous = history.get(batchId);
        Map<UUID, ParserRecalcItemStatus> existing = previous == null ? Map.of() : previous.itemStatuses();
        Map<UUID, ParserRecalcItemStatus> statuses = new LinkedHashMap<>(existing);

        List<ParserRecalcItem> orderedItems = new ArrayList<>(request.items());
        orderedItems.sort(Comparator.comparing(ParserRecalcItem::lyricsDocumentId));

        for (ParserRecalcItem item : orderedItems) {
            ParserRecalcItemStatus existingStatus = statuses.get(item.lyricsDocumentId());
            if (existingStatus == ParserRecalcItemStatus.SUCCEEDED && !item.sourceHashMismatch()) {
                statuses.put(item.lyricsDocumentId(), ParserRecalcItemStatus.SKIPPED_IDEMPOTENT);
                continue;
            }
            if (existingStatus == ParserRecalcItemStatus.FAILED_TERMINAL) {
                continue;
            }
            statuses.put(item.lyricsDocumentId(), runner.execute(item));
        }

        ParserRecalcBatchResult result = new ParserRecalcBatchResult(batchId, Instant.now(), statuses);
        history.put(batchId, result);
        return result;
    }

    public record ParserRecalcBatchRequest(
            String targetParserName,
            String targetParserVersion,
            String selectionPredicate,
            List<ParserRecalcItem> items) {

        public ParserRecalcBatchRequest {
            items = List.copyOf(items == null ? List.of() : items);
        }

        public String batchIdentity() {
            return sha256(targetParserName + "|" + targetParserVersion + "|" + canonicalize(selectionPredicate) + "|"
                    + items.stream().map(ParserRecalcItem::lyricsDocumentId).sorted().map(UUID::toString).reduce("", String::concat));
        }

        private static String canonicalize(String value) {
            return value == null ? "" : value.trim().toLowerCase();
        }

        private static String sha256(String input) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("sha-256 unavailable", exception);
            }
        }
    }

    public record ParserRecalcItem(UUID lyricsDocumentId, boolean sourceHashMismatch) {}

    public enum ParserRecalcItemStatus { PENDING, SUCCEEDED, FAILED_RETRYABLE, FAILED_TERMINAL, SKIPPED_IDEMPOTENT }

    public record ParserRecalcBatchResult(String batchIdentity, Instant finishedAt, Map<UUID, ParserRecalcItemStatus> itemStatuses) {
        public ParserRecalcBatchResult {
            itemStatuses = Map.copyOf(itemStatuses == null ? Map.of() : itemStatuses);
        }
    }

    @FunctionalInterface
    public interface ParserRecalcRunner {
        ParserRecalcItemStatus execute(ParserRecalcItem item);
    }
}
