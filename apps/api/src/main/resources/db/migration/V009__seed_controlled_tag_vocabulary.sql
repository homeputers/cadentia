-- ADR-007 initial controlled vocabulary seed data.
-- These global defaults are intentionally broad, non-local, and
-- denomination-neutral. Product/admin review is required before expanding the
-- canonical vocabulary.

INSERT INTO tags (id, tag_type, name, slug, description, sort_order, is_active)
VALUES
    (
        '0f0d9f53-9347-4d7e-a0a0-916571f6f001',
        'THEME',
        'Gratitude',
        'theme-gratitude',
        'Songs centered on thanks, gratitude, or grateful response.',
        10,
        true
    ),
    (
        '0f0d9f53-9347-4d7e-a0a0-916571f6f002',
        'MOOD',
        'Celebratory',
        'mood-celebratory',
        'Songs with an explicitly joyful or celebratory tone.',
        10,
        true
    ),
    (
        '0f0d9f53-9347-4d7e-a0a0-916571f6f003',
        'OCCASION',
        'Gathering',
        'occasion-gathering',
        'Songs suitable for opening or gathering moments in a service.',
        10,
        true
    ),
    (
        '0f0d9f53-9347-4d7e-a0a0-916571f6f004',
        'SCRIPTURE',
        'Psalms',
        'scripture-psalms',
        'Songs with a reviewed connection to the book of Psalms.',
        10,
        true
    ),
    (
        '0f0d9f53-9347-4d7e-a0a0-916571f6f005',
        'SEASON',
        'Year Round',
        'season-year-round',
        'Songs broadly suitable outside a specific calendar season.',
        10,
        true
    ),
    (
        '0f0d9f53-9347-4d7e-a0a0-916571f6f006',
        'MUSICAL_STYLE',
        'Contemporary',
        'musical-style-contemporary',
        'Songs using a broadly contemporary worship musical style.',
        10,
        true
    ),
    (
        '0f0d9f53-9347-4d7e-a0a0-916571f6f007',
        'AUDIENCE',
        'Congregation',
        'audience-congregation',
        'Songs intended for full-congregation participation.',
        10,
        true
    );
