package com.cadentia.scraperadmin;

public record ModerationEligibilityPolicyRule(
        String ruleId,
        String policyVersion,
        ModerationFlagType flagType,
        ModerationFlagSeverity severity,
        ModerationEligibilityEffect effect) {
}
