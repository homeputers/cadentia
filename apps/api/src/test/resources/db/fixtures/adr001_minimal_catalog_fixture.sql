-- ADR-001 minimal catalog fixture data.
-- This file is test-scoped and intentionally excluded from production Flyway migrations.
-- Fixture records are labeled with [TEST FIXTURE], TEST_FIXTURE import provenance, and
-- non-approved review statuses so they cannot be mistaken for recommendable catalog content.

DELETE FROM approval_records
WHERE id IN (
    '0af216ec-c8b2-4728-af19-270d94d79f23',
    'a08c4f7c-cbfc-4bd1-89f1-9fbef81f130d',
    'a9a659b5-4b60-485f-bf19-88022914a233'
);

DELETE FROM provenance_records
WHERE id IN (
    '107213e6-2c43-481e-aac5-e4f5a6bcbf80',
    'a4508a4d-c375-455b-89ea-70d70a256c03',
    'a933141e-3e2b-456b-8ab5-920485e95f49'
);

DELETE FROM arrangement_tags
WHERE arrangement_id = '81d1126b-a6c8-4227-bc16-2dbde1ad5004'
   OR tag_id IN (
       '194da157-0b37-4272-8e4e-9676cc4aae08',
       '15ea7f8d-014f-4d97-851d-64b1d33d6fab'
   );

DELETE FROM song_tags
WHERE song_id = '681f8944-19c6-4b22-8c05-0babfaaac2fa'
   OR tag_id IN (
       '194da157-0b37-4272-8e4e-9676cc4aae08',
       '15ea7f8d-014f-4d97-851d-64b1d33d6fab'
   );

DELETE FROM lyrics_documents
WHERE id = '5238de29-e02e-418f-a4d5-b221bd18bb79'
   OR arrangement_id = '81d1126b-a6c8-4227-bc16-2dbde1ad5004';

DELETE FROM arrangements
WHERE id = '81d1126b-a6c8-4227-bc16-2dbde1ad5004'
   OR song_id = '681f8944-19c6-4b22-8c05-0babfaaac2fa';

DELETE FROM tags
WHERE id IN (
    '194da157-0b37-4272-8e4e-9676cc4aae08',
    '15ea7f8d-014f-4d97-851d-64b1d33d6fab'
)
   OR (tag_type = 'THEME' AND slug = 'adr-001-fixture-gratitude')
   OR (tag_type = 'SONG_ROLE' AND slug = 'adr-001-fixture-worship');

DELETE FROM songs
WHERE id = '681f8944-19c6-4b22-8c05-0babfaaac2fa'
   OR normalized_title = 'test-fixture-song-of-mercy';

DELETE FROM import_batches
WHERE id = '6721e04c-e2b7-4942-92de-d79c187fc6af'
   OR source_system = 'adr-001-minimal-test-fixture';

INSERT INTO import_batches (
    id, source_system, initiated_by, status, summary_json, completed_at
) VALUES (
    '6721e04c-e2b7-4942-92de-d79c187fc6af',
    'adr-001-minimal-test-fixture',
    'test-fixture-loader',
    'COMPLETED',
    '{"fixture":"ADR-001 minimal catalog", "productionApproved": false, "externalServices": false}'::jsonb,
    now()
);

INSERT INTO songs (
    id,
    canonical_title,
    normalized_title,
    primary_language,
    original_artist_display,
    composer_credits,
    ccli_number,
    year_written,
    song_status,
    doctrinal_notes
) VALUES (
    '681f8944-19c6-4b22-8c05-0babfaaac2fa',
    '[TEST FIXTURE] Song of Mercy',
    'test-fixture-song-of-mercy',
    'en',
    'Cadentia Test Fixtures',
    'Cadentia synthetic fixture text; not a production song record.',
    NULL,
    2026,
    'DRAFT',
    'Fixture-only row for ADR-001 schema tests; not recommendable or production-approved.'
);

INSERT INTO arrangements (
    id,
    song_id,
    name,
    normalized_name,
    source_type,
    language,
    musical_key,
    key_mode,
    tempo_bpm,
    time_signature,
    duration_seconds,
    energy_level,
    difficulty_level,
    default_for_song,
    is_active
) VALUES (
    '81d1126b-a6c8-4227-bc16-2dbde1ad5004',
    '681f8944-19c6-4b22-8c05-0babfaaac2fa',
    '[TEST FIXTURE] Simple Lead Sheet',
    'test-fixture-simple-lead-sheet',
    'CUSTOM',
    'en',
    'G',
    'MAJOR',
    96,
    '4/4',
    120,
    2,
    1,
    true,
    true
);

INSERT INTO lyrics_documents (
    id,
    arrangement_id,
    format,
    content,
    content_hash,
    version_number,
    is_current,
    contains_chords,
    contains_sections,
    source_reference,
    created_by
) VALUES (
    '5238de29-e02e-418f-a4d5-b221bd18bb79',
    '81d1126b-a6c8-4227-bc16-2dbde1ad5004',
    'PLAIN_TEXT',
    E'Holy God, You lead us with mercy\nWe answer with grateful praise\n',
    'sha256:6e7cd46c5b9e64a6410ea09a9c2db0abec4bfd8860c867b353c4367f61693275',
    1,
    true,
    false,
    false,
    'fixture://adr-001/minimal-catalog/song-of-mercy',
    'test-fixture-loader'
);

INSERT INTO tags (id, tag_type, name, slug, description, is_active)
VALUES
    (
        '194da157-0b37-4272-8e4e-9676cc4aae08',
        'THEME',
        '[TEST FIXTURE] Gratitude',
        'adr-001-fixture-gratitude',
        'Fixture-only theme tag for ADR-001 seed tests.',
        true
    ),
    (
        '15ea7f8d-014f-4d97-851d-64b1d33d6fab',
        'SONG_ROLE',
        '[TEST FIXTURE] Worship',
        'adr-001-fixture-worship',
        'Fixture-only song role tag for ADR-001 seed tests.',
        true
    );

INSERT INTO song_tags (song_id, tag_id)
VALUES
    ('681f8944-19c6-4b22-8c05-0babfaaac2fa', '194da157-0b37-4272-8e4e-9676cc4aae08'),
    ('681f8944-19c6-4b22-8c05-0babfaaac2fa', '15ea7f8d-014f-4d97-851d-64b1d33d6fab');

INSERT INTO arrangement_tags (arrangement_id, tag_id)
VALUES
    ('81d1126b-a6c8-4227-bc16-2dbde1ad5004', '194da157-0b37-4272-8e4e-9676cc4aae08'),
    ('81d1126b-a6c8-4227-bc16-2dbde1ad5004', '15ea7f8d-014f-4d97-851d-64b1d33d6fab');

INSERT INTO provenance_records (
    id,
    song_id,
    arrangement_id,
    lyrics_document_id,
    import_batch_id,
    source_system,
    source_uri,
    source_label,
    license_type,
    license_notes,
    import_method,
    confidence_score
) VALUES
    (
        '107213e6-2c43-481e-aac5-e4f5a6bcbf80',
        '681f8944-19c6-4b22-8c05-0babfaaac2fa',
        NULL,
        NULL,
        '6721e04c-e2b7-4942-92de-d79c187fc6af',
        'adr-001-minimal-test-fixture',
        'fixture://adr-001/minimal-catalog/song-of-mercy',
        'Synthetic fixture song metadata created for schema tests only.',
        'NOT_APPLICABLE',
        'No external catalog or copyrighted lyric source was imported.',
        'TEST_FIXTURE',
        1.0000
    ),
    (
        'a4508a4d-c375-455b-89ea-70d70a256c03',
        NULL,
        '81d1126b-a6c8-4227-bc16-2dbde1ad5004',
        NULL,
        '6721e04c-e2b7-4942-92de-d79c187fc6af',
        'adr-001-minimal-test-fixture',
        'fixture://adr-001/minimal-catalog/simple-lead-sheet',
        'Synthetic fixture arrangement metadata created for schema tests only.',
        'NOT_APPLICABLE',
        'Musical metadata is test-only and not an imported arrangement.',
        'TEST_FIXTURE',
        1.0000
    ),
    (
        'a933141e-3e2b-456b-8ab5-920485e95f49',
        NULL,
        NULL,
        '5238de29-e02e-418f-a4d5-b221bd18bb79',
        '6721e04c-e2b7-4942-92de-d79c187fc6af',
        'adr-001-minimal-test-fixture',
        'fixture://adr-001/minimal-catalog/song-of-mercy/lyrics-v1',
        'Synthetic two-line lyric fixture created for schema tests only.',
        'NOT_APPLICABLE',
        'Synthetic content is limited to fixture testing and is not a production lyric document.',
        'TEST_FIXTURE',
        1.0000
    );

INSERT INTO approval_records (
    id,
    song_id,
    arrangement_id,
    lyrics_document_id,
    approval_type,
    status,
    reviewer,
    review_notes
) VALUES
    (
        '0af216ec-c8b2-4728-af19-270d94d79f23',
        '681f8944-19c6-4b22-8c05-0babfaaac2fa',
        NULL,
        NULL,
        'CATALOG_INCLUSION',
        'PENDING',
        'test-fixture-reviewer',
        'Fixture row only; intentionally pending so it is not recommendable.'
    ),
    (
        'a08c4f7c-cbfc-4bd1-89f1-9fbef81f130d',
        NULL,
        '81d1126b-a6c8-4227-bc16-2dbde1ad5004',
        NULL,
        'MUSICAL',
        'PENDING',
        'test-fixture-reviewer',
        'Fixture arrangement is available for schema tests only.'
    ),
    (
        'a9a659b5-4b60-485f-bf19-88022914a233',
        NULL,
        NULL,
        '5238de29-e02e-418f-a4d5-b221bd18bb79',
        'COPYRIGHT',
        'NEEDS_CHANGES',
        'test-fixture-reviewer',
        'Fixture lyric document is synthetic and intentionally not copyright-approved for production use.'
    );
