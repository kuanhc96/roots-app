package com.roots.authserver.model;

import com.roots.authserver.enums.SocialProvider;

public record SocialBinding(Long id, long userId, SocialProvider socialProvider, String socialUserId) {}
