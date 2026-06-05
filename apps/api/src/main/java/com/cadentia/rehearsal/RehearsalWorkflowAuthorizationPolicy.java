package com.cadentia.rehearsal;

import com.cadentia.api.security.RbacAuthorities;
import com.cadentia.api.security.SecurityObservabilityRecorder;
import io.micrometer.core.instrument.Metrics;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class RehearsalWorkflowAuthorizationPolicy {

    private static final String DENIED_MESSAGE = "Access denied.";

    private final SecurityObservabilityRecorder observabilityRecorder;

    public RehearsalWorkflowAuthorizationPolicy(SecurityObservabilityRecorder observabilityRecorder) {
        this.observabilityRecorder = observabilityRecorder;
    }

    public RehearsalWorkflowAuthorizationPolicy() {
        this(new SecurityObservabilityRecorder(Metrics.globalRegistry));
    }

    public void requireWorkflowRead() {
        Authentication authentication = authentication();
        require(
                "rehearsal.workflow.read",
                hasAnyAuthority(
                        authentication,
                        RbacAuthorities.ROLE_ADMIN,
                        RbacAuthorities.ROLE_WORSHIP_LEADER,
                        RbacAuthorities.ROLE_TEAM_SCHEDULER,
                        RbacAuthorities.ROLE_ASSIGNED_MUSICIAN,
                        RbacAuthorities.ROLE_DOCTRINAL_REVIEWER,
                        RbacAuthorities.ROLE_MUSICAL_REVIEWER,
                        RbacAuthorities.ROLE_REPORTING_VIEWER));
    }

    public void requireActionResponseMutation() {
        Authentication authentication = authentication();
        require(
                "rehearsal.workflow.action_response",
                hasAnyAuthority(
                        authentication,
                        RbacAuthorities.ROLE_ADMIN,
                        RbacAuthorities.ROLE_WORSHIP_LEADER,
                        RbacAuthorities.ROLE_TEAM_SCHEDULER,
                        RbacAuthorities.ROLE_ASSIGNED_MUSICIAN));
    }

    public void requireWorkflowMutation() {
        Authentication authentication = authentication();
        require(
                "rehearsal.workflow.mutate",
                hasAnyAuthority(
                        authentication,
                        RbacAuthorities.ROLE_ADMIN,
                        RbacAuthorities.ROLE_WORSHIP_LEADER,
                        RbacAuthorities.ROLE_TEAM_SCHEDULER,
                        RbacAuthorities.ROLE_DOCTRINAL_REVIEWER,
                        RbacAuthorities.ROLE_MUSICAL_REVIEWER));
    }

    public void requireEmergencyCorrection() {
        Authentication authentication = authentication();
        require("rehearsal.workflow.emergency_correct", hasAuthority(authentication, RbacAuthorities.ROLE_ADMIN));
    }

    public String currentActor() {
        Authentication authentication = authentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "anonymous";
        }
        return authentication.getName();
    }

    public Set<String> currentActorRoles() {
        Authentication authentication = authentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toUnmodifiableSet());
    }

    private void require(String operationClass, boolean permitted) {
        Authentication authentication = authentication();
        observabilityRecorder.recordAuthorizationDecision(
                operationClass, resolveActorRole(authentication), permitted ? "allow" : "deny", "rehearsal_policy");
        if (!permitted) {
            throw new AccessDeniedException(DENIED_MESSAGE);
        }
    }

    private Authentication authentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private boolean hasAnyAuthority(Authentication authentication, String... authorities) {
        for (String authority : authorities) {
            if (hasAuthority(authentication, authority)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null
                && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                        .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }

    private String resolveActorRole(Authentication authentication) {
        if (hasAuthority(authentication, RbacAuthorities.ROLE_ADMIN)) {
            return "ADMIN";
        }
        if (hasAuthority(authentication, RbacAuthorities.ROLE_WORSHIP_LEADER)) {
            return "WORSHIP_LEADER";
        }
        if (hasAuthority(authentication, RbacAuthorities.ROLE_TEAM_SCHEDULER)) {
            return "TEAM_SCHEDULER";
        }
        if (hasAuthority(authentication, RbacAuthorities.ROLE_ASSIGNED_MUSICIAN)) {
            return "ASSIGNED_MUSICIAN";
        }
        if (hasAuthority(authentication, RbacAuthorities.ROLE_REPORTING_VIEWER)) {
            return "REPORTING_VIEWER";
        }
        return "UNKNOWN";
    }
}
