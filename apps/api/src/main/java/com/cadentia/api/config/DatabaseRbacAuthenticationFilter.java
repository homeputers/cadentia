package com.cadentia.api.config;

import com.cadentia.admin.AdminUserRecord;
import com.cadentia.admin.AdminUserRepository;
import com.cadentia.admin.AdminUserStatus;
import com.cadentia.api.security.RbacAuthorities;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Replaces provider role claims with the tenant-scoped RBAC assignments for admin requests. */
final class DatabaseRbacAuthenticationFilter extends OncePerRequestFilter {

    private final AdminUserRepository repository;
    private final String instanceId;

    DatabaseRbacAuthenticationFilter(AdminUserRepository repository, String instanceId) {
        this.repository = repository;
        this.instanceId = instanceId;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && request.getRequestURI().startsWith("/admin/")) {
            AdminUserRecord user = repository.findByExternalSubject(instanceId, authentication.getName()).orElse(null);
            List<SimpleGrantedAuthority> authorities = user != null && user.status() == AdminUserStatus.ACTIVE
                    ? user.roles().stream().map(DatabaseRbacAuthenticationFilter::toAuthority).toList()
                    : List.of();
            UsernamePasswordAuthenticationToken scoped = new UsernamePasswordAuthenticationToken(
                    authentication.getName(), authentication.getCredentials(), authorities);
            scoped.setDetails(authentication.getDetails());
            SecurityContextHolder.getContext().setAuthentication(scoped);
        }
        filterChain.doFilter(request, response);
    }

    private static SimpleGrantedAuthority toAuthority(String role) {
        return new SimpleGrantedAuthority(switch (role) {
            case "VIEWER" -> RbacAuthorities.ROLE_VIEWER;
            case "WORSHIP_LEADER" -> RbacAuthorities.ROLE_WORSHIP_LEADER;
            case "CATALOG_EDITOR" -> RbacAuthorities.ROLE_CATALOG_EDITOR;
            case "DOCTRINAL_REVIEWER" -> RbacAuthorities.ROLE_DOCTRINAL_REVIEWER;
            case "MUSICAL_REVIEWER" -> RbacAuthorities.ROLE_MUSICAL_REVIEWER;
            case "ADMIN" -> RbacAuthorities.ROLE_ADMIN;
            case "TEAM_SCHEDULER" -> RbacAuthorities.ROLE_TEAM_SCHEDULER;
            case "ASSIGNED_MUSICIAN" -> RbacAuthorities.ROLE_ASSIGNED_MUSICIAN;
            case "REPORTING_VIEWER" -> RbacAuthorities.ROLE_REPORTING_VIEWER;
            case "INTEGRATION_MANAGER" -> RbacAuthorities.ROLE_INTEGRATION_MANAGER;
            default -> throw new IllegalArgumentException("Unknown persisted admin role: " + role);
        });
    }
}
