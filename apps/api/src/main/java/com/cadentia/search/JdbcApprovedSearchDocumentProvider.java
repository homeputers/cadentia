package com.cadentia.search;

import com.cadentia.search.ApprovedSearchModels.ApprovedSearchDocument;
import com.cadentia.search.ApprovedSearchModels.TagFacet;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcApprovedSearchDocumentProvider implements ApprovedSearchDocumentProvider {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcApprovedSearchDocumentProvider(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ApprovedSearchDocument> documents() {
        String sql = """
                SELECT
                    songs.id AS song_id,
                    arrangements.id AS arrangement_id,
                    songs.canonical_title,
                    songs.original_artist_display,
                    songs.composer_credits,
                    arrangements.musical_key,
                    arrangements.tempo_bpm,
                    arrangements.name AS arrangement_name,
                    arrangements.updated_at AS arrangement_updated_at,
                    songs.updated_at AS song_updated_at,
                    COALESCE(string_agg(DISTINCT tags.slug, ',' ORDER BY tags.slug), '') AS tag_slugs
                FROM songs
                JOIN arrangements
                    ON arrangements.song_id = songs.id
                    AND arrangements.is_active = true
                    AND arrangements.default_for_song = true
                JOIN approval_records editorial_approval
                    ON editorial_approval.song_id = songs.id
                    AND editorial_approval.approval_type = 'EDITORIAL'
                    AND editorial_approval.status = 'APPROVED'
                JOIN approval_records licensing_approval
                    ON licensing_approval.song_id = songs.id
                    AND licensing_approval.approval_type = 'LICENSING'
                    AND licensing_approval.status = 'APPROVED'
                LEFT JOIN song_tags
                    ON song_tags.song_id = songs.id
                LEFT JOIN tags
                    ON tags.id = song_tags.tag_id
                    AND tags.is_active = true
                WHERE songs.song_status <> 'ARCHIVED'
                GROUP BY
                    songs.id,
                    arrangements.id
                ORDER BY songs.canonical_title, arrangements.name
                """;
        return jdbcTemplate.query(sql, Map.of(), this::document);
    }

    private ApprovedSearchDocument document(ResultSet rs, int rowNumber) throws SQLException {
        return new ApprovedSearchDocument(
                rs.getObject("song_id", UUID.class),
                rs.getObject("arrangement_id", UUID.class),
                null,
                rs.getString("canonical_title"),
                List.of(),
                List.of(),
                tags(rs.getString("tag_slugs")),
                contributors(rs.getString("original_artist_display"), rs.getString("composer_credits")),
                rs.getString("musical_key"),
                integerOrNull(rs, "tempo_bpm"),
                rs.getString("arrangement_name"),
                List.of(rs.getString("arrangement_name")),
                List.of(),
                true,
                true,
                true,
                true,
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                latest(rs.getTimestamp("song_updated_at").toInstant(), rs.getTimestamp("arrangement_updated_at").toInstant()),
                false);
    }

    private static Integer integerOrNull(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static List<String> contributors(String... values) {
        return Arrays.stream(values)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static List<TagFacet> tags(String tagSlugs) {
        if (tagSlugs == null || tagSlugs.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tagSlugs.split(","))
                .filter(value -> !value.isBlank())
                .map(value -> new TagFacet(value, value))
                .toList();
    }

    private static Instant latest(Instant first, Instant second) {
        return first.isAfter(second) ? first : second;
    }
}
