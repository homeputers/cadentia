package com.cadentia.api.security;

import com.cadentia.catalog.model.ApprovalType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class ApprovalAuthorizationPolicy {

    public void requireApprovalPermission(ApprovalType approvalType) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            throw new AccessDeniedException("Access denied.");
        }
        boolean isAdmin = hasAuthority(authentication, RbacAuthorities.ROLE_ADMIN);
        boolean permitted = switch (approvalType) {
            case DOCTRINAL -> hasAuthority(authentication, RbacAuthorities.ROLE_DOCTRINAL_REVIEWER) || isAdmin;
            case MUSICAL -> hasAuthority(authentication, RbacAuthorities.ROLE_MUSICAL_REVIEWER) || isAdmin;
            default -> hasAuthority(authentication, RbacAuthorities.ROLE_CATALOG_EDITOR) || isAdmin;
        };
        if (!permitted) {
            throw new AccessDeniedException("Access denied.");
        }
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream().anyMatch(granted -> authority.equals(granted.getAuthority()));
    }
}
