package com.roots.account_management.constant;

public class PreAuthorizeConstants {
    public static final String INTEGRATION_TEST_CLIENT_READ = "hasAuthority('INTEGRATION_TEST_CLIENT_READ')";
    public static final String INTEGRATION_TEST_CLIENT_DELETE = "hasAuthority('INTEGRATION_TEST_CLIENT_DELETE')";
    public static final String INTEGRATION_TEST_CLIENT_WRITE = "hasAuthority('INTEGRATION_TEST_CLIENT_WRITE')";
    public static final String ADMIN = "hasRole('ADMIN')";
    public static final String MEMBER = "hasRole('MEMBER')";
    public static final String ACCOUNT_MANAGEMENT_CLIENT = "hasAuthority('ACCOUNT_MANAGEMENT_CLIENT')";
    public static final String ACCOUNT_MANAGEMENT_SUPER_CLIENT = "hasAuthority('ACCOUNT_MANAGEMENT_SUPER_CLIENT')";

    public static final String AMC_AND_ADMIN_OR_IT_DELETE = "(" + ACCOUNT_MANAGEMENT_CLIENT + " and " + ADMIN + ") or " + INTEGRATION_TEST_CLIENT_DELETE;
    public static final String AMC_AND_ADMIN_OR_IT_READ = "(" + ACCOUNT_MANAGEMENT_CLIENT + " and " + ADMIN + ") or " + INTEGRATION_TEST_CLIENT_READ;
    public static final String AMC_AND_ADMIN_OR_IT_WRITE = "(" + ACCOUNT_MANAGEMENT_CLIENT + " and " + ADMIN + ") or " + INTEGRATION_TEST_CLIENT_WRITE;

    public static final String AMC_AND_MEMBER_OR_IT_READ = "(" + ACCOUNT_MANAGEMENT_CLIENT + " and " + MEMBER + ") or " + INTEGRATION_TEST_CLIENT_READ;
    public static final String AMC_AND_MEMBER_OR_IT_WRITE = "(" + ACCOUNT_MANAGEMENT_CLIENT + " and " + MEMBER + ") or " + INTEGRATION_TEST_CLIENT_WRITE;
    public static final String AMC_AND_MEMBER_OR_IT_DELETE = "(" + ACCOUNT_MANAGEMENT_CLIENT + " and " + MEMBER + ") or " + INTEGRATION_TEST_CLIENT_DELETE;
    public static final String AMSC_AND_MEMBER_OR_IT_WRITE = "(" + ACCOUNT_MANAGEMENT_SUPER_CLIENT + " and " + MEMBER + ") or " + INTEGRATION_TEST_CLIENT_WRITE;
}
