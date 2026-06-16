package com.cadentia.api.security;

import static com.cadentia.asset.AssetModels.AssetLifecycleStatusCode.ARCHIVED;
import static com.cadentia.asset.AssetModels.AssetLifecycleStatusCode.AVAILABLE;
import static com.cadentia.asset.AssetModels.AssetLifecycleStatusCode.QUARANTINED;
import static com.cadentia.asset.AssetModels.AssetLicenseStatusCode.EXPIRED;
import static com.cadentia.asset.AssetModels.AssetLicenseStatusCode.RESTRICTED;
import static com.cadentia.asset.AssetModels.AssetLicenseStatusCode.REVOKED;
import static com.cadentia.asset.AssetModels.AssetProcessingStatusCode.FAILED;
import static com.cadentia.asset.AssetModels.AssetProcessingStatusCode.READY;
import static com.cadentia.asset.AssetModels.AssetProcessingStatusCode.REJECTED;
import static com.cadentia.api.security.RbacAuthorities.ROLE_ADMIN;
import static com.cadentia.api.security.RbacAuthorities.ROLE_ASSIGNED_MUSICIAN;
import static com.cadentia.api.security.RbacAuthorities.ROLE_CATALOG_EDITOR;
import static com.cadentia.api.security.RbacAuthorities.ROLE_DOCTRINAL_REVIEWER;
import static com.cadentia.api.security.RbacAuthorities.ROLE_MUSICAL_REVIEWER;
import static com.cadentia.api.security.RbacAuthorities.ROLE_REPORTING_VIEWER;
import static com.cadentia.api.security.RbacAuthorities.ROLE_TEAM_SCHEDULER;
import static com.cadentia.api.security.RbacAuthorities.ROLE_WORSHIP_LEADER;

import com.cadentia.asset.AssetModels.AssetAccessPolicyCode;
import com.cadentia.asset.AssetModels.AssetAttachmentRecord;
import com.cadentia.asset.AssetModels.AssetRecord;
import com.cadentia.asset.AssetModels.AssetVersionRecord;
import com.cadentia.asset.AssetModels.LicenseMetadata;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AssetAuthorizationPolicy {

    private static final Set<String> CATALOG_REVIEWER_ROLES = Set.of(
            ROLE_CATALOG_EDITOR, ROLE_DOCTRINAL_REVIEWER, ROLE_MUSICAL_REVIEWER);
    private static final Set<String> WORSHIP_TEAM_ROLES = Set.of(
            ROLE_ADMIN, ROLE_WORSHIP_LEADER, ROLE_TEAM_SCHEDULER, ROLE_ASSIGNED_MUSICIAN,
            ROLE_CATALOG_EDITOR, ROLE_DOCTRINAL_REVIEWER, ROLE_MUSICAL_REVIEWER);
    private static final Set<AssetAction> SENSITIVE_DENIED_AUDIT_ACTIONS = Set.of(
            AssetAction.READ_PRIVATE_LICENSING_FIELDS,
            AssetAction.GENERATE_SIGNED_DOWNLOAD_URL,
            AssetAction.STREAM,
            AssetAction.ARCHIVE,
            AssetAction.DELETE,
            AssetAction.QUARANTINE);

    private final AssetAuditRecorder auditRecorder;
    private final Clock clock;

    @Autowired
    public AssetAuthorizationPolicy(AssetAuditRecorder auditRecorder) {
        this(auditRecorder, Clock.systemUTC());
    }

    public AssetAuthorizationPolicy(AssetAuditRecorder auditRecorder, Clock clock) {
        this.auditRecorder = auditRecorder;
        this.clock = clock;
    }

    public AssetAuthorizationDecision authorize(AssetAuthorizationRequest request) {
        AssetAuthorizationDecision decision = decide(request);
        if (shouldAudit(request.action(), decision)) {
            auditRecorder.record(AssetAuditRecord.from(request, decision, clock.instant()));
        }
        return decision;
    }

    public RedactedLicenseMetadata licenseFor(AssetAuthorizationRequest request) {
        AssetAuthorizationDecision decision = authorize(request.withAction(AssetAction.READ_PRIVATE_LICENSING_FIELDS));
        return RedactedLicenseMetadata.from(request.version().licenseMetadata(), decision.permitted());
    }

    private AssetAuthorizationDecision decide(AssetAuthorizationRequest request) {
        AssetActor actor = request.actor();
        if (!actor.authenticated()) {
            return AssetAuthorizationDecision.deny(AssetDenialReason.UNAUTHENTICATED_ACTOR, "Actor must be authenticated");
        }
        if (!Objects.equals(actor.instanceId(), request.resourceInstanceId())) {
            return AssetAuthorizationDecision.deny(AssetDenialReason.CROSS_INSTANCE, "Asset belongs to another instance");
        }
        if (!rolePermitsAction(request)) {
            return AssetAuthorizationDecision.deny(AssetDenialReason.ROLE_NOT_PERMITTED, "Role is not permitted for asset action");
        }
        if (!policyPermitsActor(request.version().accessPolicyCode(), request)) {
            return AssetAuthorizationDecision.deny(AssetDenialReason.ACCESS_POLICY_DENIED, "Asset access policy denies actor");
        }
        if (requiresNormalBinaryAccess(request.action())) {
            AssetAuthorizationDecision availability = normalBinaryAvailability(request.version());
            if (!availability.permitted()) {
                return availability;
            }
        }
        if (request.action() == AssetAction.READ_PRIVATE_LICENSING_FIELDS
                && !policyPermitsActor(request.version().licenseMetadata().visibilityPolicyCode(), request)) {
            return AssetAuthorizationDecision.deny(AssetDenialReason.LICENSE_VISIBILITY_DENIED, "Licensing fields are redacted");
        }
        return AssetAuthorizationDecision.allow();
    }

    private boolean rolePermitsAction(AssetAuthorizationRequest request) {
        AssetActor actor = request.actor();
        if (actor.hasRole(ROLE_ADMIN)) {
            return true;
        }
        return switch (request.action()) {
            case READ_METADATA -> actor.hasAnyRole(WORSHIP_TEAM_ROLES) || actor.hasRole(ROLE_REPORTING_VIEWER)
                    || request.context().serviceParticipant();
            case READ_PRIVATE_LICENSING_FIELDS, READ_PROCESSING_RESULT -> actor.hasRole(ROLE_WORSHIP_LEADER)
                    || actor.hasRole(ROLE_TEAM_SCHEDULER) || actor.hasRole(ROLE_REPORTING_VIEWER)
                    || actor.hasAnyRole(CATALOG_REVIEWER_ROLES);
            case CREATE_UPLOAD, FINALIZE_UPLOAD, MANAGE_ATTACHMENT -> actor.hasRole(ROLE_WORSHIP_LEADER)
                    || actor.hasRole(ROLE_TEAM_SCHEDULER) || actor.hasAnyRole(CATALOG_REVIEWER_ROLES);
            case GENERATE_SIGNED_DOWNLOAD_URL, STREAM -> actor.hasRole(ROLE_WORSHIP_LEADER)
                    || actor.hasRole(ROLE_TEAM_SCHEDULER) || actor.hasRole(ROLE_ASSIGNED_MUSICIAN)
                    || request.context().assignedToService() || request.context().rehearsalParticipant();
            case ARCHIVE, DELETE, QUARANTINE -> actor.hasRole(ROLE_WORSHIP_LEADER) || actor.hasAnyRole(CATALOG_REVIEWER_ROLES);
        };
    }

    private boolean policyPermitsActor(AssetAccessPolicyCode accessPolicy, AssetAuthorizationRequest request) {
        AssetActor actor = request.actor();
        return switch (accessPolicy) {
            case PUBLIC_METADATA -> true;
            case CATALOG_REVIEWERS -> actor.hasAnyRole(CATALOG_REVIEWER_ROLES) || actor.hasRole(ROLE_ADMIN);
            case WORSHIP_TEAM -> actor.hasAnyRole(WORSHIP_TEAM_ROLES) || request.context().serviceParticipant();
            case SERVICE_PARTICIPANTS -> actor.hasRole(ROLE_WORSHIP_LEADER) || actor.hasRole(ROLE_TEAM_SCHEDULER)
                    || request.context().assignedToService() || request.context().rehearsalParticipant();
            case ADMINS_ONLY -> actor.hasRole(ROLE_ADMIN);
            case LOCAL_POLICY -> actor.hasRole(ROLE_ADMIN) || actor.hasRole(ROLE_WORSHIP_LEADER);
        };
    }

    private AssetAuthorizationDecision normalBinaryAvailability(AssetVersionRecord version) {
        if (version.lifecycleStatusCode() == ARCHIVED) {
            return AssetAuthorizationDecision.deny(AssetDenialReason.ARCHIVED_VERSION, "Archived asset versions are not downloadable");
        }
        if (version.lifecycleStatusCode() == QUARANTINED) {
            return AssetAuthorizationDecision.deny(AssetDenialReason.QUARANTINED_VERSION, "Quarantined asset versions are not downloadable");
        }
        if (version.lifecycleStatusCode() != AVAILABLE) {
            return AssetAuthorizationDecision.deny(AssetDenialReason.LIFECYCLE_NOT_AVAILABLE, "Asset version is not available");
        }
        if (version.processingStatusCode() == FAILED || version.processingStatusCode() == REJECTED) {
            return AssetAuthorizationDecision.deny(AssetDenialReason.FAILED_SCAN, "Asset processing did not pass safety checks");
        }
        if (version.processingStatusCode() != READY) {
            return AssetAuthorizationDecision.deny(AssetDenialReason.PROCESSING_NOT_READY, "Asset processing is not ready");
        }
        LicenseMetadata license = version.licenseMetadata();
        if (license.licenseStatusCode() == EXPIRED || license.licenseStatusCode() == REVOKED) {
            return AssetAuthorizationDecision.deny(AssetDenialReason.LICENSE_EXPIRED, "License is expired or revoked");
        }
        if (license.licenseStatusCode() == RESTRICTED) {
            return AssetAuthorizationDecision.deny(AssetDenialReason.LICENSE_RESTRICTED, "License restrictions block normal access");
        }
        if (license.expiresAt() != null && !license.expiresAt().isAfter(clock.instant())) {
            return AssetAuthorizationDecision.deny(AssetDenialReason.LICENSE_EXPIRED, "License expiration date has passed");
        }
        return AssetAuthorizationDecision.allow();
    }

    private boolean shouldAudit(AssetAction action, AssetAuthorizationDecision decision) {
        return action.privileged() || (!decision.permitted() && SENSITIVE_DENIED_AUDIT_ACTIONS.contains(action));
    }

    private boolean requiresNormalBinaryAccess(AssetAction action) {
        return action == AssetAction.GENERATE_SIGNED_DOWNLOAD_URL || action == AssetAction.STREAM;
    }

    public enum AssetAction {
        READ_METADATA(false),
        READ_PRIVATE_LICENSING_FIELDS(false),
        CREATE_UPLOAD(true),
        FINALIZE_UPLOAD(true),
        MANAGE_ATTACHMENT(true),
        READ_PROCESSING_RESULT(false),
        GENERATE_SIGNED_DOWNLOAD_URL(true),
        STREAM(true),
        ARCHIVE(true),
        DELETE(true),
        QUARANTINE(true);

        private final boolean privileged;

        AssetAction(boolean privileged) {
            this.privileged = privileged;
        }

        public boolean privileged() {
            return privileged;
        }
    }

    public enum AssetDenialReason {
        NONE,
        UNAUTHENTICATED_ACTOR,
        CROSS_INSTANCE,
        ROLE_NOT_PERMITTED,
        ACCESS_POLICY_DENIED,
        LICENSE_VISIBILITY_DENIED,
        LIFECYCLE_NOT_AVAILABLE,
        ARCHIVED_VERSION,
        QUARANTINED_VERSION,
        FAILED_SCAN,
        PROCESSING_NOT_READY,
        LICENSE_EXPIRED,
        LICENSE_RESTRICTED
    }

    public record AssetActor(String actorId, String instanceId, Set<String> roles, boolean authenticated) {
        public AssetActor {
            roles = roles == null ? Set.of() : Set.copyOf(roles);
        }

        public boolean hasRole(String role) {
            return roles.contains(role);
        }

        public boolean hasAnyRole(Set<String> candidateRoles) {
            return candidateRoles.stream().anyMatch(roles::contains);
        }
    }

    public record AssetAccessContext(
            UUID servicePlanId,
            UUID rehearsalSessionId,
            UUID attachmentTargetId,
            boolean assignedToService,
            boolean rehearsalParticipant,
            boolean serviceParticipant) {
        public static AssetAccessContext none() {
            return new AssetAccessContext(null, null, null, false, false, false);
        }
    }

    public record AssetAuthorizationRequest(
            AssetAction action,
            AssetActor actor,
            String resourceInstanceId,
            AssetRecord asset,
            AssetVersionRecord version,
            AssetAttachmentRecord attachment,
            AssetAccessContext context) {
        public AssetAuthorizationRequest {
            context = context == null ? AssetAccessContext.none() : context;
            Objects.requireNonNull(action, "action is required");
            Objects.requireNonNull(actor, "actor is required");
            Objects.requireNonNull(version, "version is required");
        }

        public AssetAuthorizationRequest withAction(AssetAction nextAction) {
            return new AssetAuthorizationRequest(nextAction, actor, resourceInstanceId, asset, version, attachment, context);
        }
    }

    public record AssetAuthorizationDecision(boolean permitted, AssetDenialReason reasonCode, String reason) {
        static AssetAuthorizationDecision allow() {
            return new AssetAuthorizationDecision(true, AssetDenialReason.NONE, "allowed");
        }

        static AssetAuthorizationDecision deny(AssetDenialReason reasonCode, String reason) {
            return new AssetAuthorizationDecision(false, reasonCode, reason);
        }
    }

    public record RedactedLicenseMetadata(
            String licenseSource,
            String licenseStatus,
            String usageRestrictions,
            Instant expiresAt,
            boolean privateFieldsVisible,
            String licenseReference,
            String licenseHolder) {
        static RedactedLicenseMetadata from(LicenseMetadata metadata, boolean privateFieldsVisible) {
            return new RedactedLicenseMetadata(
                    metadata.licenseSource(),
                    metadata.licenseStatusCode().name(),
                    metadata.usageRestrictions(),
                    metadata.expiresAt(),
                    privateFieldsVisible,
                    privateFieldsVisible ? metadata.licenseReference() : null,
                    privateFieldsVisible ? metadata.licenseHolder() : null);
        }
    }

    public record AssetAuditRecord(
            String actorId,
            AssetAction actionCode,
            UUID assetId,
            UUID assetVersionId,
            UUID attachmentTargetId,
            UUID servicePlanId,
            UUID rehearsalSessionId,
            boolean permitted,
            Instant decidedAt,
            AssetDenialReason reasonCode,
            Map<String, String> referenceMetadata) {
        static AssetAuditRecord from(AssetAuthorizationRequest request, AssetAuthorizationDecision decision, Instant decidedAt) {
            UUID assetId = request.asset() == null ? request.version().assetId() : request.asset().id();
            return new AssetAuditRecord(
                    request.actor().actorId(),
                    request.action(),
                    assetId,
                    request.version().id(),
                    request.context().attachmentTargetId(),
                    request.context().servicePlanId(),
                    request.context().rehearsalSessionId(),
                    decision.permitted(),
                    decidedAt,
                    decision.reasonCode(),
                    Map.of(
                            "assetAccessPolicy", request.version().accessPolicyCode().name(),
                            "assetLifecycle", request.version().lifecycleStatusCode().name(),
                            "assetProcessing", request.version().processingStatusCode().name(),
                            "licenseStatus", request.version().licenseMetadata().licenseStatusCode().name()));
        }
    }
}
