package com.cadentia.admin;

public record AdminUserCreationResult(AdminUserRecord user, String activationToken) {
}
