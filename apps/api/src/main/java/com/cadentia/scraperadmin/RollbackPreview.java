package com.cadentia.scraperadmin;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RollbackPreview(
        UUID rollbackRequestId,
        RollbackTargetType targetType,
        UUID targetId,
        UUID importBatchId,
        boolean eligibilityImpacted,
        List<Map<String, Object>> impactedRecords,
        List<String> blockers) {}
