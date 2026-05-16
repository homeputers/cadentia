-- Reset script for ADR-001 minimal catalog fixture data.
-- Safe to run repeatedly in development or tests after the core catalog schema exists.

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
   OR (tag_type = 'AUDIENCE' AND slug = 'adr-001-fixture-congregational');

DELETE FROM songs
WHERE id = '681f8944-19c6-4b22-8c05-0babfaaac2fa'
   OR normalized_title = 'test-fixture-song-of-mercy';

DELETE FROM import_batches
WHERE id = '6721e04c-e2b7-4942-92de-d79c187fc6af'
   OR source_system = 'adr-001-minimal-test-fixture';
