package com.cadentia.api.controller;

import com.cadentia.api.controller.ConversationSessionRecord.ConversationSessionSourceStamp;
import com.cadentia.generated.model.ConversationRevisionEvent;
import com.cadentia.generated.model.ConversationState;
import com.cadentia.intent.GenerateSetlistSlots;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcConversationSessionRepository implements ConversationSessionRepository {
    private static final TypeReference<Map<String, ConversationSessionSourceStamp>> SLOT_SOURCES =
            new TypeReference<>() {};
    private static final TypeReference<List<ConversationRevisionEvent>> REVISION_EVENTS =
            new TypeReference<>() {};
    private static final TypeReference<Map<String, String>> CORRELATION_METADATA =
            new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcConversationSessionRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
    }

    @Override
    public Optional<ConversationSessionRecord> findById(UUID sessionId) {
        return jdbcTemplate.query("""
                SELECT *
                FROM conversation_sessions
                WHERE id = :id
                """, new MapSqlParameterSource().addValue("id", sessionId, Types.OTHER), this::mapOptional);
    }

    @Override
    public ConversationSessionRecord save(ConversationSessionRecord record) {
        jdbcTemplate.update("""
                INSERT INTO conversation_sessions (
                    id, channel, state, merged_slots_json, slot_sources_json, revision_events_json,
                    created_at, updated_at, expires_at, expired_at, absolute_expires_at, confirmed_at,
                    last_channel_update_id, last_recommendation_result_id, correlation_metadata_json
                ) VALUES (
                    :id, :channel, :state, CAST(:slots AS jsonb), CAST(:slotSources AS jsonb), CAST(:revisionEvents AS jsonb),
                    :createdAt, :updatedAt, :expiresAt, :expiredAt, :absoluteExpiresAt, :confirmedAt,
                    :lastChannelUpdateId, :lastRecommendationResultId, CAST(:correlationMetadata AS jsonb)
                )
                ON CONFLICT (id) DO UPDATE SET
                    channel = EXCLUDED.channel,
                    state = EXCLUDED.state,
                    merged_slots_json = EXCLUDED.merged_slots_json,
                    slot_sources_json = EXCLUDED.slot_sources_json,
                    revision_events_json = EXCLUDED.revision_events_json,
                    updated_at = EXCLUDED.updated_at,
                    expires_at = EXCLUDED.expires_at,
                    expired_at = EXCLUDED.expired_at,
                    absolute_expires_at = EXCLUDED.absolute_expires_at,
                    confirmed_at = EXCLUDED.confirmed_at,
                    last_channel_update_id = EXCLUDED.last_channel_update_id,
                    last_recommendation_result_id = EXCLUDED.last_recommendation_result_id,
                    correlation_metadata_json = EXCLUDED.correlation_metadata_json
                """, params(record));
        return record;
    }

    private MapSqlParameterSource params(ConversationSessionRecord record) {
        return new MapSqlParameterSource()
                .addValue("id", record.id(), Types.OTHER)
                .addValue("channel", record.channel())
                .addValue("state", record.state().name())
                .addValue("slots", json(record.slots()))
                .addValue("slotSources", json(record.slotSources()))
                .addValue("revisionEvents", json(record.revisionHistory()))
                .addValue("createdAt", timestamp(record.createdAt()))
                .addValue("updatedAt", timestamp(record.updatedAt()))
                .addValue("expiresAt", null)
                .addValue("expiredAt", record.state() == ConversationState.EXPIRED ? timestamp(record.updatedAt()) : null)
                .addValue("absoluteExpiresAt", null)
                .addValue("confirmedAt", timestamp(record.confirmedAt()))
                .addValue("lastChannelUpdateId", record.channelUpdateId())
                .addValue("lastRecommendationResultId", record.recommendationResultId())
                .addValue("correlationMetadata", json(record.correlationMetadata()));
    }

    private Optional<ConversationSessionRecord> mapOptional(ResultSet rs) throws SQLException {
        return rs.next() ? Optional.of(map(rs)) : Optional.empty();
    }

    private ConversationSessionRecord map(ResultSet rs) throws SQLException {
        return new ConversationSessionRecord(
                rs.getObject("id", UUID.class),
                ConversationState.valueOf(rs.getString("state")),
                read(rs.getString("merged_slots_json"), GenerateSetlistSlots.class),
                read(rs.getString("slot_sources_json"), SLOT_SOURCES),
                read(rs.getString("revision_events_json"), REVISION_EVENTS),
                offset(rs.getTimestamp("created_at")),
                offset(rs.getTimestamp("updated_at")),
                offset(rs.getTimestamp("confirmed_at")),
                rs.getString("channel"),
                rs.getString("last_channel_update_id"),
                rs.getString("last_recommendation_result_id"),
                read(rs.getString("correlation_metadata_json"), CORRELATION_METADATA));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize conversation session state.", ex);
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not deserialize conversation session state.", ex);
        }
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not deserialize conversation session state.", ex);
        }
    }

    private Timestamp timestamp(OffsetDateTime value) {
        return value == null ? null : Timestamp.from(value.toInstant());
    }

    private OffsetDateTime offset(Timestamp value) {
        return value == null ? null : OffsetDateTime.ofInstant(value.toInstant(), ZoneOffset.UTC);
    }
}
