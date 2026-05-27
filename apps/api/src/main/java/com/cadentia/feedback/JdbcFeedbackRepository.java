package com.cadentia.feedback;

import com.cadentia.feedback.FeedbackModels.FeedbackEventRecord;
import com.cadentia.feedback.FeedbackModels.FeedbackResetResult;
import com.cadentia.feedback.FeedbackModels.FeedbackScopeAggregate;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcFeedbackRepository implements FeedbackRepository {
    private static final TypeReference<Map<String, Integer>> COUNTS_TYPE = new TypeReference<>() { };
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcFeedbackRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public FeedbackEventRecord createEvent(FeedbackEventRecord event) {
        FeedbackEventRecord saved = jdbcTemplate.queryForObject("""
                INSERT INTO recommendation_feedback_events (
                    setlist_id, setlist_version_id, arrangement_id, outcome, scope_layer, scope_id,
                    actor_id, replacement_reason, replaced_with_arrangement_id, familiarity_score
                ) VALUES (
                    :setlistId, :setlistVersionId, :arrangementId, :outcome, :scopeLayer, :scopeId,
                    :actorId, :replacementReason, :replacedWithArrangementId, :familiarityScore
                ) RETURNING *
                """, new MapSqlParameterSource()
                .addValue("setlistId", event.setlistId())
                .addValue("setlistVersionId", event.setlistVersionId())
                .addValue("arrangementId", event.arrangementId())
                .addValue("outcome", event.outcome())
                .addValue("scopeLayer", event.scopeLayer())
                .addValue("scopeId", event.scopeId())
                .addValue("actorId", event.actorId())
                .addValue("replacementReason", event.replacementReason())
                .addValue("replacedWithArrangementId", event.replacedWithArrangementId())
                .addValue("familiarityScore", event.familiarityScore()),
                (rs, rowNum) -> mapEvent(rs));

        updateAggregate(saved);
        return saved;
    }

    private void updateAggregate(FeedbackEventRecord event) {
        jdbcTemplate.update("""
                INSERT INTO recommendation_feedback_scope_aggregates (
                    scope_layer, scope_id, accepted_count, rejected_count, skipped_count, favorited_count,
                    replacement_reason_counts, last_feedback_at, updated_at
                ) VALUES (
                    :scopeLayer, :scopeId,
                    CASE WHEN :outcome = 'accepted' THEN 1 ELSE 0 END,
                    CASE WHEN :outcome = 'rejected' THEN 1 ELSE 0 END,
                    CASE WHEN :outcome = 'skipped' THEN 1 ELSE 0 END,
                    CASE WHEN :outcome = 'favorited' THEN 1 ELSE 0 END,
                    CASE WHEN :replacementReason IS NULL THEN '{}'::jsonb ELSE jsonb_build_object(:replacementReason, 1) END,
                    :createdAt,
                    NOW()
                ) ON CONFLICT (scope_layer, scope_id) DO UPDATE SET
                    accepted_count = recommendation_feedback_scope_aggregates.accepted_count + CASE WHEN :outcome = 'accepted' THEN 1 ELSE 0 END,
                    rejected_count = recommendation_feedback_scope_aggregates.rejected_count + CASE WHEN :outcome = 'rejected' THEN 1 ELSE 0 END,
                    skipped_count = recommendation_feedback_scope_aggregates.skipped_count + CASE WHEN :outcome = 'skipped' THEN 1 ELSE 0 END,
                    favorited_count = recommendation_feedback_scope_aggregates.favorited_count + CASE WHEN :outcome = 'favorited' THEN 1 ELSE 0 END,
                    replacement_reason_counts = recommendation_feedback_scope_aggregates.replacement_reason_counts ||
                        CASE WHEN :replacementReason IS NULL THEN '{}'::jsonb
                             ELSE jsonb_build_object(:replacementReason,
                                COALESCE((recommendation_feedback_scope_aggregates.replacement_reason_counts ->> :replacementReason)::int, 0) + 1)
                        END,
                    last_feedback_at = GREATEST(recommendation_feedback_scope_aggregates.last_feedback_at, :createdAt),
                    updated_at = NOW()
                """, new MapSqlParameterSource()
                .addValue("scopeLayer", event.scopeLayer())
                .addValue("scopeId", event.scopeId())
                .addValue("outcome", event.outcome())
                .addValue("replacementReason", event.replacementReason())
                .addValue("createdAt", Timestamp.from(event.createdAt())));
    }

    @Override
    public List<FeedbackEventRecord> listEvents(String scopeLayer, UUID scopeId, UUID arrangementId) {
        return jdbcTemplate.query("""
                SELECT * FROM recommendation_feedback_events
                WHERE (:scopeLayer IS NULL OR scope_layer = :scopeLayer)
                  AND (:scopeId IS NULL OR scope_id = :scopeId)
                  AND (:arrangementId IS NULL OR arrangement_id = :arrangementId)
                ORDER BY created_at DESC, id DESC
                """, Map.of("scopeLayer", scopeLayer, "scopeId", scopeId, "arrangementId", arrangementId),
                (rs, rowNum) -> mapEvent(rs));
    }

    @Override
    public Optional<FeedbackScopeAggregate> getScopeAggregate(String scopeLayer, UUID scopeId) {
        List<FeedbackScopeAggregate> rows = jdbcTemplate.query("""
                SELECT * FROM recommendation_feedback_scope_aggregates
                WHERE scope_layer = :scopeLayer AND scope_id = :scopeId
                """, Map.of("scopeLayer", scopeLayer, "scopeId", scopeId), (rs, rowNum) -> mapAggregate(rs));
        return rows.stream().findFirst();
    }

    @Override
    @Transactional
    public FeedbackResetResult resetScope(String scopeLayer, UUID scopeId, String actorId) {
        jdbcTemplate.update("DELETE FROM recommendation_feedback_scope_aggregates WHERE scope_layer = :scopeLayer AND scope_id = :scopeId",
                Map.of("scopeLayer", scopeLayer, "scopeId", scopeId));
        Instant resetAt = Instant.now();
        return new FeedbackResetResult(scopeLayer, scopeId, actorId, resetAt, "feedback-reset:" + scopeLayer + ":" + scopeId);
    }

    private FeedbackEventRecord mapEvent(ResultSet rs) throws SQLException {
        return new FeedbackEventRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("setlist_id", UUID.class),
                rs.getObject("setlist_version_id", UUID.class),
                rs.getObject("arrangement_id", UUID.class),
                rs.getString("outcome"),
                rs.getString("scope_layer"),
                rs.getObject("scope_id", UUID.class),
                rs.getString("actor_id"),
                rs.getString("replacement_reason"),
                rs.getObject("replaced_with_arrangement_id", UUID.class),
                rs.getObject("familiarity_score", Integer.class),
                rs.getTimestamp("created_at").toInstant());
    }

    private FeedbackScopeAggregate mapAggregate(ResultSet rs) throws SQLException {
        try {
            return new FeedbackScopeAggregate(
                    rs.getString("scope_layer"),
                    rs.getObject("scope_id", UUID.class),
                    rs.getInt("accepted_count"),
                    rs.getInt("rejected_count"),
                    rs.getInt("skipped_count"),
                    rs.getInt("favorited_count"),
                    objectMapper.readValue(rs.getString("replacement_reason_counts"), COUNTS_TYPE),
                    rs.getTimestamp("last_feedback_at") == null ? null : rs.getTimestamp("last_feedback_at").toInstant());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse aggregate replacement reason counts", e);
        }
    }
}
