package com.cadentia.reng.scoring;

import com.cadentia.reng.scoring.TeamSuitabilityModels.AssignmentStatus;
import com.cadentia.reng.scoring.TeamSuitabilityModels.ExplicitTeamConstraints;
import com.cadentia.reng.scoring.TeamSuitabilityModels.TeamAssignment;
import com.cadentia.reng.scoring.TeamSuitabilityModels.TeamContextReference;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcTeamContextResolver implements TeamContextResolver {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcTeamContextResolver(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ExplicitTeamConstraints resolve(TeamContextReference reference) {
        if (reference == null || reference.servicePlanId() == null) {
            return new ExplicitTeamConstraints(null, List.of(), Map.of(), true);
        }
        List<TeamAssignmentRow> rows = jdbcTemplate.query(
                """
                SELECT service_team_assignments.musician_id,
                       service_team_assignments.status_code,
                       service_team_assignments.role_code AS assigned_role_code,
                       service_team_assignments.instrument_code AS assigned_instrument_code,
                       service_team_assignments.vocal_part_code AS assigned_vocal_part_code,
                       musicians.comfortable_low_midi_note,
                       musicians.comfortable_high_midi_note,
                       EXISTS (
                           SELECT 1
                           FROM musician_availability_windows availability
                           WHERE availability.musician_id = service_team_assignments.musician_id
                             AND availability.service_plan_id = service_team_assignments.service_plan_id
                             AND availability.status_code IN ('DECLINED', 'UNAVAILABLE')
                       ) AS unavailable_for_service,
                       instrument_skill.instrument_code,
                       instrument_level.level_rank AS instrument_skill_rank,
                       vocal_skill.vocal_part_code,
                       vocal_level.level_rank AS vocal_skill_rank
                FROM service_team_assignments
                JOIN musicians ON musicians.id = service_team_assignments.musician_id AND musicians.active
                LEFT JOIN musician_instrument_assignments instrument_skill
                  ON instrument_skill.musician_id = service_team_assignments.musician_id
                 AND instrument_skill.active
                LEFT JOIN skill_levels instrument_level ON instrument_level.code = instrument_skill.skill_level_code
                LEFT JOIN musician_vocal_part_assignments vocal_skill
                  ON vocal_skill.musician_id = service_team_assignments.musician_id
                 AND vocal_skill.active
                LEFT JOIN skill_levels vocal_level ON vocal_level.code = vocal_skill.skill_level_code
                WHERE service_team_assignments.service_plan_id = :servicePlanId
                  AND (:servicePlanBlockId IS NULL OR EXISTS (
                      SELECT 1
                      FROM service_song_assignment_overrides overrides
                      WHERE overrides.service_plan_id = service_team_assignments.service_plan_id
                        AND overrides.service_plan_block_id = :servicePlanBlockId
                        AND overrides.base_service_assignment_id = service_team_assignments.id
                  ) OR NOT EXISTS (
                      SELECT 1
                      FROM service_song_assignment_overrides overrides
                      WHERE overrides.service_plan_id = service_team_assignments.service_plan_id
                        AND overrides.service_plan_block_id = :servicePlanBlockId
                  ))
                ORDER BY service_team_assignments.assignment_order ASC,
                         service_team_assignments.musician_id ASC,
                         instrument_skill.instrument_code ASC,
                         vocal_skill.vocal_part_code ASC
                """,
                new MapSqlParameterSource()
                        .addValue("servicePlanId", reference.servicePlanId())
                        .addValue("servicePlanBlockId", reference.servicePlanBlockId()),
                (rs, rowNum) -> mapRow(rs));
        return new ExplicitTeamConstraints(
                reference.servicePlanId(),
                mergeRows(rows),
                Map.of(),
                rows.isEmpty());
    }

    private static List<TeamAssignment> mergeRows(List<TeamAssignmentRow> rows) {
        return rows.stream()
                .collect(Collectors.groupingBy(TeamAssignmentRow::musicianId, LinkedHashMap::new, Collectors.toList()))
                .values()
                .stream()
                .map(JdbcTeamContextResolver::mergeMusicianRows)
                .toList();
    }

    private static TeamAssignment mergeMusicianRows(List<TeamAssignmentRow> rows) {
        TeamAssignmentRow first = rows.getFirst();
        Set<String> roles = rows.stream()
                .map(TeamAssignmentRow::assignedRoleCode)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        Set<String> instruments = rows.stream()
                .map(TeamAssignmentRow::instrumentCode)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        if (first.assignedInstrumentCode() != null) {
            instruments = union(instruments, first.assignedInstrumentCode());
        }
        Set<String> vocalParts = rows.stream()
                .map(TeamAssignmentRow::vocalPartCode)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        if (first.assignedVocalPartCode() != null) {
            vocalParts = union(vocalParts, first.assignedVocalPartCode());
        }
        Map<String, Integer> instrumentRanks = rows.stream()
                .filter(row -> row.instrumentCode() != null && row.instrumentSkillRank() != null)
                .collect(Collectors.toMap(TeamAssignmentRow::instrumentCode, TeamAssignmentRow::instrumentSkillRank, Integer::max));
        Map<String, Integer> vocalRanks = rows.stream()
                .filter(row -> row.vocalPartCode() != null && row.vocalSkillRank() != null)
                .collect(Collectors.toMap(TeamAssignmentRow::vocalPartCode, TeamAssignmentRow::vocalSkillRank, Integer::max));
        return new TeamAssignment(
                first.musicianId(),
                AssignmentStatus.valueOf(first.statusCode()),
                !first.unavailableForService(),
                roles,
                instruments,
                vocalParts,
                instrumentRanks,
                vocalRanks,
                first.comfortableLowMidiNote(),
                first.comfortableHighMidiNote());
    }

    private static Set<String> union(Set<String> values, String value) {
        HashSet<String> merged = new HashSet<>(values);
        merged.add(value);
        return Set.copyOf(merged);
    }

    private static TeamAssignmentRow mapRow(ResultSet rs) throws SQLException {
        return new TeamAssignmentRow(
                rs.getObject("musician_id", UUID.class),
                rs.getString("status_code"),
                rs.getString("assigned_role_code"),
                rs.getString("assigned_instrument_code"),
                rs.getString("assigned_vocal_part_code"),
                integerOrNull(rs, "comfortable_low_midi_note"),
                integerOrNull(rs, "comfortable_high_midi_note"),
                rs.getBoolean("unavailable_for_service"),
                rs.getString("instrument_code"),
                integerOrNull(rs, "instrument_skill_rank"),
                rs.getString("vocal_part_code"),
                integerOrNull(rs, "vocal_skill_rank"));
    }

    private static Integer integerOrNull(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private record TeamAssignmentRow(
            UUID musicianId,
            String statusCode,
            String assignedRoleCode,
            String assignedInstrumentCode,
            String assignedVocalPartCode,
            Integer comfortableLowMidiNote,
            Integer comfortableHighMidiNote,
            boolean unavailableForService,
            String instrumentCode,
            Integer instrumentSkillRank,
            String vocalPartCode,
            Integer vocalSkillRank) {
    }
}
