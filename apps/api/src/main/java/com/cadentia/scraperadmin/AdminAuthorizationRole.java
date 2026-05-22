package com.cadentia.scraperadmin;

enum AdminAuthorizationRole {
    VIEWER,
    REVIEWER,
    APPROVER,
    ROLLBACK_ADMIN;

    static AdminAuthorizationRole fromActor(String actor) {
        String normalized = actor == null ? "" : actor.toLowerCase();
        if (normalized.contains("rollback-admin")) {
            return ROLLBACK_ADMIN;
        }
        if (normalized.contains("approver")) {
            return APPROVER;
        }
        if (normalized.contains("reviewer")) {
            return REVIEWER;
        }
        return VIEWER;
    }
}
