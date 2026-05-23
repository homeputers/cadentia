package com.cadentia.scraperadmin;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RollbackPreview(
        UUID rollbackRequestId,
        String previewHash,
        RollbackTargetType targetType,
        UUID targetId,
        UUID importBatchId,
        boolean rollbackAllowed,
        boolean eligibilityImpacted,
        List<String> directImpactNodeIds,
        List<String> transitiveImpactNodeIds,
        List<String> eligibilityAffectedNodeIds,
        List<Map<String, Object>> impactedRecords,
        Map<String, Integer> impactSummary,
        List<String> blockingCodes,
        List<String> warningCodes,
        List<String> requiredManualActions) {}
