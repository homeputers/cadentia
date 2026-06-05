-- ADR-023 team assignment fixture data.
-- Test-scoped only; never run as a production Flyway migration.
-- All people are synthetic examples. Contact fields, private availability notes,
-- and sensitive readiness notes are deliberately omitted.
-- Expected recommendation diagnostics cite arrangement_suitability and
-- service_assignment evidence only. LLMs parse intent only and must not select
-- songs, infer suitability, or generate private personnel facts.

-- Run reset_team_assignment_fixture.sql before reloading this fixture in automated tests.

INSERT INTO import_batches (id, source_system, initiated_by, status, summary_json, completed_at)
VALUES (
    '23900000-0000-0000-0000-000000000001',
    'team-assignment-test-fixture',
    'test-fixture-loader',
    'COMPLETED',
    '{"fixture":"ADR-023 team assignment", "productionApproved":false, "personnelData":"synthetic"}'::jsonb,
    now()
);

INSERT INTO songs (id, canonical_title, normalized_title, primary_language, original_artist_display,
                   composer_credits, song_status, doctrinal_notes)
VALUES
    ('23800000-0000-0000-0000-000000000001', '[TEST FIXTURE] Sparse Mercy', 'test-fixture-sparse-mercy', 'en', 'Cadentia Test Fixtures', 'Synthetic fixture only.', 'APPROVED', 'Fixture only.'),
    ('23800000-0000-0000-0000-000000000002', '[TEST FIXTURE] Full Band Praise', 'test-fixture-full-band-praise', 'en', 'Cadentia Test Fixtures', 'Synthetic fixture only.', 'APPROVED', 'Fixture only.'),
    ('23800000-0000-0000-0000-000000000003', '[TEST FIXTURE] Vocal Benediction', 'test-fixture-vocal-benediction', 'en', 'Cadentia Test Fixtures', 'Synthetic fixture only.', 'APPROVED', 'Fixture only.'),
    ('23800000-0000-0000-0000-000000000004', '[TEST FIXTURE] Unapproved Team Favorite', 'test-fixture-unapproved-team-favorite', 'en', 'Cadentia Test Fixtures', 'Synthetic fixture only.', 'DRAFT', 'Fixture intentionally unapproved; team readiness must not make it recommendable.');

INSERT INTO arrangements (id, song_id, name, normalized_name, source_type, language, musical_key,
                          key_mode, tempo_bpm, time_signature, duration_seconds, energy_level,
                          difficulty_level, default_for_song, is_active)
VALUES
    ('23700000-0000-0000-0000-000000000001', '23800000-0000-0000-0000-000000000001', '[TEST FIXTURE] Sparse Acoustic', 'test-fixture-sparse-acoustic', 'CUSTOM', 'en', 'G', 'MAJOR', 88, '4/4', 180, 2, 1, true, true),
    ('23700000-0000-0000-0000-000000000002', '23800000-0000-0000-0000-000000000002', '[TEST FIXTURE] Full Band', 'test-fixture-full-band', 'CUSTOM', 'en', 'A', 'MAJOR', 124, '4/4', 240, 5, 4, true, true),
    ('23700000-0000-0000-0000-000000000003', '23800000-0000-0000-0000-000000000003', '[TEST FIXTURE] Vocal Led', 'test-fixture-vocal-led', 'CUSTOM', 'en', 'D', 'MAJOR', 72, '4/4', 210, 1, 2, true, true),
    ('23700000-0000-0000-0000-000000000004', '23800000-0000-0000-0000-000000000004', '[TEST FIXTURE] Unapproved Full Team', 'test-fixture-unapproved-full-team', 'CUSTOM', 'en', 'E', 'MAJOR', 112, '4/4', 220, 4, 3, true, true);

INSERT INTO lyrics_documents (id, arrangement_id, format, content, content_hash, version_number,
                              is_current, contains_chords, contains_sections, source_reference, created_by)
VALUES
    ('23600000-0000-0000-0000-000000000001', '23700000-0000-0000-0000-000000000001', 'plain_text', E'Synthetic mercy fixture line\n', 'sha256:team-assignment-sparse', 1, true, false, false, 'fixture://adr-023/sparse', 'test-fixture-loader'),
    ('23600000-0000-0000-0000-000000000002', '23700000-0000-0000-0000-000000000002', 'plain_text', E'Synthetic praise fixture line\n', 'sha256:team-assignment-full-band', 1, true, false, false, 'fixture://adr-023/full-band', 'test-fixture-loader'),
    ('23600000-0000-0000-0000-000000000003', '23700000-0000-0000-0000-000000000003', 'plain_text', E'Synthetic benediction fixture line\n', 'sha256:team-assignment-vocal', 1, true, false, false, 'fixture://adr-023/vocal-led', 'test-fixture-loader'),
    ('23600000-0000-0000-0000-000000000004', '23700000-0000-0000-0000-000000000004', 'plain_text', E'Synthetic unapproved fixture line\n', 'sha256:team-assignment-unapproved', 1, true, false, false, 'fixture://adr-023/unapproved', 'test-fixture-loader');

INSERT INTO provenance_records (id, song_id, arrangement_id, lyrics_document_id, import_batch_id,
                                source_system, source_uri, source_label, license_type, license_notes,
                                import_method, confidence_score)
VALUES
    ('23500000-0000-0000-0000-000000000001', '23800000-0000-0000-0000-000000000001', NULL, NULL, '23900000-0000-0000-0000-000000000001', 'team-assignment-test-fixture', 'fixture://adr-023/sparse/song', 'Synthetic song fixture.', 'NOT_APPLICABLE', 'Synthetic.', 'TEST_FIXTURE', 1.0),
    ('23500000-0000-0000-0000-000000000002', NULL, '23700000-0000-0000-0000-000000000001', NULL, '23900000-0000-0000-0000-000000000001', 'team-assignment-test-fixture', 'fixture://adr-023/sparse/arrangement', 'Synthetic arrangement fixture.', 'NOT_APPLICABLE', 'Synthetic.', 'TEST_FIXTURE', 1.0);

-- Approved-only gates: first three arrangements are recommendable; fourth remains non-recommendable.
INSERT INTO approval_records (id, song_id, arrangement_id, lyrics_document_id, approval_type, status, reviewer, review_notes)
SELECT gen_random_uuid(), song_id, NULL, NULL, approval_type, status, 'test-fixture-reviewer', 'ADR-023 synthetic approval fixture.'
FROM (VALUES
    ('23800000-0000-0000-0000-000000000001'::uuid, 'APPROVED'),
    ('23800000-0000-0000-0000-000000000002'::uuid, 'APPROVED'),
    ('23800000-0000-0000-0000-000000000003'::uuid, 'APPROVED'),
    ('23800000-0000-0000-0000-000000000004'::uuid, 'PENDING')
) AS s(song_id, status)
CROSS JOIN (VALUES ('DOCTRINAL'), ('EDITORIAL'), ('LICENSING')) AS t(approval_type);

INSERT INTO approval_records (id, song_id, arrangement_id, lyrics_document_id, approval_type, status, reviewer, review_notes)
SELECT gen_random_uuid(), NULL, arrangement_id, NULL, approval_type, status, 'test-fixture-reviewer', 'ADR-023 synthetic arrangement approval fixture.'
FROM (VALUES
    ('23700000-0000-0000-0000-000000000001'::uuid, 'APPROVED'),
    ('23700000-0000-0000-0000-000000000002'::uuid, 'APPROVED'),
    ('23700000-0000-0000-0000-000000000003'::uuid, 'APPROVED'),
    ('23700000-0000-0000-0000-000000000004'::uuid, 'PENDING')
) AS a(arrangement_id, status)
CROSS JOIN (VALUES ('MUSICAL'), ('EDITORIAL')) AS t(approval_type);

INSERT INTO approval_records (id, song_id, arrangement_id, lyrics_document_id, approval_type, status, reviewer, review_notes)
SELECT gen_random_uuid(), NULL, NULL, lyrics_document_id, approval_type, status, 'test-fixture-reviewer', 'ADR-023 synthetic lyrics approval fixture.'
FROM (VALUES
    ('23600000-0000-0000-0000-000000000001'::uuid, 'APPROVED'),
    ('23600000-0000-0000-0000-000000000002'::uuid, 'APPROVED'),
    ('23600000-0000-0000-0000-000000000003'::uuid, 'APPROVED'),
    ('23600000-0000-0000-0000-000000000004'::uuid, 'PENDING')
) AS l(lyrics_document_id, status)
CROSS JOIN (VALUES ('DOCTRINAL'), ('EDITORIAL'), ('LICENSING')) AS t(approval_type);

INSERT INTO arrangement_suitability_profiles (id, arrangement_id, version_number, is_current,
                                              vocal_configuration, lead_vocal_low_midi_note,
                                              lead_vocal_high_midi_note, required_backing_vocal_count,
                                              review_notes, governance_action_ref, created_by)
VALUES
    ('23300000-0000-0000-0000-000000000001', '23700000-0000-0000-0000-000000000001', 1, true, 'SOLO_LEAD', 50, 72, 0, 'Sparse acoustic fixture requirement.', 'fixture://adr-023/governance/sparse-v1', 'test-fixture-loader'),
    ('23300000-0000-0000-0000-000000000002', '23700000-0000-0000-0000-000000000002', 1, true, 'LEAD_WITH_BACKING', 48, 76, 2, 'Full-band fixture requirement.', 'fixture://adr-023/governance/full-band-v1', 'test-fixture-loader'),
    ('23300000-0000-0000-0000-000000000003', '23700000-0000-0000-0000-000000000003', 1, true, 'LEAD_WITH_BACKING', 52, 74, 2, 'Vocal-led fixture requirement.', 'fixture://adr-023/governance/vocal-led-v1', 'test-fixture-loader'),
    ('23300000-0000-0000-0000-000000000004', '23700000-0000-0000-0000-000000000004', 1, true, 'LEAD_WITH_BACKING', 48, 76, 1, 'Unapproved arrangement fixture; should not appear in approved suitability views.', 'fixture://adr-023/governance/unapproved-v1', 'test-fixture-loader');

INSERT INTO arrangement_suitability_slots (suitability_profile_id, requirement_type, role_code, instrument_code,
                                           vocal_part_code, minimum_skill_level_code, minimum_count,
                                           coverage_rule, review_notes, sort_order)
VALUES
    ('23300000-0000-0000-0000-000000000001', 'REQUIRED', 'INSTRUMENTALIST', 'ACOUSTIC_GUITAR', NULL, 'INTERMEDIATE', 1, 'AT_LEAST', 'Sparse fixture required guitar.', 1),
    ('23300000-0000-0000-0000-000000000001', 'REQUIRED', 'VOCALIST', NULL, 'LEAD', 'INTERMEDIATE', 1, 'AT_LEAST', 'Sparse fixture lead vocal.', 2),
    ('23300000-0000-0000-0000-000000000002', 'REQUIRED', 'INSTRUMENTALIST', 'DRUMS', NULL, 'INTERMEDIATE', 1, 'AT_LEAST', 'Full band drums.', 1),
    ('23300000-0000-0000-0000-000000000002', 'REQUIRED', 'INSTRUMENTALIST', 'BASS', NULL, 'INTERMEDIATE', 1, 'AT_LEAST', 'Full band bass.', 2),
    ('23300000-0000-0000-0000-000000000002', 'REQUIRED', 'INSTRUMENTALIST', 'PIANO', NULL, 'ADVANCED', 1, 'AT_LEAST', 'Full band piano skill floor.', 3),
    ('23300000-0000-0000-0000-000000000002', 'OPTIONAL', 'INSTRUMENTALIST', 'ELECTRIC_GUITAR', NULL, 'INTERMEDIATE', 1, 'AT_LEAST', 'Optional electric guitar bonus.', 4),
    ('23300000-0000-0000-0000-000000000003', 'REQUIRED', 'VOCALIST', NULL, 'LEAD', 'ADVANCED', 1, 'AT_LEAST', 'Vocal-led lead.', 1),
    ('23300000-0000-0000-0000-000000000003', 'REQUIRED', 'VOCALIST', NULL, 'BACKGROUND', 'INTERMEDIATE', 2, 'AT_LEAST', 'Vocal-led backing vocals.', 2),
    ('23300000-0000-0000-0000-000000000004', 'REQUIRED', 'INSTRUMENTALIST', 'DRUMS', NULL, 'INTERMEDIATE', 1, 'AT_LEAST', 'Unapproved fixture slot.', 1);

INSERT INTO musicians (id, display_name, primary_vocal_range_code, comfortable_low_midi_note,
                       comfortable_high_midi_note, serving_preference_code, notes, created_by, updated_by)
VALUES
    ('23100000-0000-0000-0000-000000000001', '[TEST FIXTURE] Avery Lead', 'MEDIUM', 48, 74, 'AVAILABLE', NULL, 'test-fixture-loader', 'test-fixture-loader'),
    ('23100000-0000-0000-0000-000000000002', '[TEST FIXTURE] Blair Acoustic', NULL, NULL, NULL, 'AVAILABLE', NULL, 'test-fixture-loader', 'test-fixture-loader'),
    ('23100000-0000-0000-0000-000000000003', '[TEST FIXTURE] Casey Drums', NULL, NULL, NULL, 'AVAILABLE', NULL, 'test-fixture-loader', 'test-fixture-loader'),
    ('23100000-0000-0000-0000-000000000004', '[TEST FIXTURE] Devon Bass', NULL, NULL, NULL, 'AVAILABLE', NULL, 'test-fixture-loader', 'test-fixture-loader'),
    ('23100000-0000-0000-0000-000000000005', '[TEST FIXTURE] Ellis Keys', NULL, NULL, NULL, 'AVAILABLE', NULL, 'test-fixture-loader', 'test-fixture-loader'),
    ('23100000-0000-0000-0000-000000000006', '[TEST FIXTURE] Finley Harmony', 'HIGH', 55, 76, 'AVAILABLE', NULL, 'test-fixture-loader', 'test-fixture-loader'),
    ('23100000-0000-0000-0000-000000000007', '[TEST FIXTURE] Gray Substitute', NULL, NULL, NULL, 'AVAILABLE', NULL, 'test-fixture-loader', 'test-fixture-loader');

INSERT INTO teams (id, code, display_name)
VALUES ('23200000-0000-0000-0000-000000000001', 'TEAM_ASSIGNMENT_FIXTURE_TEAM', '[TEST FIXTURE] ADR-023 Worship Team');

INSERT INTO team_memberships (team_id, musician_id, role_code, started_on)
SELECT '23200000-0000-0000-0000-000000000001', id, 'INSTRUMENTALIST', DATE '2026-01-01'
FROM musicians WHERE id::text LIKE '23100000-0000-0000-0000-0000000000%';

INSERT INTO musician_role_assignments (musician_id, role_code, skill_level_code, serving_preference_code)
VALUES
    ('23100000-0000-0000-0000-000000000001', 'VOCALIST', 'ADVANCED', 'AVAILABLE'),
    ('23100000-0000-0000-0000-000000000002', 'INSTRUMENTALIST', 'INTERMEDIATE', 'AVAILABLE'),
    ('23100000-0000-0000-0000-000000000003', 'INSTRUMENTALIST', 'INTERMEDIATE', 'AVAILABLE'),
    ('23100000-0000-0000-0000-000000000004', 'INSTRUMENTALIST', 'INTERMEDIATE', 'AVAILABLE'),
    ('23100000-0000-0000-0000-000000000005', 'INSTRUMENTALIST', 'ADVANCED', 'AVAILABLE'),
    ('23100000-0000-0000-0000-000000000006', 'VOCALIST', 'INTERMEDIATE', 'AVAILABLE'),
    ('23100000-0000-0000-0000-000000000007', 'INSTRUMENTALIST', 'INTERMEDIATE', 'AVAILABLE');

INSERT INTO musician_instrument_assignments (musician_id, instrument_code, skill_level_code, serving_preference_code)
VALUES
    ('23100000-0000-0000-0000-000000000002', 'ACOUSTIC_GUITAR', 'INTERMEDIATE', 'AVAILABLE'),
    ('23100000-0000-0000-0000-000000000003', 'DRUMS', 'INTERMEDIATE', 'AVAILABLE'),
    ('23100000-0000-0000-0000-000000000004', 'BASS', 'INTERMEDIATE', 'AVAILABLE'),
    ('23100000-0000-0000-0000-000000000005', 'PIANO', 'ADVANCED', 'AVAILABLE'),
    ('23100000-0000-0000-0000-000000000007', 'ELECTRIC_GUITAR', 'INTERMEDIATE', 'AVAILABLE');

INSERT INTO musician_vocal_part_assignments (musician_id, vocal_part_code, skill_level_code, serving_preference_code)
VALUES
    ('23100000-0000-0000-0000-000000000001', 'LEAD', 'ADVANCED', 'AVAILABLE'),
    ('23100000-0000-0000-0000-000000000006', 'BACKGROUND', 'INTERMEDIATE', 'AVAILABLE');

INSERT INTO service_plans (id, service_date_time, title, theme, scripture, notes, status)
VALUES
    ('23000000-0000-0000-0000-000000000001', '2026-07-05T15:00:00Z', '[TEST FIXTURE] Sparse acoustic service', 'Mercy', 'Psalm 23', 'Fixture service; no private notes.', 'draft'),
    ('23000000-0000-0000-0000-000000000002', '2026-07-12T15:00:00Z', '[TEST FIXTURE] Full band service', 'Praise', 'Psalm 150', 'Fixture service; no private notes.', 'draft'),
    ('23000000-0000-0000-0000-000000000003', '2026-07-19T15:00:00Z', '[TEST FIXTURE] Vocal-led service', 'Blessing', 'Numbers 6', 'Fixture service; no private notes.', 'draft'),
    ('23000000-0000-0000-0000-000000000004', '2026-07-26T15:00:00Z', '[TEST FIXTURE] Incomplete-team service', 'Trust', 'Psalm 46', 'Fixture service; unapproved arrangement remains excluded.', 'draft');

INSERT INTO service_plan_blocks (id, service_plan_id, block_type, position_index, arrangement_id, service_notes)
VALUES
    ('23b00000-0000-0000-0000-000000000001', '23000000-0000-0000-0000-000000000001', 'worship', 1, '23700000-0000-0000-0000-000000000001', 'Expected diagnostics: pass required_instrument_coverage and lead_vocal_range.'),
    ('23b00000-0000-0000-0000-000000000002', '23000000-0000-0000-0000-000000000002', 'praise', 1, '23700000-0000-0000-0000-000000000002', 'Expected diagnostics: optional electric guitar may score when present.'),
    ('23b00000-0000-0000-0000-000000000003', '23000000-0000-0000-0000-000000000003', 'worship', 1, '23700000-0000-0000-0000-000000000003', 'Expected diagnostics: vocal backing count is short and should warn/fail by profile.'),
    ('23b00000-0000-0000-0000-000000000004', '23000000-0000-0000-0000-000000000004', 'praise', 1, '23700000-0000-0000-0000-000000000004', 'Expected diagnostics: unapproved arrangement is not in approved suitability views.');

INSERT INTO rehearsal_events (id, service_plan_id, starts_at, ends_at, location, notes)
VALUES
    ('23c00000-0000-0000-0000-000000000001', '23000000-0000-0000-0000-000000000001', '2026-07-02T23:00:00Z', '2026-07-03T01:00:00Z', 'Fixture Room A', 'Fixture rehearsal.'),
    ('23c00000-0000-0000-0000-000000000002', '23000000-0000-0000-0000-000000000002', '2026-07-09T23:00:00Z', '2026-07-10T01:00:00Z', 'Fixture Room A', 'Fixture rehearsal.'),
    ('23c00000-0000-0000-0000-000000000003', '23000000-0000-0000-0000-000000000003', '2026-07-16T23:00:00Z', '2026-07-17T01:00:00Z', 'Fixture Room B', 'Fixture rehearsal.'),
    ('23c00000-0000-0000-0000-000000000004', '23000000-0000-0000-0000-000000000004', '2026-07-23T23:00:00Z', '2026-07-24T01:00:00Z', 'Fixture Room B', 'Fixture rehearsal.');

INSERT INTO service_team_assignments (id, service_plan_id, musician_id, role_code, instrument_code, vocal_part_code,
                                      status_code, substitute_for_assignment_id, notes, created_by, updated_by)
VALUES
    ('23a00000-0000-0000-0000-000000000001', '23000000-0000-0000-0000-000000000001', '23100000-0000-0000-0000-000000000001', 'VOCALIST', NULL, 'LEAD', 'ACCEPTED', NULL, NULL, 'test-fixture-loader', 'test-fixture-loader'),
    ('23a00000-0000-0000-0000-000000000002', '23000000-0000-0000-0000-000000000001', '23100000-0000-0000-0000-000000000002', 'INSTRUMENTALIST', 'ACOUSTIC_GUITAR', NULL, 'ACCEPTED', NULL, NULL, 'test-fixture-loader', 'test-fixture-loader'),
    ('23a00000-0000-0000-0000-000000000003', '23000000-0000-0000-0000-000000000002', '23100000-0000-0000-0000-000000000003', 'INSTRUMENTALIST', 'DRUMS', NULL, 'ACCEPTED', NULL, NULL, 'test-fixture-loader', 'test-fixture-loader'),
    ('23a00000-0000-0000-0000-000000000004', '23000000-0000-0000-0000-000000000002', '23100000-0000-0000-0000-000000000004', 'INSTRUMENTALIST', 'BASS', NULL, 'ACCEPTED', NULL, NULL, 'test-fixture-loader', 'test-fixture-loader'),
    ('23a00000-0000-0000-0000-000000000005', '23000000-0000-0000-0000-000000000002', '23100000-0000-0000-0000-000000000005', 'INSTRUMENTALIST', 'PIANO', NULL, 'ACCEPTED', NULL, NULL, 'test-fixture-loader', 'test-fixture-loader'),
    ('23a00000-0000-0000-0000-000000000006', '23000000-0000-0000-0000-000000000002', '23100000-0000-0000-0000-000000000007', 'INSTRUMENTALIST', 'ELECTRIC_GUITAR', NULL, 'SUBSTITUTE', '23a00000-0000-0000-0000-000000000003', NULL, 'test-fixture-loader', 'test-fixture-loader'),
    ('23a00000-0000-0000-0000-000000000007', '23000000-0000-0000-0000-000000000003', '23100000-0000-0000-0000-000000000001', 'VOCALIST', NULL, 'LEAD', 'ACCEPTED', NULL, NULL, 'test-fixture-loader', 'test-fixture-loader'),
    ('23a00000-0000-0000-0000-000000000008', '23000000-0000-0000-0000-000000000003', '23100000-0000-0000-0000-000000000006', 'VOCALIST', NULL, 'BACKGROUND', 'TENTATIVE', NULL, NULL, 'test-fixture-loader', 'test-fixture-loader'),
    ('23a00000-0000-0000-0000-000000000009', '23000000-0000-0000-0000-000000000004', '23100000-0000-0000-0000-000000000003', 'INSTRUMENTALIST', 'DRUMS', NULL, 'UNAVAILABLE', NULL, NULL, 'test-fixture-loader', 'test-fixture-loader');

INSERT INTO rehearsal_team_assignments (rehearsal_event_id, service_plan_id, musician_id, role_code,
                                        instrument_code, vocal_part_code, status_code, notes, created_by, updated_by)
SELECT r.id, s.service_plan_id, s.musician_id, s.role_code, s.instrument_code, s.vocal_part_code, s.status_code,
       NULL, 'test-fixture-loader', 'test-fixture-loader'
FROM service_team_assignments s
JOIN rehearsal_events r ON r.service_plan_id = s.service_plan_id;

INSERT INTO musician_availability_windows (musician_id, starts_at, ends_at, status_code, service_plan_id, notes)
VALUES ('23100000-0000-0000-0000-000000000003', '2026-07-26T00:00:00Z', '2026-07-27T00:00:00Z', 'UNAVAILABLE', '23000000-0000-0000-0000-000000000004', NULL);

INSERT INTO readiness_notes (id, service_plan_id, scope_type, scope_id, readiness_status_code,
                             objective_blockers, missing_people, unresolved_arrangement_conflicts,
                             human_note, privacy_classification, updated_by)
VALUES
    ('23d00000-0000-0000-0000-000000000001', '23000000-0000-0000-0000-000000000001', 'SERVICE_TEAM', '23000000-0000-0000-0000-000000000001', 'READY', '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, NULL, 'TEAM_PRIVATE', 'test-fixture-loader'),
    ('23d00000-0000-0000-0000-000000000002', '23000000-0000-0000-0000-000000000003', 'SERVICE_TEAM', '23000000-0000-0000-0000-000000000003', 'AT_RISK', '["BACKING_VOCAL_COUNT_SHORT"]'::jsonb, '["BACKGROUND_VOCAL_2"]'::jsonb, '[]'::jsonb, NULL, 'TEAM_PRIVATE', 'test-fixture-loader'),
    ('23d00000-0000-0000-0000-000000000003', '23000000-0000-0000-0000-000000000004', 'SERVICE_TEAM', '23000000-0000-0000-0000-000000000004', 'BLOCKED', '["UNAVAILABLE_DRUMMER", "UNAPPROVED_ARRANGEMENT_NOT_RECOMMENDABLE"]'::jsonb, '["DRUMS"]'::jsonb, '[{"arrangementId":"23700000-0000-0000-0000-000000000004", "reason":"approval_gate"}]'::jsonb, NULL, 'TEAM_PRIVATE', 'test-fixture-loader');
