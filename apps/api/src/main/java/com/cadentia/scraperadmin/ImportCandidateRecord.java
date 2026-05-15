package com.cadentia.scraperadmin;

public record ImportCandidateRecord(
        String rowIdentifier,
        String externalCandidateId,
        String rawTitle,
        String sourceArtistName,
        String sourceArtistMetadataJson,
        String ccliNumber,
        String lyricsHash,
        String sourcePayloadJson) {

    public ImportCandidateRecord {
        rowIdentifier = blankToNull(rowIdentifier);
        externalCandidateId = blankToNull(externalCandidateId);
        rawTitle = blankToNull(rawTitle);
        sourceArtistName = blankToNull(sourceArtistName);
        sourceArtistMetadataJson = sourceArtistMetadataJson == null ? "{}" : sourceArtistMetadataJson.trim();
        ccliNumber = blankToNull(ccliNumber);
        lyricsHash = blankToNull(lyricsHash);
        sourcePayloadJson = sourcePayloadJson == null ? "{}" : sourcePayloadJson.trim();
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
