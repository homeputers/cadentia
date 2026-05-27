package com.cadentia.reng.setlist;

import static com.cadentia.reng.setlist.SetlistVersionModels.CreateSetlistBaselineCommand;
import static com.cadentia.reng.setlist.SetlistVersionModels.CreateSetlistItemCommand;
import static com.cadentia.reng.setlist.SetlistVersionModels.CreateSetlistVersionCommand;
import static com.cadentia.reng.setlist.SetlistVersionModels.SetlistEditEventCommand;
import static com.cadentia.reng.setlist.SetlistVersionModels.SetlistVersionItemSnapshot;
import static com.cadentia.reng.setlist.SetlistVersionModels.SetlistVersionSnapshot;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcSetlistVersionRepository implements SetlistVersionRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcSetlistVersionRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public SetlistVersionSnapshot createBaseline(CreateSetlistBaselineCommand command) {
        UUID setlistId = jdbcTemplate.queryForObject(
                """
                        INSERT INTO setlists (lineage_policy, created_by)
                        VALUES (:lineagePolicy, :createdBy)
                        RETURNING id
                        """,
                Map.of("lineagePolicy", command.lineagePolicy(), "createdBy", command.createdBy()),
                (rs, rowNum) -> uuid(rs, "id"));
        UUID versionId = insertVersion(setlistId, null, 1, "GENERATED_BASELINE", command.createdBy(),
                command.scoringProfileVersion(), command.engineVersion(), command.requestPayload(),
                command.parsedIntentPayload(), command.explanationFactsPayload(), null);
        insertItems(versionId, command.items());
        return getSnapshot(setlistId, versionId).orElseThrow();
    }

    @Override
    @Transactional
    public SetlistVersionSnapshot createEditedVersion(CreateSetlistVersionCommand command) {
        Integer nextVersion = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(version_number), 0) + 1 FROM setlist_versions WHERE setlist_id = :setlistId",
                Map.of("setlistId", command.setlistId()), Integer.class);
        UUID versionId = insertVersion(command.setlistId(), command.parentVersionId(), nextVersion, "MANUAL_EDIT",
                command.createdBy(), command.scoringProfileVersion(), command.engineVersion(), command.requestPayload(),
                command.parsedIntentPayload(), command.explanationFactsPayload(), command.commitSummary());
        insertItems(versionId, command.items());
        UUID commitId = jdbcTemplate.queryForObject(
                """
                        INSERT INTO setlist_edit_commits (
                            setlist_id, base_version_id, created_version_id, committed_by, summary
                        ) VALUES (
                            :setlistId, :baseVersionId, :createdVersionId, :committedBy, :summary
                        ) RETURNING id
                        """,
                Map.of(
                        "setlistId", command.setlistId(),
                        "baseVersionId", command.parentVersionId(),
                        "createdVersionId", versionId,
                        "committedBy", command.createdBy(),
                        "summary", command.commitSummary()),
                (rs, rowNum) -> uuid(rs, "id"));
        insertEditEvents(commitId, command.editEvents());
        return getSnapshot(command.setlistId(), versionId).orElseThrow();
    }

    @Override
    public Optional<SetlistVersionSnapshot> findVersion(UUID setlistId, UUID versionId) {
        return getSnapshot(setlistId, versionId);
    }

    @Override
    public List<SetlistVersionSnapshot> findVersions(UUID setlistId) {
        List<UUID> versionIds = jdbcTemplate.query(
                "SELECT id FROM setlist_versions WHERE setlist_id = :setlistId ORDER BY version_number ASC",
                Map.of("setlistId", setlistId),
                (rs, rowNum) -> uuid(rs, "id"));
        return versionIds.stream().map(id -> getSnapshot(setlistId, id).orElseThrow()).toList();
    }

    private UUID insertVersion(UUID setlistId, UUID parentVersionId, int versionNumber, String provenanceType,
            String createdBy, String scoringProfileVersion, String engineVersion, String requestPayload,
            String parsedIntentPayload, String explanationFactsPayload, String commitSummary) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("setlistId", setlistId)
                .addValue("parentVersionId", parentVersionId)
                .addValue("versionNumber", versionNumber)
                .addValue("provenanceType", provenanceType)
                .addValue("requestPayload", requestPayload)
                .addValue("parsedIntentPayload", parsedIntentPayload)
                .addValue("explanationFacts", explanationFactsPayload)
                .addValue("scoringProfileVersion", scoringProfileVersion)
                .addValue("engineVersion", engineVersion)
                .addValue("createdBy", createdBy)
                .addValue("commitSummary", commitSummary);
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO setlist_versions (
                            setlist_id, parent_version_id, version_number, provenance_type,
                            request_payload, parsed_intent_payload, explanation_facts,
                            scoring_profile_version, engine_version, created_by, commit_summary
                        ) VALUES (
                            :setlistId, :parentVersionId, :versionNumber, :provenanceType,
                            CAST(:requestPayload AS jsonb), CAST(:parsedIntentPayload AS jsonb), CAST(:explanationFacts AS jsonb),
                            :scoringProfileVersion, :engineVersion, :createdBy, :commitSummary
                        ) RETURNING id
                        """,
                params,
                (rs, rowNum) -> uuid(rs, "id"));
    }

    private void insertItems(UUID versionId, List<CreateSetlistItemCommand> items) {
        for (CreateSetlistItemCommand item : items) {
            jdbcTemplate.update(
                    """
                            INSERT INTO setlist_version_items (
                                version_id, position_index, catalog_arrangement_id, transposed_key,
                                transposed_mode, source_item_id, item_provenance, notes
                            ) VALUES (
                                :versionId, :positionIndex, :catalogArrangementId, :transposedKey,
                                :transposedMode, :sourceItemId, :itemProvenance, :notes
                            )
                            """,
                    new MapSqlParameterSource()
                            .addValue("versionId", versionId)
                            .addValue("positionIndex", item.positionIndex())
                            .addValue("catalogArrangementId", item.catalogArrangementId())
                            .addValue("transposedKey", item.transposedKey())
                            .addValue("transposedMode", item.transposedMode())
                            .addValue("sourceItemId", item.sourceItemId())
                            .addValue("itemProvenance", item.itemProvenance())
                            .addValue("notes", item.notes()));
        }
    }

    private void insertEditEvents(UUID commitId, List<SetlistEditEventCommand> editEvents) {
        for (SetlistEditEventCommand event : editEvents) {
            jdbcTemplate.update(
                    """
                            INSERT INTO setlist_edit_events (
                                commit_id, event_index, event_type, item_id, from_position, to_position,
                                replacement_arrangement_id, transpose_to_key, transpose_to_mode, removed, payload
                            ) VALUES (
                                :commitId, :eventIndex, :eventType, :itemId, :fromPosition, :toPosition,
                                :replacementArrangementId, :transposeToKey, :transposeToMode, :removed,
                                CAST(:payload AS jsonb)
                            )
                            """,
                    new MapSqlParameterSource()
                            .addValue("commitId", commitId)
                            .addValue("eventIndex", event.eventIndex())
                            .addValue("eventType", event.eventType())
                            .addValue("itemId", event.itemId())
                            .addValue("fromPosition", event.fromPosition())
                            .addValue("toPosition", event.toPosition())
                            .addValue("replacementArrangementId", event.replacementArrangementId())
                            .addValue("transposeToKey", event.transposeToKey())
                            .addValue("transposeToMode", event.transposeToMode())
                            .addValue("removed", event.removed())
                            .addValue("payload", event.payload()));
        }
    }

    private Optional<SetlistVersionSnapshot> getSnapshot(UUID setlistId, UUID versionId) {
        List<SetlistVersionSnapshot> versions = jdbcTemplate.query(
                """
                        SELECT id, parent_version_id, version_number, provenance_type,
                               scoring_profile_version, engine_version, created_at, created_by
                        FROM setlist_versions
                        WHERE setlist_id = :setlistId AND id = :versionId
                        """,
                Map.of("setlistId", setlistId, "versionId", versionId),
                (rs, rowNum) -> new SetlistVersionSnapshot(
                        setlistId,
                        uuid(rs, "id"),
                        uuid(rs, "parent_version_id"),
                        rs.getInt("version_number"),
                        rs.getString("provenance_type"),
                        rs.getString("scoring_profile_version"),
                        rs.getString("engine_version"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getString("created_by"),
                        findItems(uuid(rs, "id"))));
        return versions.stream().findFirst();
    }

    private List<SetlistVersionItemSnapshot> findItems(UUID versionId) {
        return jdbcTemplate.query(
                """
                        SELECT id, position_index, catalog_arrangement_id, transposed_key,
                               transposed_mode, source_item_id, item_provenance, notes
                        FROM setlist_version_items
                        WHERE version_id = :versionId
                        ORDER BY position_index ASC
                        """,
                Map.of("versionId", versionId),
                (rs, rowNum) -> new SetlistVersionItemSnapshot(
                        uuid(rs, "id"),
                        rs.getInt("position_index"),
                        uuid(rs, "catalog_arrangement_id"),
                        rs.getString("transposed_key"),
                        rs.getString("transposed_mode"),
                        uuid(rs, "source_item_id"),
                        rs.getString("item_provenance"),
                        rs.getString("notes")));
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }
}
