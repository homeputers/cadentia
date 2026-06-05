-- ADR-023 / ADR-033 arrangement suitability requirements.
-- Suitability metadata is structured, versioned planning data. It is not an
-- approval gate and cannot make an arrangement recommendable unless the
-- existing v_recommendable_arrangements approval gates already include it.

CREATE TABLE arrangement_suitability_profiles (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    arrangement_id uuid NOT NULL REFERENCES arrangements (id) ON DELETE CASCADE,
    version_number integer NOT NULL,
    is_current boolean NOT NULL DEFAULT false,
    vocal_configuration varchar(64) NOT NULL DEFAULT 'UNSPECIFIED',
    lead_vocal_low_midi_note integer,
    lead_vocal_high_midi_note integer,
    required_backing_vocal_count integer NOT NULL DEFAULT 0,
    review_notes text,
    governance_action_ref varchar(255) NOT NULL,
    created_by varchar(255) NOT NULL DEFAULT 'system',
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT arrangement_suitability_profiles_version_positive CHECK (version_number > 0),
    CONSTRAINT arrangement_suitability_profiles_vocal_configuration_valid CHECK (
        vocal_configuration IN ('UNSPECIFIED', 'INSTRUMENTAL', 'SOLO_LEAD', 'LEAD_WITH_BACKING', 'CHOIR', 'CONGREGATIONAL')
    ),
    CONSTRAINT arrangement_suitability_profiles_backing_count_non_negative CHECK (required_backing_vocal_count >= 0),
    CONSTRAINT arrangement_suitability_profiles_lead_range_bounds CHECK (
        lead_vocal_low_midi_note IS NULL
        OR lead_vocal_high_midi_note IS NULL
        OR lead_vocal_low_midi_note <= lead_vocal_high_midi_note
    ),
    CONSTRAINT arrangement_suitability_profiles_lead_range_reasonable CHECK (
        (lead_vocal_low_midi_note IS NULL OR lead_vocal_low_midi_note BETWEEN 0 AND 127)
        AND (lead_vocal_high_midi_note IS NULL OR lead_vocal_high_midi_note BETWEEN 0 AND 127)
    ),
    CONSTRAINT arrangement_suitability_profiles_governance_ref_not_blank CHECK (btrim(governance_action_ref) <> ''),
    CONSTRAINT arrangement_suitability_profiles_created_by_not_blank CHECK (btrim(created_by) <> ''),
    CONSTRAINT arrangement_suitability_profiles_arrangement_version_unique UNIQUE (arrangement_id, version_number)
);

CREATE UNIQUE INDEX arrangement_suitability_profiles_current_unique_idx
    ON arrangement_suitability_profiles (arrangement_id)
    WHERE is_current;

CREATE TABLE arrangement_suitability_slots (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    suitability_profile_id uuid NOT NULL REFERENCES arrangement_suitability_profiles (id) ON DELETE CASCADE,
    requirement_type varchar(32) NOT NULL,
    role_code varchar(64) REFERENCES musician_roles (code) ON DELETE RESTRICT,
    instrument_code varchar(64) REFERENCES instruments (code) ON DELETE RESTRICT,
    vocal_part_code varchar(64) REFERENCES vocal_parts (code) ON DELETE RESTRICT,
    minimum_skill_level_code varchar(64) REFERENCES skill_levels (code) ON DELETE RESTRICT,
    minimum_count integer NOT NULL DEFAULT 1,
    coverage_rule varchar(64) NOT NULL DEFAULT 'AT_LEAST',
    review_notes text,
    sort_order integer NOT NULL DEFAULT 0,
    CONSTRAINT arrangement_suitability_slots_requirement_type_valid CHECK (requirement_type IN ('REQUIRED', 'OPTIONAL')),
    CONSTRAINT arrangement_suitability_slots_minimum_count_positive CHECK (minimum_count > 0),
    CONSTRAINT arrangement_suitability_slots_sort_order_non_negative CHECK (sort_order >= 0),
    CONSTRAINT arrangement_suitability_slots_coverage_rule_valid CHECK (coverage_rule IN ('AT_LEAST', 'EXACTLY', 'ANY_OF')),
    CONSTRAINT arrangement_suitability_slots_structured_requirement CHECK (
        role_code IS NOT NULL OR instrument_code IS NOT NULL OR vocal_part_code IS NOT NULL
    )
);

CREATE INDEX arrangement_suitability_profiles_arrangement_current_idx
    ON arrangement_suitability_profiles (arrangement_id, is_current, version_number DESC);

CREATE INDEX arrangement_suitability_slots_profile_requirement_idx
    ON arrangement_suitability_slots (suitability_profile_id, requirement_type, sort_order, id);

CREATE INDEX service_team_assignments_suitability_context_idx
    ON service_team_assignments (service_plan_id, status_code, instrument_code, vocal_part_code, role_code, musician_id);

CREATE VIEW v_approved_arrangement_suitability_profiles AS
SELECT
    recommendable.arrangement_id,
    recommendable.song_id,
    suitability.id AS suitability_profile_id,
    suitability.version_number,
    suitability.vocal_configuration,
    suitability.lead_vocal_low_midi_note,
    suitability.lead_vocal_high_midi_note,
    suitability.required_backing_vocal_count,
    suitability.review_notes,
    suitability.governance_action_ref,
    suitability.created_by,
    suitability.created_at
FROM v_recommendable_arrangements recommendable
JOIN arrangement_suitability_profiles suitability
  ON suitability.arrangement_id = recommendable.arrangement_id
 AND suitability.is_current;

CREATE VIEW v_approved_arrangement_suitability_slots AS
SELECT
    profiles.arrangement_id,
    profiles.song_id,
    profiles.suitability_profile_id,
    profiles.version_number,
    slots.id AS suitability_slot_id,
    slots.requirement_type,
    slots.role_code,
    slots.instrument_code,
    slots.vocal_part_code,
    slots.minimum_skill_level_code,
    skill_levels.level_rank AS minimum_skill_rank,
    slots.minimum_count,
    slots.coverage_rule,
    slots.review_notes,
    slots.sort_order
FROM v_approved_arrangement_suitability_profiles profiles
JOIN arrangement_suitability_slots slots ON slots.suitability_profile_id = profiles.suitability_profile_id
LEFT JOIN skill_levels ON skill_levels.code = slots.minimum_skill_level_code;

COMMENT ON TABLE arrangement_suitability_profiles IS
    'Versioned, governance-linked arrangement team-suitability metadata. Suitability is planning metadata, not catalog approval eligibility.';
COMMENT ON TABLE arrangement_suitability_slots IS
    'Structured required and optional role, instrument, vocal-part, and skill-floor requirements for an arrangement suitability profile.';
COMMENT ON VIEW v_approved_arrangement_suitability_profiles IS
    'Suitability profiles exposed only after v_recommendable_arrangements approval gates admit the arrangement.';
COMMENT ON VIEW v_approved_arrangement_suitability_slots IS
    'Queryable suitability slots for approved recommendation candidates; unapproved catalog records are intentionally absent.';
COMMENT ON COLUMN arrangement_suitability_profiles.governance_action_ref IS
    'Catalog governance action, ticket, or audit reference that explains why this suitability version was created.';
COMMENT ON COLUMN arrangement_suitability_profiles.review_notes IS
    'Human-review suitability notes; not a substitute for structured instruments, vocal parts, ranges, or skill levels.';
COMMENT ON COLUMN arrangement_suitability_slots.requirement_type IS
    'Whether the structured slot is required for a pass or optional for a warning/recommendation.';
COMMENT ON COLUMN arrangement_suitability_slots.coverage_rule IS
    'Deterministic coverage rule used when counting matching service-team assignments.';
