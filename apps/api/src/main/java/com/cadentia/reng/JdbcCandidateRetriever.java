package com.cadentia.reng;

import com.cadentia.catalog.model.ApprovalStatus;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlTypeValue;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCandidateRetriever implements CandidateRetriever {

    private static final String CANDIDATE_COLUMNS = "arrangement_id, song_id, current_lyrics_document_id, title, "
            + "language, musical_key, bpm, time_signature, energy, tags, song_doctrinal_status, "
            + "song_editorial_status, song_licensing_status, arrangement_musical_status, "
            + "arrangement_editorial_status, lyrics_doctrinal_status, lyrics_editorial_status, "
            + "lyrics_licensing_status";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcCandidateRetriever(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<RecommendableArrangement> findCandidates(CandidateSearchCriteria criteria) {
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(CANDIDATE_COLUMNS)
                .append(" FROM v_recommendable_arrangements WHERE 1 = 1");
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (criteria.language() != null && !criteria.language().isBlank()) {
            sql.append(" AND language = :language");
            params.addValue("language", criteria.language());
        }
        if (criteria.allowedKeys() != null && !criteria.allowedKeys().isEmpty()) {
            sql.append(" AND musical_key IN (:allowedKeys)");
            params.addValue("allowedKeys", criteria.allowedKeys());
        }
        if (criteria.minBpm() != null) {
            sql.append(" AND bpm >= :minBpm");
            params.addValue("minBpm", criteria.minBpm());
        }
        if (criteria.maxBpm() != null) {
            sql.append(" AND bpm <= :maxBpm");
            params.addValue("maxBpm", criteria.maxBpm());
        }
        if (criteria.requiredTags() != null && !criteria.requiredTags().isEmpty()) {
            sql.append(" AND tags @> :requiredTags");
            params.addValue("requiredTags", new TextArrayValue(criteria.requiredTags()));
        }

        sql.append(" ORDER BY title, arrangement_id");
        return jdbcTemplate.query(sql.toString(), params, candidateMapper());
    }

    private static RowMapper<RecommendableArrangement> candidateMapper() {
        return (rs, rowNum) -> new RecommendableArrangement(
                rs.getObject("arrangement_id", UUID.class),
                rs.getObject("song_id", UUID.class),
                rs.getObject("current_lyrics_document_id", UUID.class),
                rs.getString("title"),
                rs.getString("language"),
                rs.getString("musical_key"),
                rs.getInt("bpm"),
                rs.getString("time_signature"),
                rs.getInt("energy"),
                textArray(rs.getArray("tags")),
                new ApprovalGateSummary(
                        ApprovalStatus.valueOf(rs.getString("song_doctrinal_status")),
                        ApprovalStatus.valueOf(rs.getString("song_editorial_status")),
                        ApprovalStatus.valueOf(rs.getString("song_licensing_status")),
                        ApprovalStatus.valueOf(rs.getString("arrangement_musical_status")),
                        ApprovalStatus.valueOf(rs.getString("arrangement_editorial_status")),
                        ApprovalStatus.valueOf(rs.getString("lyrics_doctrinal_status")),
                        ApprovalStatus.valueOf(rs.getString("lyrics_editorial_status")),
                        ApprovalStatus.valueOf(rs.getString("lyrics_licensing_status"))));
    }

    private static List<String> textArray(Array sqlArray) throws SQLException {
        if (sqlArray == null) {
            return List.of();
        }
        String[] values = (String[]) sqlArray.getArray();
        return List.copyOf(Arrays.asList(values));
    }

    private static final class TextArrayValue implements SqlTypeValue {

        private final List<String> values;

        private TextArrayValue(List<String> values) {
            this.values = List.copyOf(values);
        }

        @Override
        public void setTypeValue(PreparedStatement ps, int paramIndex, int sqlType, String typeName)
                throws SQLException {
            Connection connection = ps.getConnection();
            Array sqlArray = connection.createArrayOf("text", new ArrayList<>(values).toArray());
            ps.setArray(paramIndex, sqlArray);
        }
    }
}
