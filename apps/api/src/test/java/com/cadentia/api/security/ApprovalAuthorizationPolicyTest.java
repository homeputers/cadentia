package com.cadentia.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cadentia.catalog.model.ApprovalType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ApprovalAuthorizationPolicyTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ApprovalAuthorizationPolicy policy =
            new ApprovalAuthorizationPolicy(new SecurityObservabilityRecorder(meterRegistry));

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requireApprovalPermissionRecordsAllowDecisionForReviewer() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                "reviewer", "n/a", List.of(() -> RbacAuthorities.ROLE_DOCTRINAL_REVIEWER)));

        policy.requireApprovalPermission(ApprovalType.DOCTRINAL);

        assertThat(meterRegistry.get("cadentia_authz_decisions_total")
                .tag("operation_class", "catalog.approve.doctrinal")
                .tag("decision", "allow")
                .tag("role", "DOCTRINAL_REVIEWER")
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("cadentia_approval_decisions_total")
                .tag("review_domain", "doctrinal")
                .tag("decision", "approved")
                .counter()
                .count()).isEqualTo(1.0d);
    }

    @Test
    void requireApprovalPermissionRecordsDenyDecisionForUnauthorizedRole() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                "viewer", "n/a", List.of(() -> RbacAuthorities.ROLE_WORSHIP_LEADER)));

        assertThatThrownBy(() -> policy.requireApprovalPermission(ApprovalType.MUSICAL))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Access denied.");

        assertThat(meterRegistry.get("cadentia_authz_decisions_total")
                .tag("operation_class", "catalog.approve.musical")
                .tag("decision", "deny")
                .tag("role", "WORSHIP_LEADER")
                .counter()
                .count()).isEqualTo(1.0d);
    }
}
