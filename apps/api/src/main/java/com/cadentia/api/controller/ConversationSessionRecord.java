package com.cadentia.api.controller;

import com.cadentia.generated.model.ConversationRevisionEvent;
import com.cadentia.generated.model.ConversationState;
import com.cadentia.intent.GenerateSetlistSlots;
import com.cadentia.intent.SlotValueSource;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ConversationSessionRecord(
        UUID id,
        ConversationState state,
        GenerateSetlistSlots slots,
        Map<String, ConversationSessionSourceStamp> slotSources,
        List<ConversationRevisionEvent> revisionHistory,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime confirmedAt,
        String channel,
        String channelUpdateId,
        String recommendationResultId,
        Map<String, String> correlationMetadata) {

    public ConversationSessionRecord {
        slotSources = Map.copyOf(slotSources);
        revisionHistory = List.copyOf(revisionHistory);
        correlationMetadata = Map.copyOf(correlationMetadata);
    }

    public ConversationSessionRecord withState(
            ConversationState nextState,
            List<ConversationRevisionEvent> nextRevisionHistory,
            OffsetDateTime nextUpdatedAt,
            OffsetDateTime nextConfirmedAt) {
        return new ConversationSessionRecord(
                id,
                nextState,
                slots,
                slotSources,
                nextRevisionHistory,
                createdAt,
                nextUpdatedAt,
                nextConfirmedAt,
                channel,
                channelUpdateId,
                recommendationResultId,
                correlationMetadata);
    }

    public ConversationSessionRecord withSlots(
            ConversationState nextState,
            GenerateSetlistSlots nextSlots,
            Map<String, ConversationSessionSourceStamp> nextSlotSources,
            List<ConversationRevisionEvent> nextRevisionHistory,
            OffsetDateTime nextUpdatedAt) {
        return new ConversationSessionRecord(
                id,
                nextState,
                nextSlots,
                nextSlotSources,
                nextRevisionHistory,
                createdAt,
                nextUpdatedAt,
                null,
                channel,
                channelUpdateId,
                recommendationResultId,
                correlationMetadata);
    }

    public ConversationSessionRecord withCorrelation(
            String nextChannel,
            String nextChannelUpdateId,
            String nextRecommendationResultId,
            Map<String, String> nextMetadata,
            OffsetDateTime nextUpdatedAt) {
        return new ConversationSessionRecord(
                id,
                state,
                slots,
                slotSources,
                revisionHistory,
                createdAt,
                nextUpdatedAt,
                confirmedAt,
                nextChannel == null ? channel : nextChannel,
                nextChannelUpdateId == null ? channelUpdateId : nextChannelUpdateId,
                nextRecommendationResultId == null ? recommendationResultId : nextRecommendationResultId,
                nextMetadata(nextMetadata, correlationMetadata));
    }

    private static Map<String, String> nextMetadata(
            Map<String, String> nextMetadata,
            Map<String, String> currentMetadata) {
        return nextMetadata == null ? currentMetadata : nextMetadata;
    }

    public record ConversationSessionSourceStamp(SlotValueSource source, OffsetDateTime updatedAt) {}
}
