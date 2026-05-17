package com.cadentia.reng;

import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.catalog.model.KeyMode;
import com.cadentia.catalog.model.TagType;
import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCandidateRetriever implements CandidateRetriever {

    private static final String CANDIDATE_COLUMNS = "arrangement_id, song_id, current_lyrics_document_id, title, "
            + "language, musical_key, key_mode, bpm, time_signature, energy, tags, song_doctrinal_status, "
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
        if (criteria.minEnergy() != null) {
            sql.append(" AND energy >= :minEnergy");
            params.addValue("minEnergy", criteria.minEnergy());
        }
        if (criteria.maxEnergy() != null) {
            sql.append(" AND energy <= :maxEnergy");
            params.addValue("maxEnergy", criteria.maxEnergy());
        }
        if (criteria.requiredApprovalStatus() != null) {
            appendApprovalStatusFilter(sql, params, criteria.requiredApprovalStatus());
        }
        if (!criteria.includeAnyTags().isEmpty()) {
            appendIncludeAnyTagFilter(sql, params, criteria.includeAnyTags());
        }
        if (!criteria.includeAllTags().isEmpty()) {
            appendIncludeAllTagFilters(sql, params, criteria.includeAllTags());
        }

        sql.append(" ORDER BY title, arrangement_id");
        List<RecommendableArrangement> candidates = jdbcTemplate.query(sql.toString(), params, candidateMapper());
        return enrichWithControlledTags(candidates, criteria);
    }

    private static void appendApprovalStatusFilter(
            StringBuilder sql, MapSqlParameterSource params, ApprovalStatus requiredApprovalStatus) {
        sql.append(" AND song_doctrinal_status = :requiredApprovalStatus")
                .append(" AND song_editorial_status = :requiredApprovalStatus")
                .append(" AND song_licensing_status = :requiredApprovalStatus")
                .append(" AND arrangement_musical_status = :requiredApprovalStatus")
                .append(" AND arrangement_editorial_status = :requiredApprovalStatus")
                .append(" AND lyrics_doctrinal_status = :requiredApprovalStatus")
                .append(" AND lyrics_editorial_status = :requiredApprovalStatus")
                .append(" AND lyrics_licensing_status = :requiredApprovalStatus");
        params.addValue("requiredApprovalStatus", requiredApprovalStatus.name());
    }

    private static void appendIncludeAnyTagFilter(
            StringBuilder sql, MapSqlParameterSource params, List<TagFilter> tagFilters) {
        sql.append(" AND EXISTS (SELECT 1 FROM v_recommendable_arrangement_tags candidate_tags ")
                .append("WHERE candidate_tags.arrangement_id = v_recommendable_arrangements.arrangement_id AND (");
        for (int i = 0; i < tagFilters.size(); i++) {
            if (i > 0) {
                sql.append(" OR ");
            }
            appendTagPredicate(sql, params, tagFilters.get(i), "includeAnyTag" + i);
        }
        sql.append("))");
    }

    private static void appendIncludeAllTagFilters(
            StringBuilder sql, MapSqlParameterSource params, List<TagFilter> tagFilters) {
        for (int i = 0; i < tagFilters.size(); i++) {
            sql.append(" AND EXISTS (SELECT 1 FROM v_recommendable_arrangement_tags candidate_tags ")
                    .append("WHERE candidate_tags.arrangement_id = v_recommendable_arrangements.arrangement_id AND ");
            appendTagPredicate(sql, params, tagFilters.get(i), "includeAllTag" + i);
            sql.append(')');
        }
    }

    private static void appendTagPredicate(
            StringBuilder sql, MapSqlParameterSource params, TagFilter tagFilter, String prefix) {
        String typeParam = prefix + "Type";
        sql.append("candidate_tags.tag_type = :").append(typeParam);
        params.addValue(typeParam, tagFilter.tagType().name());
        if (tagFilter.tagId() != null) {
            String idParam = prefix + "Id";
            sql.append(" AND candidate_tags.tag_id = :").append(idParam);
            params.addValue(idParam, tagFilter.tagId());
        } else {
            String slugParam = prefix + "Slug";
            sql.append(" AND candidate_tags.tag_slug = :").append(slugParam);
            params.addValue(slugParam, tagFilter.slug());
        }
    }

    private List<RecommendableArrangement> enrichWithControlledTags(
            List<RecommendableArrangement> candidates,
            CandidateSearchCriteria criteria) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<UUID> arrangementIds = candidates.stream()
                .map(RecommendableArrangement::arrangementId)
                .toList();
        Map<UUID, List<RecommendationTag>> tagsByArrangementId = jdbcTemplate.query(
                        "SELECT arrangement_id, tag_id, tag_type, tag_name, tag_slug "
                                + "FROM v_recommendable_arrangement_tags "
                                + "WHERE arrangement_id IN (:arrangementIds) "
                                + "ORDER BY arrangement_id, tag_type, sort_order, tag_slug, tag_id",
                        new MapSqlParameterSource("arrangementIds", arrangementIds),
                        recommendationTagRowMapper())
                .stream()
                .collect(Collectors.groupingBy(
                        RecommendationTagRow::arrangementId,
                        Collectors.mapping(RecommendationTagRow::tag, Collectors.toList())));
        List<TagFilter> requestedTagFilters = requestedTagFilters(criteria);
        return candidates.stream()
                .map(candidate -> {
                    List<RecommendationTag> controlledTags = tagsByArrangementId.getOrDefault(
                            candidate.arrangementId(), List.of());
                    List<RecommendationTag> matchedTags = controlledTags.stream()
                            .filter(tag -> requestedTagFilters.stream().anyMatch(filter -> filter.matches(tag)))
                            .toList();
                    return candidate.withRecommendationTags(controlledTags, matchedTags);
                })
                .toList();
    }

    private static List<TagFilter> requestedTagFilters(CandidateSearchCriteria criteria) {
        return java.util.stream.Stream.of(criteria.includeAnyTags(), criteria.includeAllTags())
                .flatMap(Collection::stream)
                .collect(Collectors.toMap(
                        filter -> filter.tagType() + ":" + (filter.tagId() == null ? filter.slug() : filter.tagId()),
                        Function.identity(),
                        (first, ignored) -> first))
                .values()
                .stream()
                .toList();
    }

    private static RowMapper<RecommendableArrangement> candidateMapper() {
        return (rs, rowNum) -> new RecommendableArrangement(
                rs.getObject("arrangement_id", UUID.class),
                rs.getObject("song_id", UUID.class),
                rs.getObject("current_lyrics_document_id", UUID.class),
                rs.getString("title"),
                rs.getString("language"),
                rs.getString("musical_key"),
                KeyMode.valueOf(rs.getString("key_mode")),
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

    private static RowMapper<RecommendationTagRow> recommendationTagRowMapper() {
        return (rs, rowNum) -> new RecommendationTagRow(
                rs.getObject("arrangement_id", UUID.class),
                new RecommendationTag(
                        rs.getObject("tag_id", UUID.class),
                        TagType.valueOf(rs.getString("tag_type")),
                        rs.getString("tag_name"),
                        rs.getString("tag_slug")));
    }

    private static List<String> textArray(Array sqlArray) throws SQLException {
        if (sqlArray == null) {
            return List.of();
        }
        String[] values = (String[]) sqlArray.getArray();
        return List.copyOf(Arrays.asList(values));
    }

    private record RecommendationTagRow(UUID arrangementId, RecommendationTag tag) {
    }
}
