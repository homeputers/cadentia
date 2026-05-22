package com.cadentia.catalog.lyrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.catalog.lyrics.ParserRolloutOrchestrator.ParserRecalcBatchRequest;
import com.cadentia.catalog.lyrics.ParserRolloutOrchestrator.ParserRecalcExecutionResult;
import com.cadentia.catalog.lyrics.ParserRolloutOrchestrator.ParserRecalcItem;
import com.cadentia.catalog.lyrics.ParserRolloutOrchestrator.ParserRecalcItemStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ParserRolloutOrchestratorTest {

    @Test
    void rerunSameBatchIsIdempotentForSucceededItemsWithSameSourceHashAndParserVersion() {
        ParserRolloutOrchestrator orchestrator = new ParserRolloutOrchestrator();
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000011");
        ParserRecalcBatchRequest request = new ParserRecalcBatchRequest(
                "deterministic", "1.0.0", "status=active",
                List.of(new ParserRecalcItem(id, "deterministic", "1.0.0", "0.9.0", "hash-a", "hash-a", false)));

        ParserRolloutOrchestrator.ParserRecalcBatchResult first = orchestrator.runBatch(
                request,
                item -> new ParserRecalcExecutionResult(ParserRecalcItemStatus.SUCCEEDED, item.currentSourceHash(), item.targetParserVersion(), ""));
        ParserRolloutOrchestrator.ParserRecalcBatchResult second = orchestrator.runBatch(
                request,
                item -> new ParserRecalcExecutionResult(ParserRecalcItemStatus.SUCCEEDED, item.currentSourceHash(), item.targetParserVersion(), ""));

        assertThat(first.itemOutcomes().get(id).status()).isEqualTo(ParserRecalcItemStatus.SUCCEEDED);
        assertThat(second.itemOutcomes().get(id).status()).isEqualTo(ParserRecalcItemStatus.SKIPPED_IDEMPOTENT);
    }

    @Test
    void deterministicOrderingUsesParserNameThenVersionThenLyricsDocumentId() {
        ParserRolloutOrchestrator orchestrator = new ParserRolloutOrchestrator();
        List<String> visited = new ArrayList<>();
        ParserRecalcBatchRequest request = new ParserRecalcBatchRequest(
                "deterministic", "1.0.0", "status=active", List.of(
                        new ParserRecalcItem(UUID.fromString("00000000-0000-0000-0000-000000000099"), "b", "1.0.0", "0.9.0", "hash-z", "hash-y", false),
                        new ParserRecalcItem(UUID.fromString("00000000-0000-0000-0000-000000000005"), "a", "2.0.0", "1.0.0", "hash-b", "hash-a", false),
                        new ParserRecalcItem(UUID.fromString("00000000-0000-0000-0000-000000000001"), "a", "1.0.0", "0.9.0", "hash-c", "hash-b", false)));

        orchestrator.runBatch(request, item -> {
            visited.add(item.targetParserName() + ":" + item.targetParserVersion() + ":" + item.lyricsDocumentId());
            return new ParserRecalcExecutionResult(ParserRecalcItemStatus.SUCCEEDED, item.currentSourceHash(), item.targetParserVersion(), "");
        });

        assertThat(visited).containsExactly(
                "a:1.0.0:00000000-0000-0000-0000-000000000001",
                "a:2.0.0:00000000-0000-0000-0000-000000000005",
                "b:1.0.0:00000000-0000-0000-0000-000000000099");
    }

    @Test
    void itemEligibilityUsesParserOrSourceMismatchAndLegalHoldExclusion() {
        ParserRecalcItem parserMismatch = new ParserRecalcItem(UUID.randomUUID(), "det", "2.0.0", "1.0.0", "hash-a", "hash-a", false);
        ParserRecalcItem sourceMismatch = new ParserRecalcItem(UUID.randomUUID(), "det", "1.0.0", "1.0.0", "hash-b", "hash-a", false);
        ParserRecalcItem noMismatch = new ParserRecalcItem(UUID.randomUUID(), "det", "1.0.0", "1.0.0", "hash-a", "hash-a", false);
        ParserRecalcItem legalHold = new ParserRecalcItem(UUID.randomUUID(), "det", "2.0.0", "1.0.0", "hash-b", "hash-a", true);

        assertThat(parserMismatch.eligible()).isTrue();
        assertThat(sourceMismatch.eligible()).isTrue();
        assertThat(noMismatch.eligible()).isFalse();
        assertThat(legalHold.eligible()).isFalse();
    }

    @Test
    void sourceHashChangeBetweenRunsCreatesNewExecutionAndPartialFailureMarkersAreCounted() {
        ParserRolloutOrchestrator orchestrator = new ParserRolloutOrchestrator();
        UUID successId = UUID.fromString("00000000-0000-0000-0000-000000000021");
        UUID retryId = UUID.fromString("00000000-0000-0000-0000-000000000022");

        ParserRecalcBatchRequest first = new ParserRecalcBatchRequest(
                "deterministic", "1.0.0", "status=active", List.of(
                        new ParserRecalcItem(successId, "deterministic", "1.0.0", "0.9.0", "hash-v1", "hash-v0", false),
                        new ParserRecalcItem(retryId, "deterministic", "1.0.0", "0.9.0", "hash-v1", "hash-v0", false)));

        ParserRolloutOrchestrator.ParserRecalcBatchResult firstRun = orchestrator.runBatch(first, item ->
                item.lyricsDocumentId().equals(successId)
                        ? new ParserRecalcExecutionResult(ParserRecalcItemStatus.SUCCEEDED, item.currentSourceHash(), item.targetParserVersion(), "")
                        : new ParserRecalcExecutionResult(ParserRecalcItemStatus.FAILED_RETRYABLE, item.currentSourceHash(), item.targetParserVersion(), "network timeout"));

        assertThat(firstRun.succeededCount()).isEqualTo(1);
        assertThat(firstRun.failedRetryableCount()).isEqualTo(1);

        ParserRecalcBatchRequest changedSource = new ParserRecalcBatchRequest(
                "deterministic", "1.0.0", "status=active", List.of(
                        new ParserRecalcItem(successId, "deterministic", "1.0.0", "1.0.0", "hash-v2", "hash-v1", false),
                        new ParserRecalcItem(retryId, "deterministic", "1.0.0", "1.0.0", "hash-v2", "hash-v1", false)));

        ParserRolloutOrchestrator.ParserRecalcBatchResult secondRun = orchestrator.runBatch(changedSource, item ->
                new ParserRecalcExecutionResult(ParserRecalcItemStatus.SUCCEEDED, item.currentSourceHash(), item.targetParserVersion(), ""));

        assertThat(secondRun.itemOutcomes().get(successId).status()).isEqualTo(ParserRecalcItemStatus.SUCCEEDED);
        assertThat(secondRun.itemOutcomes().get(retryId).status()).isEqualTo(ParserRecalcItemStatus.SUCCEEDED);
    }
}
