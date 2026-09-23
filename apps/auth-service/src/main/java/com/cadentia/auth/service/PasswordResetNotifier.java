package com.cadentia.auth.service;

import com.cadentia.auth.domain.AuthUser;

public interface PasswordResetNotifier {

    void send(AuthUser user, String rawToken);
}
