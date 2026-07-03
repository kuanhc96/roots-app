package com.roots.authserver.enums;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum Role {
    PASTOR("pastor"),
    DEACON("deacon"),
    SMALL_GROUP_LEADER("small_group_leader"),
    VICE_SMALL_GROUP_LEADER("vice_small_group_leader"),
    MEMBER("member"),
    GUEST("guest");

    @JsonValue
    private final String value;

    Role(String value) {
        this.value = value;
    }

    @JsonCreator
    public static Role getValue(String value) {
        for (Role type : Role.values()) {
            if (type.getValue().equalsIgnoreCase(value)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Unknown enum value: %s. Valid values are [%s]".formatted(value,
                Arrays.stream(Role.values())
                        .map(Role::getValue)  // Get the lowercase values from the enum
                        .reduce((v1, v2) -> v1 + ", " + v2)  // Join the values with a comma separator
                        .orElse("")));
    }
}
