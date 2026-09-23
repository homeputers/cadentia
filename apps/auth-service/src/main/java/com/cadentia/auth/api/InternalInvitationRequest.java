package com.cadentia.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InternalInvitationRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 2, max = 120) String displayName) {
}
