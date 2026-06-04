package com.cadentia.team;

import com.cadentia.team.PersonnelAuditModels.PersonnelAuditEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PersonnelPrivilegedAuditService implements PersonnelAuditRecorder {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PersonnelPrivilegedAuditService(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public UUID record(PersonnelAuditEvent event) {
        String sql = """
                INSERT INTO privileged_action_audit_events (
                    id, actor, actor_roles, action, target_type, target_id, before_state_ref, after_state_ref,
                    before_state_hash, after_state_hash, metadata, occurred_at, request_id
                ) VALUES (
                    :id, :actor, CAST(:actorRoles AS jsonb), :action, :targetType, :targetId, :beforeStateRef,
                    :afterStateRef, :beforeStateHash, :afterStateHash, CAST(:metadata AS jsonb), :occurredAt, :requestId
                )
                RETURNING id
                """;
        return jdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource()
                        .addValue("id", event.id())
                        .addValue("actor", event.actor())
                        .addValue("actorRoles", writeJson(event.actorRoles()))
                        .addValue("action", event.action().name())
                        .addValue("targetType", event.targetType().name())
                        .addValue("targetId", event.targetId())
                        .addValue("beforeStateRef", event.beforeStateRef())
                        .addValue("afterStateRef", event.afterStateRef())
                        .addValue("beforeStateHash", event.beforeStateHash())
                        .addValue("afterStateHash", event.afterStateHash())
                        .addValue("metadata", metadataJson(event))
                        .addValue("occurredAt", Timestamp.from(event.occurredAt()))
                        .addValue("requestId", event.reference()),
                UUID.class);
    }

    private String metadataJson(PersonnelAuditEvent event) {
        return writeJson(Map.of(
                "reasonCode", nullToEmpty(event.reasonCode()),
                "reference", nullToEmpty(event.reference()),
                "changedFields", event.changedFields()));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize personnel audit metadata.", exception);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
