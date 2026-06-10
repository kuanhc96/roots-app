package com.roots.account_management.enums;

public enum Role {
    PASTOR("pastor"),
    DEACON("deacon"),
    SMALL_GROUP_LEADER("small_group_leader"),
    VICE_SMALL_GROUP_LEADER("vice_small_group_leader"),
    MEMBER("member"),
    GUEST("guest");

    private final String dbValue;

    Role(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }
}
