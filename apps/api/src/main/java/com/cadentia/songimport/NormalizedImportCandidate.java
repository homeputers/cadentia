package com.cadentia.songimport;

import com.cadentia.catalog.model.ImportMethod;
import com.cadentia.catalog.model.LicenseType;
import java.time.Instant;
import java.util.Map;

public record NormalizedImportCandidate(
        String connectorId,
        String providerName,
        ImportMethod importMethod,
        String sourceRecordId,
        String sourceReference,
        LicenseType licenseType,
        Instant retrievedAt,
        String rawContentHash,
        String normalizedContentHash,
        String rawTitle,
        String normalizedTitle,
        String sourceArtistName,
        String ccliNumber,
        String sourcePayloadJson,
        Map<String, String> reviewNotes) {

    public NormalizedImportCandidate {
        connectorId = ImportConnectorValidation.requireText(connectorId, "connectorId");
        providerName = ImportConnectorValidation.requireText(providerName, "providerName");
        importMethod = ImportConnectorValidation.requireNonNull(importMethod, "importMethod");
        sourceRecordId = ImportConnectorValidation.requireText(sourceRecordId, "sourceRecordId");
        sourceReference = ImportConnectorValidation.requireText(sourceReference, "sourceReference");
        licenseType = ImportConnectorValidation.requireNonNull(licenseType, "licenseType");
        retrievedAt = ImportConnectorValidation.requireNonNull(retrievedAt, "retrievedAt");
        rawContentHash = ImportConnectorValidation.requireText(rawContentHash, "rawContentHash");
        normalizedContentHash = ImportConnectorValidation.requireText(normalizedContentHash, "normalizedContentHash");
        rawTitle = ImportConnectorValidation.requireText(rawTitle, "rawTitle");
        normalizedTitle = ImportConnectorValidation.requireText(normalizedTitle, "normalizedTitle");
        sourceArtistName = ImportConnectorValidation.requireOptionalText(sourceArtistName, "sourceArtistName");
        ccliNumber = ImportConnectorValidation.requireOptionalText(ccliNumber, "ccliNumber");
        sourcePayloadJson = ImportConnectorValidation.requireText(sourcePayloadJson, "sourcePayloadJson");
        reviewNotes = Map.copyOf(ImportConnectorValidation.requireNonNull(reviewNotes, "reviewNotes"));
    }
}
