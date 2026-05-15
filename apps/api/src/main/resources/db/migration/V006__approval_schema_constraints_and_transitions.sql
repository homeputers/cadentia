ALTER TABLE approval_records DROP CONSTRAINT approval_records_approval_type_valid;
ALTER TABLE approval_records DROP CONSTRAINT approval_records_status_valid;

UPDATE approval_records
SET approval_type = CASE approval_type
        WHEN 'COPYRIGHT' THEN 'LICENSING'
        WHEN 'CATALOG_INCLUSION' THEN 'EDITORIAL'
        ELSE approval_type
    END,
    status = CASE status
        WHEN 'NEEDS_CHANGES' THEN 'NEEDS_REVIEW'
        ELSE status
    END;

ALTER TABLE approval_records
    ADD CONSTRAINT approval_records_approval_type_valid
    CHECK (approval_type IN ('DOCTRINAL', 'EDITORIAL', 'MUSICAL', 'LICENSING'));

ALTER TABLE approval_records
    ADD CONSTRAINT approval_records_status_valid
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'NEEDS_REVIEW'));

CREATE UNIQUE INDEX IF NOT EXISTS approval_records_one_song_approval_type_idx
    ON approval_records (song_id, approval_type)
    WHERE song_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS approval_records_one_arrangement_approval_type_idx
    ON approval_records (arrangement_id, approval_type)
    WHERE arrangement_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS approval_records_one_lyrics_approval_type_idx
    ON approval_records (lyrics_document_id, approval_type)
    WHERE lyrics_document_id IS NOT NULL;

CREATE OR REPLACE FUNCTION approval_records_validate_status_transition()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.status = OLD.status THEN
        RETURN NEW;
    END IF;

    IF (OLD.status = 'PENDING' AND NEW.status IN ('APPROVED', 'REJECTED'))
            OR (OLD.status = 'APPROVED' AND NEW.status = 'NEEDS_REVIEW')
            OR (OLD.status = 'REJECTED' AND NEW.status = 'NEEDS_REVIEW')
            OR (OLD.status = 'NEEDS_REVIEW' AND NEW.status IN ('APPROVED', 'REJECTED')) THEN
        RETURN NEW;
    END IF;

    RAISE EXCEPTION 'Invalid approval status transition from % to %', OLD.status, NEW.status
        USING ERRCODE = '23514';
END;
$$;

DROP TRIGGER IF EXISTS approval_records_status_transition_valid ON approval_records;

CREATE TRIGGER approval_records_status_transition_valid
    BEFORE UPDATE OF status ON approval_records
    FOR EACH ROW
    EXECUTE FUNCTION approval_records_validate_status_transition();

COMMENT ON COLUMN approval_records.approval_type IS 'Controlled review category: doctrinal, editorial, musical, or licensing.';
COMMENT ON COLUMN approval_records.status IS 'Controlled review state: pending, approved, rejected, or needs_review.';
