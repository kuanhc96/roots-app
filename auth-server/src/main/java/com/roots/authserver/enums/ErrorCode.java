package com.roots.authserver.enums;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

/**
 * Machine-readable error codes sent to the frontend as the {@code e} query parameter on
 * redirects. The exact mirror of the keys in {@code frontend/utils/errorMessages.ts},
 * which owns the display text — keep the two in sync.
 */
@Getter
public enum ErrorCode {
    INVALID_LOGIN("invalid_login"),
    INVALID_TOKEN("invalid_token"),
    INVALID_PASSWORD("invalid_password"),
    OAUTH_REDIRECT_FAILED("oauth_redirect_failed"),
    /** Reserved: mapped in errorMessages.ts but not currently emitted by the server. */
    NO_MFA_PENDING("no_mfa_pending"),
    EMAIL_TAKEN("email_taken"),
    INVALID_REQUEST("invalid_request");

    private final String value;

    ErrorCode(String value) {
        this.value = value;
    }

    /** The wire code itself, so call sites can concatenate the constant directly. */
    @Override
    public String toString() {
        return value;
    }

    @JsonCreator
    public static ErrorCode getValue(String value) {
        for (ErrorCode type : ErrorCode.values()) {
            if (type.getValue().equalsIgnoreCase(value)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Unknown enum value: %s. Valid values are [%s]".formatted(value,
                Arrays.stream(ErrorCode.values())
                        .map(ErrorCode::getValue)  // Get the lowercase values from the enum
                        .reduce((v1, v2) -> v1 + ", " + v2)  // Join the values with a comma separator
                        .orElse("")));
    }
}
