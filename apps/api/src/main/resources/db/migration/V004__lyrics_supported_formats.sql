ALTER TABLE lyrics_documents
    DROP CONSTRAINT lyrics_documents_format_valid;

UPDATE lyrics_documents
SET format = CASE format
    WHEN 'PLAIN_TEXT' THEN 'plain_text'
    WHEN 'CHORDPRO' THEN 'chordpro'
    WHEN 'ONSONG' THEN 'onsong'
    WHEN 'MARKDOWN' THEN 'markdown'
    ELSE format
END;

ALTER TABLE lyrics_documents
    ADD CONSTRAINT lyrics_documents_format_valid
    CHECK (format IN ('plain_text', 'chordpro', 'onsong', 'markdown'));

COMMENT ON COLUMN lyrics_documents.format IS
    'ADR-004 declared lyrics format. Accepted values: plain_text, chordpro, onsong, markdown.';
