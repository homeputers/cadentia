package com.cadentia.catalog.entity;

import com.cadentia.catalog.model.ImportMethod;
import com.cadentia.catalog.model.LicenseType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProvenanceRecord(
        UUID id,
        UUID songId,
        UUID arrangementId,
        UUID lyricsDocumentId,
        UUID importBatchId,
        String sourceSystem,
        String sourceUri,
        String sourceLabel,
        LicenseType licenseType,
        String licenseNotes,
        ImportMethod importMethod,
        BigDecimal confidenceScore,
        Instant capturedAt) {
}
