package com.cadentia.scraperadmin;

import java.util.UUID;

public record RollbackExecutionResult(UUID rollbackRequestId, String action, UUID auditEventId) {}
