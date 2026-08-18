package com.cadentia.api.config;

import com.cadentia.api.security.RbacAuthorities;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Injects a fully-authenticated local-admin-approver principal into the
 * SecurityContextHolder for every request when running in local-development
 * mode. This allows @PreAuthorize method-security guards to pass even though
 * no OAuth / Basic-auth credentials are sent by the admin console.
 */
class LocalDevelopmentAuthenticationFilter extends OncePerRequestFilter {

    static final String LOCAL_ADMIN_ACTOR = "local-admin-approver";

    private static final UsernamePasswordAuthenticationToken LOCAL_ADMIN_TOKEN =
            new UsernamePasswordAuthenticationToken(
                    LOCAL_ADMIN_ACTOR,
                    null,
                    List.of(
                            new SimpleGrantedAuthority(RbacAuthorities.ROLE_ADMIN),
                            new SimpleGrantedAuthority(RbacAuthorities.ROLE_CATALOG_EDITOR),
                            new SimpleGrantedAuthority(RbacAuthorities.ROLE_DOCTRINAL_REVIEWER),
                            new SimpleGrantedAuthority(RbacAuthorities.ROLE_MUSICAL_REVIEWER),
                            new SimpleGrantedAuthority(RbacAuthorities.ROLE_WORSHIP_LEADER),
                            new SimpleGrantedAuthority(RbacAuthorities.ROLE_TEAM_SCHEDULER),
                            new SimpleGrantedAuthority(RbacAuthorities.ROLE_REPORTING_VIEWER),
                            new SimpleGrantedAuthority(RbacAuthorities.ROLE_INTEGRATION_MANAGER),
                            new SimpleGrantedAuthority("catalog.admin.review"),
                            new SimpleGrantedAuthority("catalog.admin.approve")));

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        SecurityContextHolder.getContext().setAuthentication(LOCAL_ADMIN_TOKEN);
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
