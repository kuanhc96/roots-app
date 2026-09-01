package com.roots.account_management.integration;

import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.roots.account_management.dto.request.AddRoleRequest;
import com.roots.account_management.dto.request.CreateAccountRequest;
import com.roots.account_management.dto.request.DeleteAccountsRequest;
import com.roots.account_management.dto.request.UpdateEmailRequest;
import com.roots.account_management.dto.request.UpdateMfaRequest;
import com.roots.account_management.dto.request.UpdateNameRequest;
import com.roots.account_management.dto.request.UpdatePasswordRequest;
import com.roots.account_management.dto.response.AccountProfileResponse;
import com.roots.account_management.dto.response.AccountProfilesResponse;
import com.roots.account_management.dto.response.AddRoleResponse;
import com.roots.account_management.dto.response.CreateTestAccountResponse;
import com.roots.account_management.dto.response.UpdateEmailResponse;
import com.roots.account_management.dto.response.UpdateMfaResponse;
import com.roots.account_management.dto.response.UpdateNameResponse;
import com.roots.account_management.dto.response.UpdatePasswordResponse;
import com.roots.account_management.dto.response.UserCredentialTestingResponse;
import com.roots.account_management.enums.Role;

/**
 * Client for account-management integration tests.
 */
public class AccountManagementClient {

    private final String baseUrl;
    private final RestTemplate restTemplate;
    private final HttpHeaders headers;

    public AccountManagementClient(String baseUrl, String accessToken) {
        this.baseUrl = baseUrl;
        headers = bearerJsonHeaders(accessToken);
        this.restTemplate = new RestTemplate();
    }

    public ResponseEntity<CreateTestAccountResponse> createTestAccount(String name, String email, String password) {
        HttpEntity<CreateAccountRequest> entity = new HttpEntity<>(
                CreateAccountRequest.builder().name(name).email(email).password(password).build(), headers);
        return restTemplate.exchange(baseUrl + "/api/account/test", HttpMethod.POST, entity, CreateTestAccountResponse.class);
    }

    public ResponseEntity<UserCredentialTestingResponse> getTestAccountByEmail(String email) {
        return getTestAccount("email=" + (email));
    }

    public ResponseEntity<UserCredentialTestingResponse> getTestAccountByUserGUID(String userGUID) {
        return getTestAccount("userGUID=" + (userGUID));
    }

    public ResponseEntity<Void> deleteByEmail(String email) {
        return deleteTestAccount("email=" + (email));
    }

    public ResponseEntity<Void> deleteByUserGUID(String userGUID) {
        return deleteTestAccount("userGUID=" + (userGUID));
    }

    public ResponseEntity<Void> deleteByEmailAndUserGUID(String email, String userGUID) {
        return deleteTestAccount("email=" + (email) + "&userGUID=" + (userGUID));
    }

    public ResponseEntity<Void> deleteWithoutParams() {
        return deleteTestAccount("");
    }

    public ResponseEntity<Void> deleteAccounts(List<String> userGUIDs) {
        HttpEntity<DeleteAccountsRequest> entity = new HttpEntity<>(
                DeleteAccountsRequest.builder().userGUIDs(userGUIDs).build(),
                headers
        );
        return restTemplate.exchange(baseUrl + "/api/account", HttpMethod.DELETE, entity, Void.class);
    }

    public ResponseEntity<UpdateMfaResponse> updateMfaByUserGUID(String userGUID, Boolean mfaEnabled) {
        HttpEntity<UpdateMfaRequest> entity = new HttpEntity<>(UpdateMfaRequest.builder().mfaEnabled(mfaEnabled).build(), headers);
        return restTemplate.exchange(baseUrl + "/api/account/mfa/" + (userGUID), HttpMethod.PUT, entity, UpdateMfaResponse.class);
    }

    public ResponseEntity<UpdatePasswordResponse> updatePasswordByUserGUID(String userGUID, String password, String oldPassword) {
        HttpEntity<UpdatePasswordRequest> entity = new HttpEntity<>(UpdatePasswordRequest.builder().password(password).oldPassword(oldPassword).build(), headers);
        return restTemplate.exchange(baseUrl + "/api/account/password/" + (userGUID), HttpMethod.PUT, entity, UpdatePasswordResponse.class);
    }

    public ResponseEntity<UpdateNameResponse> updateNameByUserGUID(String userGUID, String name) {
        HttpEntity<UpdateNameRequest> entity = new HttpEntity<>(UpdateNameRequest.builder().name(name).build(), headers);
        return restTemplate.exchange(baseUrl + "/api/account/name/" + (userGUID), HttpMethod.PUT, entity, UpdateNameResponse.class);
    }

    public ResponseEntity<UpdateEmailResponse> updateEmailByUserGUID(String userGUID, String email) {
        HttpEntity<UpdateEmailRequest> entity = new HttpEntity<>(UpdateEmailRequest.builder().email(email).build(), headers);
        return restTemplate.exchange(baseUrl + "/api/account/email/" + (userGUID), HttpMethod.PUT, entity, UpdateEmailResponse.class);
    }

    public ResponseEntity<AddRoleResponse> addRoleByUserGUID(String userGUID, Role role) {
        HttpEntity<AddRoleRequest> entity = new HttpEntity<>(AddRoleRequest.builder().role(role.getValue()).build(), headers);
        return restTemplate.exchange(baseUrl + "/api/account/role/" + (userGUID), HttpMethod.POST, entity, AddRoleResponse.class);
    }

    public ResponseEntity<AccountProfileResponse> getAccountProfileByEmail(String email) {
        return getAccountProfile("email=" + (email));
    }

    public ResponseEntity<AccountProfileResponse> getAccountProfileByUserGUID(String userGUID) {
        return getAccountProfile("userGUID=" + (userGUID));
    }

    public ResponseEntity<AccountProfileResponse> getAccountProfileWithQuery(String query) {
        return getAccountProfile(query);
    }

    public ResponseEntity<AccountProfilesResponse> getAccountProfiles(int page, int size) {
        return getAccountProfilesWithQuery("page=" + page + "&size=" + size);
    }

    public ResponseEntity<AccountProfilesResponse> getAccountProfilesWithQuery(String query) {
        String url = baseUrl + "/api/account/profiles" + (query.isBlank() ? "" : "?" + query);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(url, HttpMethod.GET, entity, AccountProfilesResponse.class);
    }

    public ResponseEntity<List<AccountProfileResponse>> searchAccountsByEmail(String email, boolean fullMatch, Integer maxCount) {
        String query = "email=" + (email) + "&fullMatch=" + fullMatch;
        if (maxCount != null) {
            query = query + "&maxCount=" + maxCount;
        }
        return searchAccountsWithQuery(query);
    }

    public ResponseEntity<List<AccountProfileResponse>> searchAccountsByName(String name, boolean fullMatch, Integer maxCount) {
        String query = "name=" + (name) + "&fullMatch=" + fullMatch;
        if (maxCount != null) {
            query = query + "&maxCount=" + maxCount;
        }
        return searchAccountsWithQuery(query);
    }

    public ResponseEntity<List<AccountProfileResponse>> searchAccountsWithQuery(String query) {
        String url = baseUrl + "/api/account/search" + (query.isBlank() ? "" : "?" + query);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(url, HttpMethod.POST, entity, new ParameterizedTypeReference<List<AccountProfileResponse>>() {});
    }

    private ResponseEntity<AccountProfileResponse> getAccountProfile(String query) {
        String url = baseUrl + "/api/account/profile" + (query.isBlank() ? "" : "?" + query);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(url, HttpMethod.GET, entity, AccountProfileResponse.class);
    }

    private ResponseEntity<UserCredentialTestingResponse> getTestAccount(String query) {
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        String url = baseUrl + "/api/account/test" + (query.isBlank() ? "" : "?" + query);
        return restTemplate.exchange(url, HttpMethod.GET, entity, UserCredentialTestingResponse.class);
    }

    private ResponseEntity<Void> deleteTestAccount(String query) {
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        String url = baseUrl + "/api/account/test" + (query.isBlank() ? "" : "?" + query);
        return restTemplate.exchange(url, HttpMethod.DELETE, entity, Void.class);
    }

    private HttpHeaders bearerJsonHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

}

