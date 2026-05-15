package com.cadentia.catalog.model;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class ApprovalStatusTransition {

    private static final Map<ApprovalStatus, Set<ApprovalStatus>> ALLOWED_TRANSITIONS = Map.of(
            ApprovalStatus.PENDING, EnumSet.of(ApprovalStatus.APPROVED, ApprovalStatus.REJECTED),
            ApprovalStatus.APPROVED, EnumSet.of(ApprovalStatus.NEEDS_REVIEW),
            ApprovalStatus.REJECTED, EnumSet.of(ApprovalStatus.NEEDS_REVIEW),
            ApprovalStatus.NEEDS_REVIEW, EnumSet.of(ApprovalStatus.APPROVED, ApprovalStatus.REJECTED));

    private ApprovalStatusTransition() {
    }

    public static void requireAllowed(ApprovalStatus fromStatus, ApprovalStatus toStatus) {
        CatalogValidation.requireEnum(fromStatus, "fromStatus");
        CatalogValidation.requireEnum(toStatus, "toStatus");
        if (fromStatus == toStatus || ALLOWED_TRANSITIONS.get(fromStatus).contains(toStatus)) {
            return;
        }
        throw new IllegalArgumentException(
                "Approval status transition from " + fromStatus + " to " + toStatus + " is not allowed");
    }
}
