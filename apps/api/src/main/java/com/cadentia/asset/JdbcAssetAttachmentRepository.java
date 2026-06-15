package com.cadentia.asset;

import com.cadentia.asset.AssetModels.ArchiveAssetAttachmentCommand;
import com.cadentia.asset.AssetModels.AssetAccessPolicyCode;
import com.cadentia.asset.AssetModels.AssetAttachmentAuditEventRecord;
import com.cadentia.asset.AssetModels.AssetAttachmentAuditEventType;
import com.cadentia.asset.AssetModels.AssetAttachmentPurposeCode;
import com.cadentia.asset.AssetModels.AssetAttachmentRecord;
import com.cadentia.asset.AssetModels.AssetAttachmentTargetTypeCode;
import com.cadentia.asset.AssetModels.AssetTypeCode;
import com.cadentia.asset.AssetModels.CreateAssetAttachmentCommand;
import com.cadentia.asset.AssetModels.ReorderAssetAttachmentCommand;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcAssetAttachmentRepository implements AssetAttachmentRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcAssetAttachmentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public AssetAttachmentRecord createAttachment(CreateAssetAttachmentCommand command) {
        UUID id = jdbcTemplate.queryForObject(
                """
                INSERT INTO asset_attachments (
                    target_type_code, target_id, service_plan_id, asset_version_id, attachment_type_code,
                    display_label, sort_order, purpose_code, required_for_use, effective_from,
                    effective_until, visibility_policy_code, created_by, updated_by
                ) VALUES (
                    :targetTypeCode, :targetId, :servicePlanId, :assetVersionId, :attachmentTypeCode,
                    :displayLabel, :sortOrder, :purposeCode, :requiredForUse, :effectiveFrom,
                    :effectiveUntil, :visibilityPolicyCode, :createdBy, :createdBy
                )
                RETURNING id
                """,
                params(command),
                UUID.class);
        audit(id, "CREATED", null, command.assetVersionId(), null, command.sortOrder(), null, command.createdBy());
        return find(id);
    }

    @Override
    public List<AssetAttachmentRecord> listAttachments(AssetAttachmentTargetTypeCode targetTypeCode, UUID targetId) {
        return jdbcTemplate.query(
                """
                SELECT *
                FROM asset_attachments
                WHERE target_type_code = :targetTypeCode
                  AND target_id = :targetId
                ORDER BY archived_at NULLS FIRST, purpose_code ASC, sort_order ASC, created_at ASC
                """,
                Map.of("targetTypeCode", targetTypeCode.name(), "targetId", targetId),
                (rs, rowNum) -> mapAttachment(rs));
    }

    @Override
    @Transactional
    public AssetAttachmentRecord reorderAttachment(ReorderAssetAttachmentCommand command) {
        AssetAttachmentRecord previous = find(command.attachmentId());
        jdbcTemplate.update(
                """
                UPDATE asset_attachments
                SET sort_order = :sortOrder,
                    updated_by = :updatedBy
                WHERE id = :attachmentId
                """,
                new MapSqlParameterSource()
                        .addValue("sortOrder", command.sortOrder())
                        .addValue("updatedBy", command.updatedBy())
                        .addValue("attachmentId", command.attachmentId()));
        audit(command.attachmentId(), "REORDERED", null, null, previous.sortOrder(), command.sortOrder(),
                command.reason(), command.updatedBy());
        return find(command.attachmentId());
    }

    @Override
    @Transactional
    public AssetAttachmentRecord archiveAttachment(ArchiveAssetAttachmentCommand command) {
        jdbcTemplate.update(
                """
                UPDATE asset_attachments
                SET archived_at = NOW(),
                    archived_by = :archivedBy,
                    archive_reason = :reason,
                    updated_by = :archivedBy
                WHERE id = :attachmentId
                """,
                new MapSqlParameterSource()
                        .addValue("archivedBy", command.archivedBy())
                        .addValue("reason", command.reason())
                        .addValue("attachmentId", command.attachmentId()));
        audit(command.attachmentId(), "ARCHIVED", null, null, null, null, command.reason(), command.archivedBy());
        return find(command.attachmentId());
    }

    @Override
    public List<AssetAttachmentAuditEventRecord> listAuditEvents(UUID attachmentId) {
        return jdbcTemplate.query(
                """
                SELECT *
                FROM asset_attachment_audit_events
                WHERE attachment_id = :attachmentId
                ORDER BY changed_at ASC, id ASC
                """,
                Map.of("attachmentId", attachmentId),
                (rs, rowNum) -> mapAuditEvent(rs));
    }

    private AssetAttachmentRecord find(UUID id) {
        return jdbcTemplate.query(
                        "SELECT * FROM asset_attachments WHERE id = :id",
                        Map.of("id", id),
                        (rs, rowNum) -> mapAttachment(rs))
                .stream()
                .findFirst()
                .orElseThrow();
    }

    private MapSqlParameterSource params(CreateAssetAttachmentCommand command) {
        return new MapSqlParameterSource()
                .addValue("targetTypeCode", command.targetTypeCode().name())
                .addValue("targetId", command.targetId())
                .addValue("servicePlanId", command.servicePlanId())
                .addValue("assetVersionId", command.assetVersionId())
                .addValue("attachmentTypeCode", command.attachmentTypeCode().name())
                .addValue("displayLabel", command.displayLabel())
                .addValue("sortOrder", command.sortOrder())
                .addValue("purposeCode", command.purposeCode().name())
                .addValue("requiredForUse", command.requiredForUse())
                .addValue("effectiveFrom", timestamp(command.effectiveFrom()))
                .addValue("effectiveUntil", timestamp(command.effectiveUntil()))
                .addValue("visibilityPolicyCode", command.visibilityPolicyCode().name())
                .addValue("createdBy", command.createdBy());
    }

    private void audit(
            UUID attachmentId,
            String eventType,
            UUID previousVersionId,
            UUID newVersionId,
            Integer previousSortOrder,
            Integer newSortOrder,
            String reason,
            String changedBy) {
        jdbcTemplate.update(
                """
                INSERT INTO asset_attachment_audit_events (
                    attachment_id, event_type, previous_asset_version_id, new_asset_version_id,
                    previous_sort_order, new_sort_order, reason, changed_by
                ) VALUES (
                    :attachmentId, :eventType, :previousVersionId, :newVersionId,
                    :previousSortOrder, :newSortOrder, :reason, :changedBy
                )
                """,
                new MapSqlParameterSource()
                        .addValue("attachmentId", attachmentId)
                        .addValue("eventType", eventType)
                        .addValue("previousVersionId", previousVersionId)
                        .addValue("newVersionId", newVersionId)
                        .addValue("previousSortOrder", previousSortOrder)
                        .addValue("newSortOrder", newSortOrder)
                        .addValue("reason", reason)
                        .addValue("changedBy", changedBy));
    }

    private AssetAttachmentRecord mapAttachment(ResultSet rs) throws SQLException {
        return new AssetAttachmentRecord(
                uuid(rs, "id"),
                AssetAttachmentTargetTypeCode.valueOf(rs.getString("target_type_code")),
                uuid(rs, "target_id"),
                uuid(rs, "service_plan_id"),
                uuid(rs, "asset_version_id"),
                AssetTypeCode.valueOf(rs.getString("attachment_type_code")),
                rs.getString("display_label"),
                rs.getInt("sort_order"),
                AssetAttachmentPurposeCode.valueOf(rs.getString("purpose_code")),
                rs.getBoolean("required_for_use"),
                instant(rs, "effective_from"),
                instant(rs, "effective_until"),
                AssetAccessPolicyCode.valueOf(rs.getString("visibility_policy_code")),
                instant(rs, "archived_at"),
                rs.getString("archived_by"),
                rs.getString("archive_reason"),
                rs.getString("created_by"),
                rs.getString("updated_by"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private AssetAttachmentAuditEventRecord mapAuditEvent(ResultSet rs) throws SQLException {
        return new AssetAttachmentAuditEventRecord(
                uuid(rs, "id"),
                uuid(rs, "attachment_id"),
                AssetAttachmentAuditEventType.valueOf(rs.getString("event_type")),
                uuid(rs, "previous_asset_version_id"),
                uuid(rs, "new_asset_version_id"),
                integer(rs, "previous_sort_order"),
                integer(rs, "new_sort_order"),
                rs.getString("reason"),
                rs.getString("changed_by"),
                instant(rs, "changed_at"));
    }

    private UUID uuid(ResultSet rs, String column) throws SQLException {
        return (UUID) rs.getObject(column);
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private Integer integer(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
