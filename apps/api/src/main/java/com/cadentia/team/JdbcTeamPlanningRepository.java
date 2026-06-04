package com.cadentia.team;

import com.cadentia.team.TeamPlanningModels.AssignmentStatusCode;
import com.cadentia.team.TeamPlanningModels.AvailabilityWindowRecord;
import com.cadentia.team.TeamPlanningModels.ControlledVocabularyEntry;
import com.cadentia.team.TeamPlanningModels.CreateMusicianCommand;
import com.cadentia.team.TeamPlanningModels.InstrumentCode;
import com.cadentia.team.TeamPlanningModels.MusicianRecord;
import com.cadentia.team.TeamPlanningModels.MusicianRoleCode;
import com.cadentia.team.TeamPlanningModels.RehearsalAssignmentRecord;
import com.cadentia.team.TeamPlanningModels.RehearsalEventRecord;
import com.cadentia.team.TeamPlanningModels.ServiceAssignmentRecord;
import com.cadentia.team.TeamPlanningModels.ServingPreferenceCode;
import com.cadentia.team.TeamPlanningModels.SkillLevelCode;
import com.cadentia.team.TeamPlanningModels.SongAssignmentOverrideRecord;
import com.cadentia.team.TeamPlanningModels.VocalPartCode;
import com.cadentia.team.TeamPlanningModels.VocalRangeCode;
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
public class JdbcTeamPlanningRepository implements TeamPlanningRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcTeamPlanningRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ControlledVocabularyEntry> listAssignmentStatuses() {
        return listVocabulary("assignment_statuses");
    }

    @Override
    public List<ControlledVocabularyEntry> listInstruments() {
        return listVocabulary("instruments");
    }

    @Override
    public List<ControlledVocabularyEntry> listMusicianRoles() {
        return listVocabulary("musician_roles");
    }

    @Override
    @Transactional
    public MusicianRecord createMusician(CreateMusicianCommand command) {
        UUID musicianId = jdbcTemplate.queryForObject(
                """
                INSERT INTO musicians (
                    display_name, account_principal, email, phone, primary_vocal_range_code,
                    comfortable_low_midi_note, comfortable_high_midi_note, serving_preference_code,
                    created_by, updated_by
                ) VALUES (
                    :displayName, :accountPrincipal, :email, :phone, :primaryVocalRangeCode,
                    :comfortableLowMidiNote, :comfortableHighMidiNote, :servingPreferenceCode,
                    :createdBy, :createdBy
                )
                RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("displayName", command.displayName())
                        .addValue("accountPrincipal", command.accountPrincipal())
                        .addValue("email", command.email())
                        .addValue("phone", command.phone())
                        .addValue("primaryVocalRangeCode", enumName(command.primaryVocalRangeCode()))
                        .addValue("comfortableLowMidiNote", command.comfortableLowMidiNote())
                        .addValue("comfortableHighMidiNote", command.comfortableHighMidiNote())
                        .addValue("servingPreferenceCode", enumName(command.servingPreferenceCode()))
                        .addValue("createdBy", command.createdBy()),
                UUID.class);
        return findMusician(musicianId).orElseThrow();
    }

    @Override
    public Optional<MusicianRecord> findMusician(UUID musicianId) {
        List<MusicianRecord> rows = jdbcTemplate.query(
                "SELECT * FROM musicians WHERE id = :id",
                Map.of("id", musicianId),
                (rs, rowNum) -> mapMusician(rs));
        return rows.stream().findFirst();
    }

    @Override
    public UUID assignRole(UUID musicianId, MusicianRoleCode roleCode, SkillLevelCode skillLevelCode) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO musician_role_assignments (musician_id, role_code, skill_level_code)
                VALUES (:musicianId, :roleCode, :skillLevelCode)
                RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("musicianId", musicianId)
                        .addValue("roleCode", enumName(roleCode))
                        .addValue("skillLevelCode", enumName(skillLevelCode)),
                UUID.class);
    }

    @Override
    public UUID assignInstrument(UUID musicianId, InstrumentCode instrumentCode, SkillLevelCode skillLevelCode) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO musician_instrument_assignments (musician_id, instrument_code, skill_level_code)
                VALUES (:musicianId, :instrumentCode, :skillLevelCode)
                RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("musicianId", musicianId)
                        .addValue("instrumentCode", enumName(instrumentCode))
                        .addValue("skillLevelCode", enumName(skillLevelCode)),
                UUID.class);
    }

    @Override
    public UUID assignVocalPart(UUID musicianId, VocalPartCode vocalPartCode, SkillLevelCode skillLevelCode) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO musician_vocal_part_assignments (musician_id, vocal_part_code, skill_level_code)
                VALUES (:musicianId, :vocalPartCode, :skillLevelCode)
                RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("musicianId", musicianId)
                        .addValue("vocalPartCode", enumName(vocalPartCode))
                        .addValue("skillLevelCode", enumName(skillLevelCode)),
                UUID.class);
    }

    @Override
    public AvailabilityWindowRecord createAvailabilityWindow(
            UUID musicianId,
            Instant startsAt,
            Instant endsAt,
            AssignmentStatusCode statusCode,
            UUID servicePlanId) {
        UUID id = jdbcTemplate.queryForObject(
                """
                INSERT INTO musician_availability_windows (musician_id, starts_at, ends_at, status_code, service_plan_id)
                VALUES (:musicianId, :startsAt, :endsAt, :statusCode, :servicePlanId)
                RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("musicianId", musicianId)
                        .addValue("startsAt", Timestamp.from(startsAt))
                        .addValue("endsAt", Timestamp.from(endsAt))
                        .addValue("statusCode", enumName(statusCode))
                        .addValue("servicePlanId", servicePlanId),
                UUID.class);
        return new AvailabilityWindowRecord(id, musicianId, startsAt, endsAt, statusCode, servicePlanId);
    }

    @Override
    public ServiceAssignmentRecord createServiceAssignment(
            UUID servicePlanId,
            UUID musicianId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            AssignmentStatusCode statusCode) {
        UUID id = jdbcTemplate.queryForObject(
                """
                INSERT INTO service_team_assignments (
                    service_plan_id, musician_id, role_code, instrument_code, vocal_part_code, status_code
                ) VALUES (:servicePlanId, :musicianId, :roleCode, :instrumentCode, :vocalPartCode, :statusCode)
                RETURNING id
                """,
                assignmentParameters(servicePlanId, musicianId, roleCode, instrumentCode, vocalPartCode, statusCode),
                UUID.class);
        return new ServiceAssignmentRecord(
                id, servicePlanId, musicianId, roleCode, instrumentCode, vocalPartCode, statusCode);
    }

    @Override
    public RehearsalEventRecord createRehearsalEvent(
            UUID servicePlanId,
            Instant startsAt,
            Instant endsAt,
            String location) {
        UUID id = jdbcTemplate.queryForObject(
                """
                INSERT INTO rehearsal_events (service_plan_id, starts_at, ends_at, location)
                VALUES (:servicePlanId, :startsAt, :endsAt, :location)
                RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("servicePlanId", servicePlanId)
                        .addValue("startsAt", Timestamp.from(startsAt))
                        .addValue("endsAt", Timestamp.from(endsAt))
                        .addValue("location", location),
                UUID.class);
        return new RehearsalEventRecord(id, servicePlanId, startsAt, endsAt, location);
    }

    @Override
    public RehearsalAssignmentRecord createRehearsalAssignment(
            UUID rehearsalEventId,
            UUID servicePlanId,
            UUID musicianId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            AssignmentStatusCode statusCode) {
        UUID id = jdbcTemplate.queryForObject(
                """
                INSERT INTO rehearsal_team_assignments (
                    rehearsal_event_id, service_plan_id, musician_id, role_code,
                    instrument_code, vocal_part_code, status_code
                ) VALUES (
                    :rehearsalEventId, :servicePlanId, :musicianId, :roleCode,
                    :instrumentCode, :vocalPartCode, :statusCode
                )
                RETURNING id
                """,
                assignmentParameters(servicePlanId, musicianId, roleCode, instrumentCode, vocalPartCode, statusCode)
                        .addValue("rehearsalEventId", rehearsalEventId),
                UUID.class);
        return new RehearsalAssignmentRecord(
                id, rehearsalEventId, servicePlanId, musicianId, roleCode, instrumentCode, vocalPartCode, statusCode);
    }

    @Override
    public SongAssignmentOverrideRecord createSongAssignmentOverride(
            UUID servicePlanId,
            UUID servicePlanBlockId,
            UUID baseServiceAssignmentId,
            UUID musicianId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            AssignmentStatusCode statusCode) {
        UUID id = jdbcTemplate.queryForObject(
                """
                INSERT INTO service_song_assignment_overrides (
                    service_plan_id, service_plan_block_id, base_service_assignment_id,
                    musician_id, role_code, instrument_code, vocal_part_code, status_code
                ) VALUES (
                    :servicePlanId, :servicePlanBlockId, :baseServiceAssignmentId,
                    :musicianId, :roleCode, :instrumentCode, :vocalPartCode, :statusCode
                )
                RETURNING id
                """,
                assignmentParameters(servicePlanId, musicianId, roleCode, instrumentCode, vocalPartCode, statusCode)
                        .addValue("servicePlanBlockId", servicePlanBlockId)
                        .addValue("baseServiceAssignmentId", baseServiceAssignmentId),
                UUID.class);
        return new SongAssignmentOverrideRecord(
                id,
                servicePlanId,
                servicePlanBlockId,
                baseServiceAssignmentId,
                musicianId,
                roleCode,
                instrumentCode,
                vocalPartCode,
                statusCode);
    }

    private List<ControlledVocabularyEntry> listVocabulary(String tableName) {
        return jdbcTemplate.query(
                "SELECT code, display_name, active, sort_order, system_default, local_extension FROM "
                        + tableName + " ORDER BY sort_order, code",
                (rs, rowNum) -> new ControlledVocabularyEntry(
                        rs.getString("code"),
                        rs.getString("display_name"),
                        rs.getBoolean("active"),
                        rs.getInt("sort_order"),
                        rs.getBoolean("system_default"),
                        rs.getBoolean("local_extension")));
    }

    private MapSqlParameterSource assignmentParameters(
            UUID servicePlanId,
            UUID musicianId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            AssignmentStatusCode statusCode) {
        return new MapSqlParameterSource()
                .addValue("servicePlanId", servicePlanId)
                .addValue("musicianId", musicianId)
                .addValue("roleCode", enumName(roleCode))
                .addValue("instrumentCode", enumName(instrumentCode))
                .addValue("vocalPartCode", enumName(vocalPartCode))
                .addValue("statusCode", enumName(statusCode));
    }

    private MusicianRecord mapMusician(ResultSet rs) throws SQLException {
        return new MusicianRecord(
                rs.getObject("id", UUID.class),
                rs.getString("display_name"),
                rs.getString("account_principal"),
                rs.getString("email"),
                rs.getString("phone"),
                enumValue(VocalRangeCode.class, rs.getString("primary_vocal_range_code")),
                nullableInteger(rs, "comfortable_low_midi_note"),
                nullableInteger(rs, "comfortable_high_midi_note"),
                enumValue(ServingPreferenceCode.class, rs.getString("serving_preference_code")),
                rs.getBoolean("active"));
    }

    private Integer nullableInteger(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : value;
    }

    private String enumName(Enum<?> enumValue) {
        return enumValue == null ? null : enumValue.name();
    }

    private <E extends Enum<E>> E enumValue(Class<E> enumClass, String value) {
        return value == null ? null : Enum.valueOf(enumClass, value);
    }
}
