ALTER TABLE provenance_records
    DROP CONSTRAINT provenance_records_import_method_valid;

ALTER TABLE provenance_records
    ADD CONSTRAINT provenance_records_import_method_valid CHECK (
        import_method IN (
            'MANUAL_ENTRY',
            'CSV_IMPORT',
            'CHORDPRO_IMPORT',
            'OPENSONG_IMPORT',
            'MARKDOWN_IMPORT',
            'API_IMPORT',
            'SCRAPER_REVIEWED',
            'STARTER_PACKAGE_IMPORT',
            'TEST_FIXTURE'
        )
    );
