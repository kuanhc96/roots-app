package com.roots.account_management.model;

public record UserCredential(Long id, String userGuid, String email, String name, String password, boolean mfaEnabled, boolean emailVerified) {}
