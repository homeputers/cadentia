package com.cadentia.reng;

import com.cadentia.catalog.model.TagType;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcTagReportingRepository implements TagReportingRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcTagReportingRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<TagUsageReportRow> findRecommendableArrangementTagUsage() {
        return jdbcTemplate.query(
                "SELECT tag_id, tag_type, tag_slug, tag_name, COUNT(DISTINCT arrangement_id) AS arrangement_count "
                        + "FROM v_recommendable_arrangement_tags "
                        + "GROUP BY tag_id, tag_type, tag_slug, tag_name, sort_order "
                        + "ORDER BY tag_type, sort_order, tag_slug, tag_id",
                tagUsageMapper());
    }

    private static RowMapper<TagUsageReportRow> tagUsageMapper() {
        return (rs, rowNum) -> new TagUsageReportRow(
                rs.getObject("tag_id", java.util.UUID.class),
                TagType.valueOf(rs.getString("tag_type")),
                rs.getString("tag_slug"),
                rs.getString("tag_name"),
                rs.getInt("arrangement_count"));
    }
}
