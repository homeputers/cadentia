package com.cadentia.reng;

import com.cadentia.catalog.model.ApprovalStatus;

public record ApprovalGateSummary(
        ApprovalStatus songDoctrinalStatus,
        ApprovalStatus songEditorialStatus,
        ApprovalStatus songLicensingStatus,
        ApprovalStatus arrangementMusicalStatus,
        ApprovalStatus arrangementEditorialStatus,
        ApprovalStatus lyricsDoctrinalStatus,
        ApprovalStatus lyricsEditorialStatus,
        ApprovalStatus lyricsLicensingStatus) {
}
