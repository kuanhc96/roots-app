package com.roots.authserver.enums;

import java.util.Arrays;

import lombok.Getter;

/**
 * External identity providers a local account can be bound to. The lowercase
 * {@code value} is what is stored in {@code social_binding.social_provider}.
 */
@Getter
public enum SocialProvider {
    GOOGLE("google");

    private final String value;

    SocialProvider(String value) {
        this.value = value;
    }

    /** The stored wire value itself, so call sites can concatenate the constant directly. */
    @Override
    public String toString() {
        return value;
    }

    public static SocialProvider fromValue(String value) {
        return Arrays.stream(values())
                .filter(p -> p.value.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown social provider: " + value));
    }
}
