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
        Map<UUID, ParserRecalcItemOutcome> outcomes = previous == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(previous.itemOutcomes());

        List<ParserRecalcItem> orderedItems = new ArrayList<>(request.items());
        orderedItems.sort(Comparator.comparing(ParserRecalcItem::targetParserName)
                .thenComparing(ParserRecalcItem::targetParserVersion)
                .thenComparing(ParserRecalcItem::lyricsDocumentId));

        for (ParserRecalcItem item : orderedItems) {
            if (!item.eligible()) {
                outcomes.put(item.lyricsDocumentId(), ParserRecalcItemOutcome.skipped(ParserRecalcItemStatus.SKIPPED_INELIGIBLE));
                continue;
            }

            ParserRecalcItemOutcome existing = outcomes.get(item.lyricsDocumentId());
            if (existing != null && existing.status() == ParserRecalcItemStatus.FAILED_TERMINAL) {
                continue;
            }
            if (existing != null
                    && existing.status() == ParserRecalcItemStatus.SUCCEEDED
                    && existing.lastSourceHash().equals(item.currentSourceHash())
                    && existing.lastParserVersion().equals(item.targetParserVersion())) {
                outcomes.put(item.lyricsDocumentId(), ParserRecalcItemOutcome.skipped(ParserRecalcItemStatus.SKIPPED_IDEMPOTENT));
                continue;
            }

            ParserRecalcExecutionResult executionResult = runner.execute(item);
            outcomes.put(item.lyricsDocumentId(), new ParserRecalcItemOutcome(
                    executionResult.status(),
                    executionResult.lastSourceHash(),
                    executionResult.lastParserVersion(),
                    executionResult.diagnostic()));
        }

        ParserRecalcBatchResult result = new ParserRecalcBatchResult(batchId, Instant.now(), outcomes);
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
            String selectionPredicateHash = sha256(canonicalize(selectionPredicate));
            String sourceSnapshotHash = sha256(items.stream()
                    .sorted(Comparator.comparing(ParserRecalcItem::lyricsDocumentId))
                    .map(item -> item.lyricsDocumentId() + ":" + item.currentSourceHash())
                    .reduce("", (a, b) -> a + "|" + b));
            return sha256(targetParserName + "|" + targetParserVersion + "|" + selectionPredicateHash + "|" + sourceSnapshotHash);
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

    public record ParserRecalcItem(
            UUID lyricsDocumentId,
            String targetParserName,
            String targetParserVersion,
            String lastParserVersion,
            String currentSourceHash,
            String lastSourceHash,
            boolean legalHold) {
        public boolean eligible() {
            boolean parserChanged = !Objects.equals(lastParserVersion, targetParserVersion);
            boolean sourceChanged = !Objects.equals(lastSourceHash, currentSourceHash);
            return !legalHold && (parserChanged || sourceChanged);
        }
    }

    public enum ParserRecalcItemStatus {
        PENDING,
        SUCCEEDED,
        FAILED_RETRYABLE,
        FAILED_TERMINAL,
        SKIPPED_IDEMPOTENT,
        SKIPPED_INELIGIBLE
    }

    public record ParserRecalcExecutionResult(
            ParserRecalcItemStatus status,
            String lastSourceHash,
            String lastParserVersion,
            String diagnostic) {}

    public record ParserRecalcItemOutcome(
            ParserRecalcItemStatus status,
            String lastSourceHash,
            String lastParserVersion,
            String diagnostic) {
        static ParserRecalcItemOutcome skipped(ParserRecalcItemStatus status) {
            return new ParserRecalcItemOutcome(status, "", "", "");
        }
    }

    public record ParserRecalcBatchResult(String batchIdentity, Instant finishedAt, Map<UUID, ParserRecalcItemOutcome> itemOutcomes) {
        public ParserRecalcBatchResult {
            itemOutcomes = Map.copyOf(itemOutcomes == null ? Map.of() : itemOutcomes);
        }

        public long succeededCount() {
            return itemOutcomes.values().stream().filter(outcome -> outcome.status() == ParserRecalcItemStatus.SUCCEEDED).count();
        }

        public long failedRetryableCount() {
            return itemOutcomes.values().stream().filter(outcome -> outcome.status() == ParserRecalcItemStatus.FAILED_RETRYABLE).count();
        }

        public long failedTerminalCount() {
            return itemOutcomes.values().stream().filter(outcome -> outcome.status() == ParserRecalcItemStatus.FAILED_TERMINAL).count();
        }
    }

    @FunctionalInterface
    public interface ParserRecalcRunner {
        ParserRecalcExecutionResult execute(ParserRecalcItem item);
    }
}
