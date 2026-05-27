package com.cadentia.serviceplan;

import com.cadentia.serviceplan.ServicePlanModels.ServicePlanBlock;
import com.cadentia.serviceplan.ServicePlanModels.ServicePlanRecord;
import com.cadentia.serviceplan.ServicePlanModels.ServicePlanStatus;
import com.cadentia.serviceplan.ServicePlanModels.SetlistAttachment;
import java.sql.ResultSet;
import java.sql.SQLException;
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
public class JdbcServicePlanRepository implements ServicePlanRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcServicePlanRepository(NamedParameterJdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Override @Transactional
    public ServicePlanRecord create(Instant serviceDateTime, String title, String theme, String scripture, String notes) {
        UUID id = jdbcTemplate.queryForObject("""
                INSERT INTO service_plans (service_date_time, title, theme, scripture, notes)
                VALUES (:serviceDateTime, :title, :theme, :scripture, :notes)
                RETURNING id
                """, new MapSqlParameterSource()
                .addValue("serviceDateTime", serviceDateTime)
                .addValue("title", title).addValue("theme", theme).addValue("scripture", scripture).addValue("notes", notes), UUID.class);
        return findById(id).orElseThrow();
    }

    @Override public List<ServicePlanRecord> list() {
        return jdbcTemplate.query("SELECT id FROM service_plans ORDER BY service_date_time DESC", (rs, n) -> rs.getObject("id", UUID.class))
                .stream().map(this::getRequired).toList();
    }
    @Override public Optional<ServicePlanRecord> findById(UUID servicePlanId) {
        List<ServicePlanRecord> rows = jdbcTemplate.query("SELECT * FROM service_plans WHERE id = :id", Map.of("id", servicePlanId), (rs, n) -> mapPlan(rs));
        return rows.stream().findFirst();
    }
    @Override @Transactional
    public ServicePlanRecord updateMetadata(UUID id, Instant serviceDateTime, String title, String theme, String scripture, String notes) {
        jdbcTemplate.update("""
                UPDATE service_plans
                SET service_date_time = :serviceDateTime, title = :title, theme = :theme, scripture = :scripture, notes = :notes, updated_at = NOW()
                WHERE id = :id
                """, new MapSqlParameterSource().addValue("id", id).addValue("serviceDateTime", serviceDateTime).addValue("title", title)
                .addValue("theme", theme).addValue("scripture", scripture).addValue("notes", notes));
        return getRequired(id);
    }
    @Override @Transactional
    public ServicePlanRecord reorderBlocks(UUID id, List<UUID> blockIds) {
        int pos = 0;
        for (UUID blockId : blockIds) {
            jdbcTemplate.update("UPDATE service_plan_blocks SET position_index=:pos WHERE id=:id AND service_plan_id=:servicePlanId",
                    new MapSqlParameterSource().addValue("pos", pos++).addValue("id", blockId).addValue("servicePlanId", id));
        }
        return getRequired(id);
    }
    @Override @Transactional
    public ServicePlanRecord attachSetlistVersion(UUID id, UUID setlistId, UUID setlistVersionId) {
        Integer nextOrder = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(attachment_order), -1) + 1 FROM service_plan_setlist_attachments WHERE service_plan_id = :id", Map.of("id", id), Integer.class);
        jdbcTemplate.update("""
                INSERT INTO service_plan_setlist_attachments (service_plan_id, setlist_id, setlist_version_id, attachment_order)
                VALUES (:servicePlanId, :setlistId, :setlistVersionId, :attachmentOrder)
                """, new MapSqlParameterSource().addValue("servicePlanId", id).addValue("setlistId", setlistId)
                .addValue("setlistVersionId", setlistVersionId).addValue("attachmentOrder", nextOrder));
        return getRequired(id);
    }
    @Override @Transactional
    public ServicePlanRecord publish(UUID id, String publishedBy, String publishNote) {
        jdbcTemplate.update("UPDATE service_plans SET status='published', published_at=NOW(), published_by=:publishedBy, publish_note=:publishNote, updated_at=NOW() WHERE id=:id",
                new MapSqlParameterSource().addValue("id", id).addValue("publishedBy", publishedBy).addValue("publishNote", publishNote));
        jdbcTemplate.update("""
                INSERT INTO service_plan_snapshots (service_plan_id, status, snapshot_payload)
                SELECT :id, 'published', jsonb_build_object('blockCount', COUNT(*), 'capturedAt', NOW())
                FROM service_plan_blocks WHERE service_plan_id = :id
                """, Map.of("id", id));
        return getRequired(id);
    }

    private ServicePlanRecord getRequired(UUID id) { return findById(id).orElseThrow(); }
    private ServicePlanRecord mapPlan(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        return new ServicePlanRecord(id, rs.getTimestamp("service_date_time").toInstant(), rs.getString("title"), rs.getString("theme"), rs.getString("scripture"), rs.getString("notes"),
                ServicePlanStatus.valueOf(rs.getString("status").toUpperCase()),
                rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toInstant(), rs.getString("published_by"), listBlocks(id), listAttachments(id));
    }
    private List<ServicePlanBlock> listBlocks(UUID id) {
        return jdbcTemplate.query("SELECT * FROM service_plan_blocks WHERE service_plan_id = :id ORDER BY position_index", Map.of("id", id), (rs, n) ->
                new ServicePlanBlock(rs.getObject("id", UUID.class), rs.getString("block_type"), rs.getInt("position_index"), rs.getObject("arrangement_id", UUID.class),
                        rs.getString("service_notes"), rs.getString("override_key"), rs.getString("override_mode"), rs.getObject("source_setlist_version_id", UUID.class), rs.getObject("source_setlist_item_id", UUID.class)));
    }
    private List<SetlistAttachment> listAttachments(UUID id) {
        return jdbcTemplate.query("SELECT * FROM service_plan_setlist_attachments WHERE service_plan_id = :id ORDER BY attachment_order", Map.of("id", id), (rs, n) ->
                new SetlistAttachment(rs.getObject("id", UUID.class), rs.getObject("setlist_id", UUID.class), rs.getObject("setlist_version_id", UUID.class), rs.getInt("attachment_order")));
    }
}
