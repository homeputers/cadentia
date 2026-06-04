package com.cadentia.api.security;

import com.cadentia.team.TeamPlanningModels.MusicianRecord;
import io.micrometer.core.instrument.Metrics;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class PersonnelAuthorizationPolicy {

    private static final String DENIED_MESSAGE = "Access denied.";

    private final SecurityObservabilityRecorder observabilityRecorder;

    public PersonnelAuthorizationPolicy(SecurityObservabilityRecorder observabilityRecorder) {
        this.observabilityRecorder = observabilityRecorder;
    }

    public PersonnelAuthorizationPolicy() {
        this(new SecurityObservabilityRecorder(Metrics.globalRegistry));
    }

    public boolean canReadRoster() {
        Authentication authentication = authentication();
        return hasAnyAuthority(
                authentication,
                RbacAuthorities.ROLE_ADMIN,
                RbacAuthorities.ROLE_WORSHIP_LEADER,
                RbacAuthorities.ROLE_TEAM_SCHEDULER,
                RbacAuthorities.ROLE_REPORTING_VIEWER,
                RbacAuthorities.ROLE_DOCTRINAL_REVIEWER,
                RbacAuthorities.ROLE_MUSICAL_REVIEWER);
    }

    public void requireRosterRead() {
        require("personnel.roster.read", canReadRoster());
    }

    public boolean canReadMusicianProfile(MusicianRecord musician) {
        Authentication authentication = authentication();
        return canReadRoster() || isAssignedMusician(authentication, musician);
    }

    public void requireMusicianProfileRead(MusicianRecord musician) {
        require("personnel.profile.read", canReadMusicianProfile(musician));
    }

    public boolean canReadPrivateContactData(MusicianRecord musician) {
        Authentication authentication = authentication();
        return hasAnyAuthority(
                        authentication,
                        RbacAuthorities.ROLE_ADMIN,
                        RbacAuthorities.ROLE_WORSHIP_LEADER,
                        RbacAuthorities.ROLE_TEAM_SCHEDULER)
                || isAssignedMusician(authentication, musician);
    }

    public boolean canReadSensitiveSkillAndRangeData(MusicianRecord musician) {
        Authentication authentication = authentication();
        return hasAnyAuthority(
                        authentication,
                        RbacAuthorities.ROLE_ADMIN,
                        RbacAuthorities.ROLE_WORSHIP_LEADER,
                        RbacAuthorities.ROLE_TEAM_SCHEDULER)
                || isAssignedMusician(authentication, musician);
    }

    public boolean canReadAvailabilityNotes(MusicianRecord musician) {
        Authentication authentication = authentication();
        return hasAnyAuthority(
                        authentication,
                        RbacAuthorities.ROLE_ADMIN,
                        RbacAuthorities.ROLE_WORSHIP_LEADER,
                        RbacAuthorities.ROLE_TEAM_SCHEDULER)
                || isAssignedMusician(authentication, musician);
    }

    public boolean canReadReadinessNotes() {
        Authentication authentication = authentication();
        return hasAnyAuthority(
                authentication,
                RbacAuthorities.ROLE_ADMIN,
                RbacAuthorities.ROLE_WORSHIP_LEADER,
                RbacAuthorities.ROLE_TEAM_SCHEDULER);
    }

    public void requireContactDataRead(MusicianRecord musician) {
        require("personnel.contact.read", canReadPrivateContactData(musician));
    }

    public void requireAvailabilityManagement(MusicianRecord musician) {
        Authentication authentication = authentication();
        require(
                "personnel.availability.manage",
                hasAnyAuthority(
                                authentication,
                                RbacAuthorities.ROLE_ADMIN,
                                RbacAuthorities.ROLE_WORSHIP_LEADER,
                                RbacAuthorities.ROLE_TEAM_SCHEDULER)
                        || isAssignedMusician(authentication, musician));
    }

    public void requireAssignmentManagement() {
        Authentication authentication = authentication();
        require(
                "personnel.assignment.manage",
                hasAnyAuthority(
                        authentication,
                        RbacAuthorities.ROLE_ADMIN,
                        RbacAuthorities.ROLE_WORSHIP_LEADER,
                        RbacAuthorities.ROLE_TEAM_SCHEDULER));
    }

    public void requireSkillRangeMaintenance() {
        Authentication authentication = authentication();
        require(
                "personnel.skill_range.maintain",
                hasAnyAuthority(authentication, RbacAuthorities.ROLE_ADMIN, RbacAuthorities.ROLE_WORSHIP_LEADER));
    }

    public void requireTeamReadinessUpdate() {
        Authentication authentication = authentication();
        require(
                "personnel.readiness.update",
                hasAnyAuthority(
                        authentication,
                        RbacAuthorities.ROLE_ADMIN,
                        RbacAuthorities.ROLE_WORSHIP_LEADER,
                        RbacAuthorities.ROLE_TEAM_SCHEDULER));
    }

    public void requireSelfServiceAssignmentResponse(MusicianRecord musician) {
        require("personnel.assignment.self_response", isAssignedMusician(authentication(), musician));
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
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private void require(String operationClass, boolean permitted) {
        Authentication authentication = authentication();
        observabilityRecorder.recordAuthorizationDecision(
                operationClass, resolveActorRole(authentication), permitted ? "allow" : "deny", "personnel_policy");
        if (!permitted) {
            throw new AccessDeniedException(DENIED_MESSAGE);
        }
    }

    private Authentication authentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private boolean isAssignedMusician(Authentication authentication, MusicianRecord musician) {
        if (authentication == null || musician == null || musician.accountPrincipal() == null) {
            return false;
        }
        return hasAuthority(authentication, RbacAuthorities.ROLE_ASSIGNED_MUSICIAN)
                && musician.accountPrincipal().equalsIgnoreCase(authentication.getName());
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
        if (hasAuthority(authentication, RbacAuthorities.ROLE_DOCTRINAL_REVIEWER)
                || hasAuthority(authentication, RbacAuthorities.ROLE_MUSICAL_REVIEWER)) {
            return "REVIEWER";
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
