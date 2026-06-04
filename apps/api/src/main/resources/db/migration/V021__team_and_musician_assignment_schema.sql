CREATE TABLE musician_roles (
    code varchar(64) PRIMARY KEY,
    display_name varchar(128) NOT NULL,
    description text,
    active boolean NOT NULL DEFAULT true,
    sort_order integer NOT NULL,
    system_default boolean NOT NULL DEFAULT false,
    local_extension boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT musician_roles_code_not_blank CHECK (btrim(code) <> ''),
    CONSTRAINT musician_roles_display_name_not_blank CHECK (btrim(display_name) <> ''),
    CONSTRAINT musician_roles_sort_order_non_negative CHECK (sort_order >= 0),
    CONSTRAINT musician_roles_updated_at_not_before_created_at CHECK (updated_at >= created_at)
);

CREATE TABLE instruments (
    code varchar(64) PRIMARY KEY,
    display_name varchar(128) NOT NULL,
    description text,
    active boolean NOT NULL DEFAULT true,
    sort_order integer NOT NULL,
    system_default boolean NOT NULL DEFAULT false,
    local_extension boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT instruments_code_not_blank CHECK (btrim(code) <> ''),
    CONSTRAINT instruments_display_name_not_blank CHECK (btrim(display_name) <> ''),
    CONSTRAINT instruments_sort_order_non_negative CHECK (sort_order >= 0),
    CONSTRAINT instruments_updated_at_not_before_created_at CHECK (updated_at >= created_at)
);

CREATE TABLE vocal_parts (
    code varchar(64) PRIMARY KEY,
    display_name varchar(128) NOT NULL,
    description text,
    active boolean NOT NULL DEFAULT true,
    sort_order integer NOT NULL,
    system_default boolean NOT NULL DEFAULT false,
    local_extension boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT vocal_parts_code_not_blank CHECK (btrim(code) <> ''),
    CONSTRAINT vocal_parts_display_name_not_blank CHECK (btrim(display_name) <> ''),
    CONSTRAINT vocal_parts_sort_order_non_negative CHECK (sort_order >= 0),
    CONSTRAINT vocal_parts_updated_at_not_before_created_at CHECK (updated_at >= created_at)
);

CREATE TABLE vocal_ranges (
    code varchar(64) PRIMARY KEY,
    display_name varchar(128) NOT NULL,
    description text,
    lowest_midi_note integer,
    highest_midi_note integer,
    active boolean NOT NULL DEFAULT true,
    sort_order integer NOT NULL,
    system_default boolean NOT NULL DEFAULT false,
    local_extension boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT vocal_ranges_code_not_blank CHECK (btrim(code) <> ''),
    CONSTRAINT vocal_ranges_display_name_not_blank CHECK (btrim(display_name) <> ''),
    CONSTRAINT vocal_ranges_sort_order_non_negative CHECK (sort_order >= 0),
    CONSTRAINT vocal_ranges_note_bounds CHECK (
        lowest_midi_note IS NULL
        OR highest_midi_note IS NULL
        OR lowest_midi_note <= highest_midi_note
    ),
    CONSTRAINT vocal_ranges_notes_reasonable CHECK (
        (lowest_midi_note IS NULL OR lowest_midi_note BETWEEN 0 AND 127)
        AND (highest_midi_note IS NULL OR highest_midi_note BETWEEN 0 AND 127)
    ),
    CONSTRAINT vocal_ranges_updated_at_not_before_created_at CHECK (updated_at >= created_at)
);

CREATE TABLE skill_levels (
    code varchar(64) PRIMARY KEY,
    display_name varchar(128) NOT NULL,
    description text,
    level_rank integer NOT NULL,
    active boolean NOT NULL DEFAULT true,
    sort_order integer NOT NULL,
    system_default boolean NOT NULL DEFAULT false,
    local_extension boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT skill_levels_code_not_blank CHECK (btrim(code) <> ''),
    CONSTRAINT skill_levels_display_name_not_blank CHECK (btrim(display_name) <> ''),
    CONSTRAINT skill_levels_rank_positive CHECK (level_rank > 0),
    CONSTRAINT skill_levels_sort_order_non_negative CHECK (sort_order >= 0),
    CONSTRAINT skill_levels_updated_at_not_before_created_at CHECK (updated_at >= created_at)
);

CREATE TABLE serving_preferences (
    code varchar(64) PRIMARY KEY,
    display_name varchar(128) NOT NULL,
    description text,
    active boolean NOT NULL DEFAULT true,
    sort_order integer NOT NULL,
    system_default boolean NOT NULL DEFAULT false,
    local_extension boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT serving_preferences_code_not_blank CHECK (btrim(code) <> ''),
    CONSTRAINT serving_preferences_display_name_not_blank CHECK (btrim(display_name) <> ''),
    CONSTRAINT serving_preferences_sort_order_non_negative CHECK (sort_order >= 0),
    CONSTRAINT serving_preferences_updated_at_not_before_created_at CHECK (updated_at >= created_at)
);

CREATE TABLE assignment_statuses (
    code varchar(64) PRIMARY KEY,
    display_name varchar(128) NOT NULL,
    description text,
    active boolean NOT NULL DEFAULT true,
    sort_order integer NOT NULL,
    system_default boolean NOT NULL DEFAULT false,
    local_extension boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT assignment_statuses_code_not_blank CHECK (btrim(code) <> ''),
    CONSTRAINT assignment_statuses_display_name_not_blank CHECK (btrim(display_name) <> ''),
    CONSTRAINT assignment_statuses_sort_order_non_negative CHECK (sort_order >= 0),
    CONSTRAINT assignment_statuses_updated_at_not_before_created_at CHECK (updated_at >= created_at)
);

CREATE TABLE musicians (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name varchar(255) NOT NULL,
    account_principal varchar(255),
    email varchar(255),
    phone varchar(64),
    primary_vocal_range_code varchar(64) REFERENCES vocal_ranges (code) ON DELETE RESTRICT,
    comfortable_low_midi_note integer,
    comfortable_high_midi_note integer,
    serving_preference_code varchar(64) REFERENCES serving_preferences (code) ON DELETE RESTRICT,
    active boolean NOT NULL DEFAULT true,
    notes text,
    created_by varchar(255) NOT NULL DEFAULT 'system',
    updated_by varchar(255) NOT NULL DEFAULT 'system',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT musicians_display_name_not_blank CHECK (btrim(display_name) <> ''),
    CONSTRAINT musicians_account_principal_not_blank CHECK (account_principal IS NULL OR btrim(account_principal) <> ''),
    CONSTRAINT musicians_email_not_blank CHECK (email IS NULL OR btrim(email) <> ''),
    CONSTRAINT musicians_phone_not_blank CHECK (phone IS NULL OR btrim(phone) <> ''),
    CONSTRAINT musicians_created_by_not_blank CHECK (btrim(created_by) <> ''),
    CONSTRAINT musicians_updated_by_not_blank CHECK (btrim(updated_by) <> ''),
    CONSTRAINT musicians_vocal_note_bounds CHECK (
        comfortable_low_midi_note IS NULL
        OR comfortable_high_midi_note IS NULL
        OR comfortable_low_midi_note <= comfortable_high_midi_note
    ),
    CONSTRAINT musicians_vocal_notes_reasonable CHECK (
        (comfortable_low_midi_note IS NULL OR comfortable_low_midi_note BETWEEN 0 AND 127)
        AND (comfortable_high_midi_note IS NULL OR comfortable_high_midi_note BETWEEN 0 AND 127)
    ),
    CONSTRAINT musicians_updated_at_not_before_created_at CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX musicians_account_principal_unique_idx
    ON musicians (account_principal)
    WHERE account_principal IS NOT NULL;

CREATE TABLE teams (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(64) NOT NULL,
    display_name varchar(128) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT teams_code_not_blank CHECK (btrim(code) <> ''),
    CONSTRAINT teams_display_name_not_blank CHECK (btrim(display_name) <> ''),
    CONSTRAINT teams_code_unique UNIQUE (code),
    CONSTRAINT teams_updated_at_not_before_created_at CHECK (updated_at >= created_at)
);

CREATE TABLE team_memberships (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id uuid NOT NULL REFERENCES teams (id) ON DELETE CASCADE,
    musician_id uuid NOT NULL REFERENCES musicians (id) ON DELETE CASCADE,
    role_code varchar(64) REFERENCES musician_roles (code) ON DELETE RESTRICT,
    active boolean NOT NULL DEFAULT true,
    started_on date,
    ended_on date,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT team_memberships_date_range CHECK (ended_on IS NULL OR started_on IS NULL OR ended_on >= started_on),
    CONSTRAINT team_memberships_updated_at_not_before_created_at CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX team_memberships_active_unique_idx
    ON team_memberships (team_id, musician_id, (COALESCE(role_code, '')))
    WHERE active;

CREATE TABLE musician_role_assignments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    musician_id uuid NOT NULL REFERENCES musicians (id) ON DELETE CASCADE,
    role_code varchar(64) NOT NULL REFERENCES musician_roles (code) ON DELETE RESTRICT,
    skill_level_code varchar(64) REFERENCES skill_levels (code) ON DELETE RESTRICT,
    serving_preference_code varchar(64) REFERENCES serving_preferences (code) ON DELETE RESTRICT,
    active boolean NOT NULL DEFAULT true,
    notes text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT musician_role_assignments_updated_at_not_before_created_at CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX musician_role_assignments_active_unique_idx
    ON musician_role_assignments (musician_id, role_code)
    WHERE active;

CREATE TABLE musician_instrument_assignments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    musician_id uuid NOT NULL REFERENCES musicians (id) ON DELETE CASCADE,
    instrument_code varchar(64) NOT NULL REFERENCES instruments (code) ON DELETE RESTRICT,
    skill_level_code varchar(64) REFERENCES skill_levels (code) ON DELETE RESTRICT,
    serving_preference_code varchar(64) REFERENCES serving_preferences (code) ON DELETE RESTRICT,
    active boolean NOT NULL DEFAULT true,
    notes text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT musician_instrument_assignments_updated_at_not_before_created_at CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX musician_instrument_assignments_active_unique_idx
    ON musician_instrument_assignments (musician_id, instrument_code)
    WHERE active;

CREATE TABLE musician_vocal_part_assignments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    musician_id uuid NOT NULL REFERENCES musicians (id) ON DELETE CASCADE,
    vocal_part_code varchar(64) NOT NULL REFERENCES vocal_parts (code) ON DELETE RESTRICT,
    skill_level_code varchar(64) REFERENCES skill_levels (code) ON DELETE RESTRICT,
    serving_preference_code varchar(64) REFERENCES serving_preferences (code) ON DELETE RESTRICT,
    active boolean NOT NULL DEFAULT true,
    notes text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT musician_vocal_part_assignments_updated_at_not_before_created_at CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX musician_vocal_part_assignments_active_unique_idx
    ON musician_vocal_part_assignments (musician_id, vocal_part_code)
    WHERE active;

CREATE TABLE musician_availability_windows (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    musician_id uuid NOT NULL REFERENCES musicians (id) ON DELETE CASCADE,
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    status_code varchar(64) NOT NULL REFERENCES assignment_statuses (code) ON DELETE RESTRICT,
    service_plan_id uuid REFERENCES service_plans (id) ON DELETE CASCADE,
    notes text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT musician_availability_windows_time_range CHECK (ends_at > starts_at),
    CONSTRAINT musician_availability_windows_updated_at_not_before_created_at CHECK (updated_at >= created_at)
);

CREATE INDEX musician_availability_windows_musician_time_idx
    ON musician_availability_windows (musician_id, starts_at, ends_at);

ALTER TABLE service_plan_blocks
    ADD CONSTRAINT service_plan_blocks_service_id_block_id_unique UNIQUE (service_plan_id, id);

CREATE TABLE rehearsal_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    service_plan_id uuid NOT NULL REFERENCES service_plans (id) ON DELETE CASCADE,
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    location varchar(255),
    notes text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT rehearsal_events_time_range CHECK (ends_at > starts_at),
    CONSTRAINT rehearsal_events_location_not_blank CHECK (location IS NULL OR btrim(location) <> ''),
    CONSTRAINT rehearsal_events_service_id_event_id_unique UNIQUE (service_plan_id, id),
    CONSTRAINT rehearsal_events_updated_at_not_before_created_at CHECK (updated_at >= created_at)
);

CREATE TABLE service_team_assignments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    service_plan_id uuid NOT NULL REFERENCES service_plans (id) ON DELETE CASCADE,
    musician_id uuid NOT NULL REFERENCES musicians (id) ON DELETE RESTRICT,
    role_code varchar(64) NOT NULL REFERENCES musician_roles (code) ON DELETE RESTRICT,
    instrument_code varchar(64) REFERENCES instruments (code) ON DELETE RESTRICT,
    vocal_part_code varchar(64) REFERENCES vocal_parts (code) ON DELETE RESTRICT,
    status_code varchar(64) NOT NULL REFERENCES assignment_statuses (code) ON DELETE RESTRICT,
    substitute_for_assignment_id uuid REFERENCES service_team_assignments (id) ON DELETE SET NULL,
    notes text,
    created_by varchar(255) NOT NULL DEFAULT 'system',
    updated_by varchar(255) NOT NULL DEFAULT 'system',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT service_team_assignments_created_by_not_blank CHECK (btrim(created_by) <> ''),
    CONSTRAINT service_team_assignments_updated_by_not_blank CHECK (btrim(updated_by) <> ''),
    CONSTRAINT service_team_assignments_service_id_assignment_id_unique UNIQUE (service_plan_id, id),
    CONSTRAINT service_team_assignments_updated_at_not_before_created_at CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX service_team_assignments_active_role_unique_idx
    ON service_team_assignments (service_plan_id, musician_id, role_code)
    WHERE status_code IN ('REQUESTED', 'TENTATIVE', 'ACCEPTED', 'SUBSTITUTE');

CREATE TABLE rehearsal_team_assignments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    rehearsal_event_id uuid NOT NULL REFERENCES rehearsal_events (id) ON DELETE CASCADE,
    service_plan_id uuid NOT NULL,
    musician_id uuid NOT NULL REFERENCES musicians (id) ON DELETE RESTRICT,
    role_code varchar(64) NOT NULL REFERENCES musician_roles (code) ON DELETE RESTRICT,
    instrument_code varchar(64) REFERENCES instruments (code) ON DELETE RESTRICT,
    vocal_part_code varchar(64) REFERENCES vocal_parts (code) ON DELETE RESTRICT,
    status_code varchar(64) NOT NULL REFERENCES assignment_statuses (code) ON DELETE RESTRICT,
    substitute_for_assignment_id uuid REFERENCES rehearsal_team_assignments (id) ON DELETE SET NULL,
    notes text,
    created_by varchar(255) NOT NULL DEFAULT 'system',
    updated_by varchar(255) NOT NULL DEFAULT 'system',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT rehearsal_team_assignments_created_by_not_blank CHECK (btrim(created_by) <> ''),
    CONSTRAINT rehearsal_team_assignments_updated_by_not_blank CHECK (btrim(updated_by) <> ''),
    CONSTRAINT rehearsal_team_assignments_rehearsal_service_fk
        FOREIGN KEY (service_plan_id, rehearsal_event_id)
        REFERENCES rehearsal_events (service_plan_id, id) ON DELETE CASCADE,
    CONSTRAINT rehearsal_team_assignments_updated_at_not_before_created_at CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX rehearsal_team_assignments_active_role_unique_idx
    ON rehearsal_team_assignments (rehearsal_event_id, musician_id, role_code)
    WHERE status_code IN ('REQUESTED', 'TENTATIVE', 'ACCEPTED', 'SUBSTITUTE');

CREATE TABLE service_song_assignment_overrides (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    service_plan_id uuid NOT NULL,
    service_plan_block_id uuid NOT NULL,
    base_service_assignment_id uuid NOT NULL,
    musician_id uuid NOT NULL REFERENCES musicians (id) ON DELETE RESTRICT,
    role_code varchar(64) NOT NULL REFERENCES musician_roles (code) ON DELETE RESTRICT,
    instrument_code varchar(64) REFERENCES instruments (code) ON DELETE RESTRICT,
    vocal_part_code varchar(64) REFERENCES vocal_parts (code) ON DELETE RESTRICT,
    status_code varchar(64) NOT NULL REFERENCES assignment_statuses (code) ON DELETE RESTRICT,
    notes text,
    created_by varchar(255) NOT NULL DEFAULT 'system',
    updated_by varchar(255) NOT NULL DEFAULT 'system',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT service_song_assignment_overrides_block_service_fk
        FOREIGN KEY (service_plan_id, service_plan_block_id)
        REFERENCES service_plan_blocks (service_plan_id, id) ON DELETE CASCADE,
    CONSTRAINT service_song_assignment_overrides_assignment_service_fk
        FOREIGN KEY (service_plan_id, base_service_assignment_id)
        REFERENCES service_team_assignments (service_plan_id, id) ON DELETE CASCADE,
    CONSTRAINT service_song_assignment_overrides_created_by_not_blank CHECK (btrim(created_by) <> ''),
    CONSTRAINT service_song_assignment_overrides_updated_by_not_blank CHECK (btrim(updated_by) <> ''),
    CONSTRAINT service_song_assignment_overrides_updated_at_not_before_created_at CHECK (updated_at >= created_at),
    CONSTRAINT service_song_assignment_overrides_block_assignment_unique UNIQUE (service_plan_block_id, base_service_assignment_id)
);

CREATE OR REPLACE FUNCTION enforce_song_assignment_override_block()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM service_plan_blocks
        WHERE service_plan_id = NEW.service_plan_id
          AND id = NEW.service_plan_block_id
          AND arrangement_id IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'song assignment override must reference a song service-plan block';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER service_song_assignment_overrides_song_block_trigger
    BEFORE INSERT OR UPDATE ON service_song_assignment_overrides
    FOR EACH ROW
    EXECUTE FUNCTION enforce_song_assignment_override_block();

INSERT INTO musician_roles (code, display_name, description, sort_order, system_default, local_extension) VALUES
    ('WORSHIP_LEADER', 'Worship leader', 'Primary leader for the worship set or service music team.', 10, true, false),
    ('VOCALIST', 'Vocalist', 'Singer assigned to lead or background vocal parts.', 20, true, false),
    ('INSTRUMENTALIST', 'Instrumentalist', 'Musician assigned to play an instrument.', 30, true, false),
    ('MUSIC_DIRECTOR', 'Music director', 'Musical director responsible for arrangement and band direction.', 40, true, false),
    ('TECH', 'Technical support', 'Audio, slides, stream, or other technical support role.', 50, true, false);

INSERT INTO instruments (code, display_name, description, sort_order, system_default, local_extension) VALUES
    ('ACOUSTIC_GUITAR', 'Acoustic guitar', 'Acoustic rhythm or lead guitar.', 10, true, false),
    ('ELECTRIC_GUITAR', 'Electric guitar', 'Electric rhythm or lead guitar.', 20, true, false),
    ('PIANO', 'Piano', 'Acoustic piano or primary piano patch.', 30, true, false),
    ('KEYS', 'Keys', 'Keyboard, pads, organ, or synth parts.', 40, true, false),
    ('BASS', 'Bass', 'Bass guitar or synth bass.', 50, true, false),
    ('DRUMS', 'Drums', 'Drum kit or primary percussion.', 60, true, false),
    ('PERCUSSION', 'Percussion', 'Auxiliary percussion.', 70, true, false);

INSERT INTO vocal_parts (code, display_name, description, sort_order, system_default, local_extension) VALUES
    ('LEAD', 'Lead vocal', 'Primary melody or song lead.', 10, true, false),
    ('ALTO', 'Alto', 'Alto harmony part.', 20, true, false),
    ('TENOR', 'Tenor', 'Tenor harmony part.', 30, true, false),
    ('BARITONE', 'Baritone', 'Baritone harmony part.', 40, true, false),
    ('SOPRANO', 'Soprano', 'Soprano harmony part.', 50, true, false),
    ('BACKGROUND', 'Background vocal', 'General backing vocal assignment.', 60, true, false);

INSERT INTO vocal_ranges (code, display_name, description, lowest_midi_note, highest_midi_note, sort_order, system_default, local_extension) VALUES
    ('LOW', 'Low range', 'Conservative low vocal range bucket for local refinement.', 40, 64, 10, true, false),
    ('MEDIUM', 'Medium range', 'Conservative medium vocal range bucket for local refinement.', 48, 72, 20, true, false),
    ('HIGH', 'High range', 'Conservative high vocal range bucket for local refinement.', 55, 79, 30, true, false),
    ('UNKNOWN', 'Unknown range', 'No maintained vocal range data.', NULL, NULL, 40, true, false);

INSERT INTO skill_levels (code, display_name, description, level_rank, sort_order, system_default, local_extension) VALUES
    ('BEGINNER', 'Beginner', 'Can serve with simple parts and support.', 1, 10, true, false),
    ('INTERMEDIATE', 'Intermediate', 'Comfortable with common worship arrangements.', 2, 20, true, false),
    ('ADVANCED', 'Advanced', 'Can handle complex arrangements and adapt quickly.', 3, 30, true, false),
    ('DIRECTOR', 'Director', 'Can lead, arrange, or direct other musicians.', 4, 40, true, false);

INSERT INTO serving_preferences (code, display_name, description, sort_order, system_default, local_extension) VALUES
    ('PREFERRED', 'Preferred', 'Preferred or primary serving area.', 10, true, false),
    ('AVAILABLE', 'Available', 'Available but not primary preference.', 20, true, false),
    ('LIMITED', 'Limited', 'Use sparingly or with constraints.', 30, true, false),
    ('DO_NOT_SCHEDULE', 'Do not schedule', 'Avoid scheduling unless manually overridden.', 40, true, false);

INSERT INTO assignment_statuses (code, display_name, description, sort_order, system_default, local_extension) VALUES
    ('REQUESTED', 'Requested', 'Invitation or assignment request has been sent but not answered.', 10, true, false),
    ('TENTATIVE', 'Tentative', 'Musician may be able to serve but is not confirmed.', 20, true, false),
    ('ACCEPTED', 'Accepted', 'Musician accepted the assignment or availability.', 30, true, false),
    ('DECLINED', 'Declined', 'Musician declined the assignment or window.', 40, true, false),
    ('UNAVAILABLE', 'Unavailable', 'Musician is unavailable for the window or assignment.', 50, true, false),
    ('SUBSTITUTE', 'Substitute', 'Musician is serving as a substitute for another assignment.', 60, true, false);

CREATE INDEX musicians_active_idx ON musicians (active);
CREATE INDEX musician_role_assignments_musician_id_idx ON musician_role_assignments (musician_id);
CREATE INDEX musician_instrument_assignments_musician_id_idx ON musician_instrument_assignments (musician_id);
CREATE INDEX musician_vocal_part_assignments_musician_id_idx ON musician_vocal_part_assignments (musician_id);
CREATE INDEX service_team_assignments_service_plan_id_idx ON service_team_assignments (service_plan_id);
CREATE INDEX rehearsal_events_service_plan_id_idx ON rehearsal_events (service_plan_id);
CREATE INDEX rehearsal_team_assignments_rehearsal_event_id_idx ON rehearsal_team_assignments (rehearsal_event_id);
CREATE INDEX service_song_assignment_overrides_context_idx ON service_song_assignment_overrides (service_plan_id, service_plan_block_id);

COMMENT ON TABLE musicians IS 'Instance-scoped musician/person records with optional account and contact linkage.';
COMMENT ON COLUMN musicians.account_principal IS 'Optional authenticated-account principal link; private contact fields are not required for assignment queries.';
COMMENT ON TABLE musician_roles IS 'Controlled vocabulary for musician team roles with stable deterministic codes and local-extension metadata.';
COMMENT ON TABLE instruments IS 'Controlled vocabulary for instruments with stable deterministic codes and local-extension metadata.';
COMMENT ON TABLE vocal_parts IS 'Controlled vocabulary for vocal parts with stable deterministic codes and local-extension metadata.';
COMMENT ON TABLE vocal_ranges IS 'Controlled vocabulary for conservative vocal range buckets and optional MIDI note bounds.';
COMMENT ON TABLE skill_levels IS 'Controlled vocabulary for musician and arrangement skill levels ordered by deterministic rank.';
COMMENT ON TABLE serving_preferences IS 'Controlled vocabulary for local serving preference semantics.';
COMMENT ON TABLE assignment_statuses IS 'Controlled vocabulary for availability and assignment states across service and rehearsal planning.';
COMMENT ON TABLE musician_availability_windows IS 'Musician availability windows for service/rehearsal scheduling using controlled assignment statuses.';
COMMENT ON TABLE service_team_assignments IS 'Service-scoped team assignments linked to musicians and controlled role/instrument/vocal vocabularies.';
COMMENT ON TABLE rehearsal_events IS 'Rehearsal windows tied to a service plan.';
COMMENT ON TABLE rehearsal_team_assignments IS 'Rehearsal-scoped team assignments linked to musicians and controlled assignment statuses.';
COMMENT ON TABLE service_song_assignment_overrides IS 'Song-block-specific assignment overrides constrained to the same service context as the base assignment.';
