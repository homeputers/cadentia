package com.cadentia.scraperadmin;

import com.cadentia.catalog.entity.ApprovalRecord;
import com.cadentia.catalog.entity.Arrangement;
import com.cadentia.catalog.entity.ProvenanceRecord;
import com.cadentia.catalog.entity.Song;
import java.util.List;

public record AdminMergeResult(
        Song song,
        Arrangement arrangement,
        List<ProvenanceRecord> provenanceRecords,
        List<ApprovalRecord> approvalRecords,
        boolean idempotentReplay) {
}
