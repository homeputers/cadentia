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

    private void updateAggregate(FeedbackEventRecord event) {
        int acceptedIncrement = "accepted".equals(event.outcome()) ? 1 : 0;
        int rejectedIncrement = "rejected".equals(event.outcome()) ? 1 : 0;
        int skippedIncrement = "skipped".equals(event.outcome()) ? 1 : 0;
        int favoritedIncrement = "favorited".equals(event.outcome()) ? 1 : 0;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("scopeLayer", event.scopeLayer())
                .addValue("scopeId", event.scopeId())
                .addValue("acceptedIncrement", acceptedIncrement)
                .addValue("rejectedIncrement", rejectedIncrement)
                .addValue("skippedIncrement", skippedIncrement)
                .addValue("favoritedIncrement", favoritedIncrement)
                .addValue("createdAt", Timestamp.from(event.createdAt()));

        if (event.replacementReason() == null) {
            jdbcTemplate.update("""
                    INSERT INTO recommendation_feedback_scope_aggregates (
                        scope_layer, scope_id, accepted_count, rejected_count, skipped_count, favorited_count,
                        replacement_reason_counts, last_feedback_at, updated_at
                    ) VALUES (
                        :scopeLayer, :scopeId,
                        :acceptedIncrement, :rejectedIncrement, :skippedIncrement, :favoritedIncrement,
                        '{}'::jsonb, :createdAt, NOW()
                    ) ON CONFLICT (scope_layer, scope_id) DO UPDATE SET
                        accepted_count = recommendation_feedback_scope_aggregates.accepted_count + :acceptedIncrement,
                        rejected_count = recommendation_feedback_scope_aggregates.rejected_count + :rejectedIncrement,
                        skipped_count = recommendation_feedback_scope_aggregates.skipped_count + :skippedIncrement,
                        favorited_count = recommendation_feedback_scope_aggregates.favorited_count + :favoritedIncrement,
                        last_feedback_at = GREATEST(
                            COALESCE(recommendation_feedback_scope_aggregates.last_feedback_at, :createdAt),
                            :createdAt
                        ),
                        updated_at = NOW()
                    """, params);
            return;
        }

        params.addValue("replacementReason", event.replacementReason());
        jdbcTemplate.update("""
                INSERT INTO recommendation_feedback_scope_aggregates (
                    scope_layer, scope_id, accepted_count, rejected_count, skipped_count, favorited_count,
                    replacement_reason_counts, last_feedback_at, updated_at
                ) VALUES (
                    :scopeLayer, :scopeId,
                    :acceptedIncrement, :rejectedIncrement, :skippedIncrement, :favoritedIncrement,
                    jsonb_build_object(CAST(:replacementReason AS text), 1),
                    :createdAt, NOW()
                ) ON CONFLICT (scope_layer, scope_id) DO UPDATE SET
                    accepted_count = recommendation_feedback_scope_aggregates.accepted_count + :acceptedIncrement,
                    rejected_count = recommendation_feedback_scope_aggregates.rejected_count + :rejectedIncrement,
                    skipped_count = recommendation_feedback_scope_aggregates.skipped_count + :skippedIncrement,
                    favorited_count = recommendation_feedback_scope_aggregates.favorited_count + :favoritedIncrement,
                    replacement_reason_counts = jsonb_set(
                        recommendation_feedback_scope_aggregates.replacement_reason_counts,
                        ARRAY[CAST(:replacementReason AS text)],
                        to_jsonb(COALESCE((recommendation_feedback_scope_aggregates.replacement_reason_counts ->> CAST(:replacementReason AS text))::int, 0) + 1),
                        true
                    ),
                    last_feedback_at = GREATEST(
                        COALESCE(recommendation_feedback_scope_aggregates.last_feedback_at, :createdAt),
                        :createdAt
                    ),
                    updated_at = NOW()
                """, params);
    }
}
