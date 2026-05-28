package com.cadentia.api.security;

import com.cadentia.catalog.model.ApprovalType;
import io.micrometer.core.instrument.Metrics;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class ApprovalAuthorizationPolicy {

    private final SecurityObservabilityRecorder observabilityRecorder;

    public ApprovalAuthorizationPolicy(SecurityObservabilityRecorder observabilityRecorder) {
        this.observabilityRecorder = observabilityRecorder;
    }

    public ApprovalAuthorizationPolicy() {
        this(new SecurityObservabilityRecorder(Metrics.globalRegistry));
    }

    public void requireApprovalPermission(ApprovalType approvalType) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            observabilityRecorder.recordAuthorizationDecision(
                    operationClass(approvalType), "UNKNOWN", "deny", "service_policy");
            throw new AccessDeniedException("Access denied.");
        }

        boolean isAdmin = hasAuthority(authentication, RbacAuthorities.ROLE_ADMIN);
        boolean permitted = switch (approvalType) {
            case DOCTRINAL -> hasAuthority(authentication, RbacAuthorities.ROLE_DOCTRINAL_REVIEWER) || isAdmin;
            case MUSICAL -> hasAuthority(authentication, RbacAuthorities.ROLE_MUSICAL_REVIEWER) || isAdmin;
            default -> hasAuthority(authentication, RbacAuthorities.ROLE_CATALOG_EDITOR) || isAdmin;
        };

        String actorRole = resolveActorRole(authentication);
        observabilityRecorder.recordAuthorizationDecision(
                operationClass(approvalType), actorRole, permitted ? "allow" : "deny", "service_policy");

        if (!permitted) {
            throw new AccessDeniedException("Access denied.");
        }

        observabilityRecorder.recordApprovalDecision(reviewDomain(approvalType), "approved", actorRole);
    }

    private String operationClass(ApprovalType approvalType) {
        return switch (approvalType) {
            case DOCTRINAL -> "catalog.approve.doctrinal";
            case MUSICAL -> "catalog.approve.musical";
            default -> "catalog.approve.editorial";
        };
    }

    private String reviewDomain(ApprovalType approvalType) {
        return switch (approvalType) {
            case DOCTRINAL -> "doctrinal";
            case MUSICAL -> "musical";
            default -> "editorial";
        };
    }

    private String resolveActorRole(Authentication authentication) {
        if (hasAuthority(authentication, RbacAuthorities.ROLE_ADMIN)) {
            return "ADMIN";
        }
        if (hasAuthority(authentication, RbacAuthorities.ROLE_DOCTRINAL_REVIEWER)) {
            return "DOCTRINAL_REVIEWER";
        }
        if (hasAuthority(authentication, RbacAuthorities.ROLE_MUSICAL_REVIEWER)) {
            return "MUSICAL_REVIEWER";
        }
        if (hasAuthority(authentication, RbacAuthorities.ROLE_CATALOG_EDITOR)) {
            return "CATALOG_EDITOR";
        }
        if (hasAuthority(authentication, RbacAuthorities.ROLE_WORSHIP_LEADER)) {
            return "WORSHIP_LEADER";
        }
        return "UNKNOWN";
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream().anyMatch(granted -> authority.equals(granted.getAuthority()));
    }
}
