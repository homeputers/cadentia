package com.cadentia.scraperadmin;

public record ImportCandidateRecord(
        String rowIdentifier,
        String externalCandidateId,
        String rawTitle,
        String sourceArtistName,
        String sourceArtistMetadataJson,
        String ccliNumber,
        String lyricsHash,
        String sourcePayloadJson,
        String importMethod,
        String sourceReference,
        String sourceCollectedAt,
        String operatorIdentity,
        String licenseType,
        String licenseEvidence) {

    public ImportCandidateRecord {
        rowIdentifier = blankToNull(rowIdentifier);
        externalCandidateId = blankToNull(externalCandidateId);
        rawTitle = blankToNull(rawTitle);
        sourceArtistName = blankToNull(sourceArtistName);
        sourceArtistMetadataJson = sourceArtistMetadataJson == null ? "{}" : sourceArtistMetadataJson.trim();
        ccliNumber = blankToNull(ccliNumber);
        lyricsHash = blankToNull(lyricsHash);
        sourcePayloadJson = sourcePayloadJson == null ? "{}" : sourcePayloadJson.trim();
        importMethod = blankToNull(importMethod);
        sourceReference = blankToNull(sourceReference);
        sourceCollectedAt = blankToNull(sourceCollectedAt);
        operatorIdentity = blankToNull(operatorIdentity);
        licenseType = blankToNull(licenseType);
        licenseEvidence = blankToNull(licenseEvidence);
    }

    public String displayIdentifier(int index) {
        if (rowIdentifier != null) {
            return rowIdentifier;
        }
        if (externalCandidateId != null) {
            return externalCandidateId;
        }
        return "row-" + index;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
