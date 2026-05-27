package com.cadentia.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.feedback.FeedbackModels.FeedbackEventRecord;
import com.cadentia.feedback.FeedbackModels.FeedbackResetResult;
import com.cadentia.feedback.FeedbackModels.FeedbackScopeAggregate;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FeedbackServiceTest {

    private final RecordingFeedbackRepository repository = new RecordingFeedbackRepository();
    private final RecordingFeedbackObservabilityRecorder recorder = new RecordingFeedbackObservabilityRecorder();
    private final FeedbackService service = new FeedbackService(repository, recorder);

    @Test
    void createEventEmitsIngestionObservabilityRecord() {
        // Given
        FeedbackEventRecord event = repository.createSample();

        // When
        FeedbackEventRecord saved = service.createEvent(event);

        // Then
        assertThat(saved).isEqualTo(event);
        assertThat(recorder.ingestedEvents).containsExactly(event);
    }

    @Test
    void getScopeStateWithFallbackEmitsFallbackIndicator() {
        // Given
        UUID scopeId = UUID.randomUUID();

        // When
        FeedbackScopeAggregate aggregate = service.getScopeStateWithFallback("team", scopeId);

        // Then
        assertThat(aggregate.scopeId()).isEqualTo(scopeId);
        assertThat(recorder.scopeStateReads).containsExactly("team:true");
    }

    @Test
    void resetScopeEmitsAuditObservabilityRecord() {
        // Given
        UUID scopeId = UUID.randomUUID();

        // When
        FeedbackResetResult result = service.resetScope("policy", scopeId, "admin-7");

        // Then
        assertThat(result.auditReference()).isEqualTo("feedback-reset:policy:" + scopeId);
        assertThat(recorder.resets).containsExactly(result);
    }

    private static final class RecordingFeedbackRepository implements FeedbackRepository {
        @Override
        public FeedbackEventRecord createEvent(FeedbackEventRecord event) {
            return event;
        }

        @Override
        public List<FeedbackEventRecord> listEvents(String scopeLayer, UUID scopeId, UUID arrangementId) {
            return List.of();
        }

        @Override
        public Optional<FeedbackScopeAggregate> getScopeAggregate(String scopeLayer, UUID scopeId) {
            return Optional.empty();
        }

        @Override
        public FeedbackResetResult resetScope(String scopeLayer, UUID scopeId, String actorId) {
            return new FeedbackResetResult(scopeLayer, scopeId, actorId, Instant.parse("2026-05-27T00:00:00Z"), "feedback-reset:" + scopeLayer + ":" + scopeId);
        }

        private FeedbackEventRecord createSample() {
            return new FeedbackEventRecord(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "accepted",
                    "team",
                    UUID.randomUUID(),
                    "leader-4",
                    null,
                    null,
                    null,
                    Instant.parse("2026-05-27T00:00:00Z"));
        }
    }

    private static final class RecordingFeedbackObservabilityRecorder implements FeedbackObservabilityRecorder {
        private final java.util.ArrayList<FeedbackEventRecord> ingestedEvents = new java.util.ArrayList<>();
        private final java.util.ArrayList<String> scopeStateReads = new java.util.ArrayList<>();
        private final java.util.ArrayList<FeedbackResetResult> resets = new java.util.ArrayList<>();

        @Override
        public void recordEventIngested(FeedbackEventRecord eventRecord) {
            ingestedEvents.add(eventRecord);
        }

        @Override
        public void recordScopeStateRead(String scopeLayer, UUID scopeId, boolean fallbackReturned) {
            scopeStateReads.add(scopeLayer + ":" + fallbackReturned);
        }

        @Override
        public void recordScopeReset(FeedbackResetResult resetResult) {
            resets.add(resetResult);
        }

        @Override
        public void recordRankingImpactDistribution(Map<UUID, Double> feedbackContributions) {
            // no-op for this service-level test
        }
    }
}
