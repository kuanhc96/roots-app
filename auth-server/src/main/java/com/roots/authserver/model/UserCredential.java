package com.roots.authserver.model;

public record UserCredential(Long id, String userGuid, String email, String name, String password, boolean mfaEnabled, boolean emailVerified, boolean passwordChangeRequired) {}
