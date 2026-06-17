package com.cadentia.api.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class PluginRegistryAuthorizationPolicy {
    public void requireManageIntegrations() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities().stream().noneMatch(authority ->
                RbacAuthorities.ROLE_ADMIN.equals(authority.getAuthority())
                        || RbacAuthorities.ROLE_INTEGRATION_MANAGER.equals(authority.getAuthority()))) {
            throw new AccessDeniedException("Access denied.");
        }
    }
}
