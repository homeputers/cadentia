package com.cadentia.catalog.lyrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.catalog.lyrics.ParserRolloutOrchestrator.ParserRecalcBatchRequest;
import com.cadentia.catalog.lyrics.ParserRolloutOrchestrator.ParserRecalcItem;
import com.cadentia.catalog.lyrics.ParserRolloutOrchestrator.ParserRecalcItemStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ParserRolloutOrchestratorTest {

    @Test
    void rerunSameBatchIsIdempotentForSucceededItemsWithoutSourceHashMismatch() {
        ParserRolloutOrchestrator orchestrator = new ParserRolloutOrchestrator();
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000011");
        ParserRecalcBatchRequest request = new ParserRecalcBatchRequest("deterministic", "1.0.0", "status=active", List.of(new ParserRecalcItem(id, false)));

        ParserRolloutOrchestrator.ParserRecalcBatchResult first = orchestrator.runBatch(request, item -> ParserRecalcItemStatus.SUCCEEDED);
        ParserRolloutOrchestrator.ParserRecalcBatchResult second = orchestrator.runBatch(request, item -> ParserRecalcItemStatus.SUCCEEDED);

        assertThat(first.itemStatuses().get(id)).isEqualTo(ParserRecalcItemStatus.SUCCEEDED);
        assertThat(second.itemStatuses().get(id)).isEqualTo(ParserRecalcItemStatus.SKIPPED_IDEMPOTENT);
    }

    @Test
    void deterministicOrderingUsesLyricsDocumentIdAscending() {
        ParserRolloutOrchestrator orchestrator = new ParserRolloutOrchestrator();
        List<UUID> visited = new ArrayList<>();
        UUID high = UUID.fromString("00000000-0000-0000-0000-000000000099");
        UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001");
        ParserRecalcBatchRequest request = new ParserRecalcBatchRequest("deterministic", "1.0.0", "status=active", List.of(new ParserRecalcItem(high, false), new ParserRecalcItem(low, false)));

        orchestrator.runBatch(request, item -> {
            visited.add(item.lyricsDocumentId());
            return ParserRecalcItemStatus.SUCCEEDED;
        });

        assertThat(visited).containsExactly(low, high);
    }

    @Test
    void retriesRetryableFailuresAndPreservesTerminalFailures() {
        ParserRolloutOrchestrator orchestrator = new ParserRolloutOrchestrator();
        UUID retry = UUID.fromString("00000000-0000-0000-0000-000000000021");
        UUID terminal = UUID.fromString("00000000-0000-0000-0000-000000000022");
        ParserRecalcBatchRequest request = new ParserRecalcBatchRequest("deterministic", "1.0.0", "status=active", List.of(new ParserRecalcItem(retry, false), new ParserRecalcItem(terminal, false)));

        orchestrator.runBatch(request, item -> item.lyricsDocumentId().equals(retry)
                ? ParserRecalcItemStatus.FAILED_RETRYABLE
                : ParserRecalcItemStatus.FAILED_TERMINAL);
        ParserRolloutOrchestrator.ParserRecalcBatchResult rerun = orchestrator.runBatch(request, item -> ParserRecalcItemStatus.SUCCEEDED);

        assertThat(rerun.itemStatuses().get(retry)).isEqualTo(ParserRecalcItemStatus.SUCCEEDED);
        assertThat(rerun.itemStatuses().get(terminal)).isEqualTo(ParserRecalcItemStatus.FAILED_TERMINAL);
    }
}
