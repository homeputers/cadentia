package com.cadentia.search;

import com.cadentia.search.ApprovedSearchModels.ApprovedSearchDocument;
import com.cadentia.search.ApprovedSearchModels.SearchActor;
import com.cadentia.search.ApprovedSearchModels.SearchEligibilityDecision;
import com.cadentia.search.ApprovedSearchModels.SearchVisibilityPolicy;
import java.util.Objects;
import java.util.Set;

public class SearchEligibilityPolicy {

    public SearchEligibilityDecision evaluate(SearchActor actor, ApprovedSearchDocument document) {
        if (document == null) {
            return SearchEligibilityDecision.deny("missingDocument");
        }
        if (actor == null || !actor.authenticated()) {
            return SearchEligibilityDecision.deny("unauthenticated");
        }
        if (!Objects.equals(actor.instanceId(), document.instanceId())) {
            return SearchEligibilityDecision.deny("instanceScope");
        }
        if (!document.approved()) {
            return SearchEligibilityDecision.deny("approvalState");
        }
        if (!document.active()) {
            return SearchEligibilityDecision.deny("activeStatus");
        }
        if (!document.instanceVisible()) {
            return SearchEligibilityDecision.deny("instanceVisibility");
        }
        if (!document.packageVisible()) {
            return SearchEligibilityDecision.deny("packageVisibility");
        }
        if (!document.licensed()) {
            return SearchEligibilityDecision.deny("licensing");
        }
        if (document.visibilityPolicy() == SearchVisibilityPolicy.RESTRICTED
                && actor.roles().stream().noneMatch(document.authorizedRoles()::contains)) {
            return SearchEligibilityDecision.deny("roleAuthorization");
        }
        if (!document.governanceRestrictionCodes().isEmpty()
                && !actor.governanceBypassCodes().containsAll(document.governanceRestrictionCodes())) {
            return SearchEligibilityDecision.deny("catalogGovernance");
        }
        return SearchEligibilityDecision.allow();
    }

    public boolean canReturn(SearchActor actor, ApprovedSearchDocument document) {
        return evaluate(actor, document).eligible();
    }

    public SearchActor user(String actorId, java.util.UUID instanceId, Set<String> roles) {
        return new SearchActor(actorId, instanceId, roles, Set.of(), true);
    }
}
