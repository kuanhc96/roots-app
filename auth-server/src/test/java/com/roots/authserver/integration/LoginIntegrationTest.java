package com.roots.authserver.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roots.authserver.dto.CreateTestAccountResponse;
import com.roots.authserver.dto.TokenResponse;
import com.roots.authserver.dto.UserCredentialTestingResponse;
import com.roots.authserver.enums.ErrorCode;
import com.roots.authserver.util.HttpFlowUtils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;

import java.net.HttpCookie;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the form-login flow end-to-end against a live auth-server and
 * account-management. Account creation lives in a @Nested class per flag combination
 * (recording the userGUID for cleanup): tests that share an account shape share its
 * @BeforeEach, and future combinations (MFA on, unverified, …) get their own nested
 * group. The outer @AfterEach deletes whatever account was created.
 */
class LoginIntegrationTest extends IntegrationTestBase {

    private static final String TEST_NAME = "Integration Test User";
    private static final String TEST_PASSWORD = "Password123";

    @Value("${web-client-location}")
    private String webClientLocation;

    @Value("${web-client-secret}")
    private String webClientSecret;

    private String email;
    private String userGUID;

    @AfterEach
    void deleteTestAccount() {
        // Tests create their own account; a test that fails before doing so leaves
        // userGUID null, and there is nothing to clean up.
        if (userGUID != null) {
            accountManagementClient.deleteByUserGUID(userGUID);
        }
    }

    @Nested
    class MfaDisabled_EmailVerified_PasswordChangeNotRequired {
        @BeforeEach
        void createTestAccount() {
            // 1. Create an account with default roles, MFA disabled, email verified, and no
            //    password change required — nothing should interrupt the login.
            email = "itest_" + UUID.randomUUID() + "@example.com";
            ResponseEntity<CreateTestAccountResponse> createResponse = accountManagementClient.createTestAccount(
                    TEST_NAME, email, TEST_PASSWORD,
                    false /* mfaEnabled */, true /* emailVerified */, false /* passwordChangeRequired */);
            assertThat(createResponse.getStatusCode().value()).isEqualTo(201);
            assertThat(createResponse.getBody()).isNotNull();
            userGUID = createResponse.getBody().userGUID();
            assertThat(userGUID).isNotBlank();
        }

        @Test
        void login_completesAuthorizationCodeFlow() throws Exception {
            // 2. Start the authorization-code flow so a SavedRequest is held in the session;
            //    a fully authenticated login redirects back to it (and thus to the callback).
            String redirectUri = webClientLocation + "/callback";
            HttpResponse<String> authorizeResponse =
                    authServerClient.startOAuth2AuthorizationFlow("WEB_CLIENT", redirectUri, "openid WEB_CLIENT_READ", "test-state");
            assertThat(authorizeResponse.statusCode()).isEqualTo(302);
            HttpFlowUtils.followRedirects(authServerClient, authServerLocation, authorizeResponse, redirectUri);

            // 3. Log in. With MFA disabled, email verified, and no password change pending,
            //    the session is fully authenticated and redirected straight back to the
            //    saved authorize request — not to /ott/login, /reset-password, or
            //    /signup/success.
            HttpResponse<String> loginResponse = authServerClient.login(email, TEST_PASSWORD);
            assertThat(loginResponse.statusCode()).isEqualTo(302);
            assertThat(loginResponse.headers().firstValue("Location").orElseThrow()).contains("/oauth2/authorize");

            // 4. Follow the redirect chain; we should land on the web-client callback with
            //    an authorization code.
            HttpResponse<String> response = HttpFlowUtils.followRedirects(
                    authServerClient, authServerLocation, loginResponse, redirectUri);
            assertThat(response.statusCode()).isEqualTo(302);
            String callback = response.headers().firstValue("Location").orElseThrow();
            assertThat(callback).startsWith(redirectUri);
            String code = HttpFlowUtils.extractQueryParam(callback, "code");
            assertThat(code).isNotBlank();

            // 5. Exchange the code for tokens and verify the right user is logged in with
            //    the default role.
            TokenResponse tokens = oAuth2Client.getAuthorizationGrantToken(code, "WEB_CLIENT", webClientSecret, redirectUri);
            assertThat(tokens.accessToken()).isNotBlank();
            assertThat(tokens.refreshToken()).isNotBlank();
            assertThat(tokens.idToken()).isNotBlank();

            byte[] payloadBytes = Base64.getUrlDecoder().decode(tokens.accessToken().split("\\.")[1]);
            Map<String, Object> claims = new ObjectMapper().readValue(payloadBytes, new TypeReference<>() {});
            assertThat(claims.get("sub")).isEqualTo(email);
            List<String> roles = (List<String>) claims.get("roles");
            assertThat(roles).contains("MEMBER");

            // The id_token carries the claims bff-server serves from /api/auth/status:
            // email, userGUID (from the user_credential row), and roles.
            byte[] idTokenPayload = Base64.getUrlDecoder().decode(tokens.idToken().split("\\.")[1]);
            Map<String, Object> idTokenClaims = new ObjectMapper().readValue(idTokenPayload, new TypeReference<>() {});
            assertThat(idTokenClaims.get("email")).isEqualTo(email);
            assertThat(idTokenClaims.get("userGUID")).isEqualTo(userGUID);
            assertThat((List<String>) idTokenClaims.get("roles")).contains("MEMBER");

            // 6. "Restart the browser" and try again: remember-me was not checked, so the
            //    cookie jar is empty after dropping the session cookie. With no
            //    remember-me cookie there is no auto-login — the new authorize flow
            //    dead-ends on the login form instead of reaching the callback.
            authServerClient.clearSessionCookies();

            HttpResponse<String> secondAuthorize =
                    authServerClient.startOAuth2AuthorizationFlow("WEB_CLIENT", redirectUri, "openid WEB_CLIENT_READ", "test-state-2");
            assertThat(secondAuthorize.statusCode()).isEqualTo(302);
            HttpResponse<String> loginPage = HttpFlowUtils.followRedirects(
                    authServerClient, authServerLocation, secondAuthorize, redirectUri);
            assertThat(loginPage.statusCode()).isEqualTo(200);
            assertThat(loginPage.uri().getPath()).isEqualTo("/login");
        }

        @Test
        void login_withRememberMe_reAuthenticatesWithCookieAfterSessionCleared() throws Exception {
            // 2. Start the authorization-code flow and log in with "remember me" checked.
            String redirectUri = webClientLocation + "/callback";
            HttpResponse<String> authorizeResponse =
                    authServerClient.startOAuth2AuthorizationFlow("WEB_CLIENT", redirectUri, "openid WEB_CLIENT_READ", "test-state");
            assertThat(authorizeResponse.statusCode()).isEqualTo(302);
            HttpFlowUtils.followRedirects(authServerClient, authServerLocation, authorizeResponse, redirectUri);

            HttpResponse<String> loginResponse = authServerClient.login(email, TEST_PASSWORD, true /* rememberMe */);
            assertThat(loginResponse.statusCode()).isEqualTo(302);

            // 3. The login response must issue the persistent remember-me cookie.
            String rememberMeSetCookie = loginResponse.headers().allValues("set-cookie").stream()
                    .filter(header -> header.startsWith("remember-me="))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No remember-me cookie issued on the login response"));
            assertThat(HttpCookie.parse(rememberMeSetCookie).get(0).getValue()).isNotBlank();

            // 4. Finish the first login so its saved request is consumed.
            HttpResponse<String> firstCallback = HttpFlowUtils.followRedirects(
                    authServerClient, authServerLocation, loginResponse, redirectUri);
            assertThat(firstCallback.statusCode()).isEqualTo(302);
            assertThat(firstCallback.headers().firstValue("Location").orElseThrow()).contains("code=");

            // 5. "Restart the browser": drop the JSESSIONID but keep the persistent
            //    remember-me cookie, then start a fresh authorization flow. No credentials
            //    are posted this time — the remember-me filter must authenticate the cookie
            //    (302 to /login → cookie auth → back to the saved authorize request) and
            //    carry the flow through to the callback.
            authServerClient.clearSessionCookies();

            HttpResponse<String> secondAuthorize =
                    authServerClient.startOAuth2AuthorizationFlow("WEB_CLIENT", redirectUri, "openid WEB_CLIENT_READ", "test-state-2");
            assertThat(secondAuthorize.statusCode()).isEqualTo(302);
            HttpResponse<String> secondCallback = HttpFlowUtils.followRedirects(
                    authServerClient, authServerLocation, secondAuthorize, redirectUri);
            assertThat(secondCallback.statusCode()).isEqualTo(302);
            String callback = secondCallback.headers().firstValue("Location").orElseThrow();
            assertThat(callback).startsWith(redirectUri);
            String code = HttpFlowUtils.extractQueryParam(callback, "code");
            assertThat(code).isNotBlank();

            // 6. Exchange the code and confirm the cookie logged in the same user.
            TokenResponse tokens = oAuth2Client.getAuthorizationGrantToken(code, "WEB_CLIENT", webClientSecret, redirectUri);
            assertThat(tokens.accessToken()).isNotBlank();
            byte[] payloadBytes = Base64.getUrlDecoder().decode(tokens.accessToken().split("\\.")[1]);
            Map<String, Object> claims = new ObjectMapper().readValue(payloadBytes, new TypeReference<>() {});
            assertThat(claims.get("sub")).isEqualTo(email);
        }

    }

    @Nested
    class InvalidCredentials {
        @BeforeEach
        void createTestAccount() {
            // 1. Create a fully set-up account (MFA off, verified, no password change) so
            //    the only thing that can fail the login is the credentials themselves.
            email = "itest_" + UUID.randomUUID() + "@example.com";
            ResponseEntity<CreateTestAccountResponse> createResponse = accountManagementClient.createTestAccount(
                    TEST_NAME, email, TEST_PASSWORD,
                    false /* mfaEnabled */, true /* emailVerified */, false /* passwordChangeRequired */);
            assertThat(createResponse.getStatusCode().value()).isEqualTo(201);
            assertThat(createResponse.getBody()).isNotNull();
            userGUID = createResponse.getBody().userGUID();
            assertThat(userGUID).isNotBlank();
        }

        @Test
        void login_withWrongPassword_redirectsToLoginWithInvalidLoginError() throws Exception {
            // 2. Log in with the right email but the wrong password. The failure handler
            //    redirects to the login page with the generic error code — the same code
            //    an unknown email produces, so the response never reveals whether the
            //    email has an account.
            HttpResponse<String> loginResponse = authServerClient.login(email, "WrongPassword1");
            assertThat(loginResponse.statusCode()).isEqualTo(302);
            assertThat(loginResponse.headers().firstValue("Location").orElseThrow())
                    .endsWith("/login?e=" + ErrorCode.INVALID_LOGIN);
        }

        @Test
        void login_withUnknownEmail_redirectsToLoginWithInvalidLoginError() throws Exception {
            // 2. Log in with an email no account has. The redirect must be identical to
            //    the wrong-password case (anti-enumeration).
            String unknownEmail = "nosuchuser_" + UUID.randomUUID() + "@example.com";
            HttpResponse<String> loginResponse = authServerClient.login(unknownEmail, TEST_PASSWORD);
            assertThat(loginResponse.statusCode()).isEqualTo(302);
            assertThat(loginResponse.headers().firstValue("Location").orElseThrow())
                    .endsWith("/login?e=" + ErrorCode.INVALID_LOGIN);
        }
    }

    @Nested
    class MfaEnabled_EmailVerified_PasswordChangeNotRequired {
        @BeforeEach
        void createTestAccount() {
            // 1. Create an account with default roles and MFA enabled — the first factor
            //    alone must not complete the login.
            email = "itest_" + UUID.randomUUID() + "@example.com";
            ResponseEntity<CreateTestAccountResponse> createResponse = accountManagementClient.createTestAccount(
                    TEST_NAME, email, TEST_PASSWORD,
                    true /* mfaEnabled */, true /* emailVerified */, false /* passwordChangeRequired */);
            assertThat(createResponse.getStatusCode().value()).isEqualTo(201);
            assertThat(createResponse.getBody()).isNotNull();
            userGUID = createResponse.getBody().userGUID();
            assertThat(userGUID).isNotBlank();
        }

        @Test
        void login_noRememberMe_browserNotRemembered() throws Exception {
            // 2. Start the authorization-code flow and log in (no remember-me).
            String redirectUri = webClientLocation + "/callback";
            HttpResponse<String> authorizeResponse =
                    authServerClient.startOAuth2AuthorizationFlow("WEB_CLIENT", redirectUri, "openid WEB_CLIENT_READ", "test-state");
            assertThat(authorizeResponse.statusCode()).isEqualTo(302);
            HttpFlowUtils.followRedirects(authServerClient, authServerLocation, authorizeResponse, redirectUri);

            // 3. The first factor alone lands on the MFA page, not the callback.
            HttpResponse<String> loginResponse = authServerClient.login(email, TEST_PASSWORD);
            assertThat(loginResponse.statusCode()).isEqualTo(302);
            assertThat(loginResponse.headers().firstValue("Location").orElseThrow()).endsWith("/ott/login");
            HttpResponse<String> ottPage = authServerClient.getOnSession(authServerLocation + "/ott/login");
            assertThat(ottPage.statusCode()).isEqualTo(200);

            // 4. Obtain the OTT out-of-band and submit it, leaving "Remember this
            //    browser?" unchecked so MFA stays enabled.
            HttpResponse<String> ottResponse = authServerClient.generateOtt(email);
            assertThat(ottResponse.statusCode()).isEqualTo(200);
            String ott = ottResponse.body();
            assertThat(ott).isNotBlank();

            HttpResponse<String> verifyResponse = authServerClient.verifyOtt(ott, false /* rememberBrowser */);
            assertThat(verifyResponse.statusCode()).isEqualTo(302);

            // 5. The upgraded session completes the flow to the callback.
            HttpResponse<String> firstCallback = HttpFlowUtils.followRedirects(
                    authServerClient, authServerLocation, verifyResponse, redirectUri);
            assertThat(firstCallback.statusCode()).isEqualTo(302);
            String callback = firstCallback.headers().firstValue("Location").orElseThrow();
            assertThat(callback).startsWith(redirectUri);
            assertThat(HttpFlowUtils.extractQueryParam(callback, "code")).isNotBlank();

            // 6. The browser was not remembered, so MFA must still be enabled in the DB…
            ResponseEntity<UserCredentialTestingResponse> accountResponse =
                    accountManagementClient.getTestAccountByUserGUID(userGUID);
            assertThat(accountResponse.getStatusCode().value()).isEqualTo(200);
            assertThat(accountResponse.getBody()).isNotNull();
            assertThat(accountResponse.getBody().mfaEnabled()).isTrue();

            // 7. …and a second login after a "browser restart" is gated by the MFA page
            //    again.
            authServerClient.clearSessionCookies();

            HttpResponse<String> secondAuthorize =
                    authServerClient.startOAuth2AuthorizationFlow("WEB_CLIENT", redirectUri, "openid WEB_CLIENT_READ", "test-state-2");
            assertThat(secondAuthorize.statusCode()).isEqualTo(302);
            HttpFlowUtils.followRedirects(authServerClient, authServerLocation, secondAuthorize, redirectUri);

            HttpResponse<String> secondLoginResponse = authServerClient.login(email, TEST_PASSWORD);
            assertThat(secondLoginResponse.statusCode()).isEqualTo(302);
            assertThat(secondLoginResponse.headers().firstValue("Location").orElseThrow()).endsWith("/ott/login");
        }

        @Test
        void login_noRememberMe_browserRemembered() throws Exception {
            // 2. Start the authorization-code flow and log in (no remember-me).
            String redirectUri = webClientLocation + "/callback";
            HttpResponse<String> authorizeResponse =
                    authServerClient.startOAuth2AuthorizationFlow("WEB_CLIENT", redirectUri, "openid WEB_CLIENT_READ", "test-state");
            assertThat(authorizeResponse.statusCode()).isEqualTo(302);
            HttpFlowUtils.followRedirects(authServerClient, authServerLocation, authorizeResponse, redirectUri);

            // 3. The first factor alone lands on the MFA page, not the callback.
            HttpResponse<String> loginResponse = authServerClient.login(email, TEST_PASSWORD);
            assertThat(loginResponse.statusCode()).isEqualTo(302);
            assertThat(loginResponse.headers().firstValue("Location").orElseThrow()).endsWith("/ott/login");
            HttpResponse<String> ottPage = authServerClient.getOnSession(authServerLocation + "/ott/login");
            assertThat(ottPage.statusCode()).isEqualTo(200);

            // 4. Obtain the OTT out-of-band and submit it with "Remember this browser?"
            //    checked, which disables MFA for the user.
            HttpResponse<String> ottResponse = authServerClient.generateOtt(email);
            assertThat(ottResponse.statusCode()).isEqualTo(200);
            String ott = ottResponse.body();
            assertThat(ott).isNotBlank();

            HttpResponse<String> verifyResponse = authServerClient.verifyOtt(ott, true /* rememberBrowser */);
            assertThat(verifyResponse.statusCode()).isEqualTo(302);

            // 5. The upgraded session completes the flow to the callback.
            HttpResponse<String> firstCallback = HttpFlowUtils.followRedirects(
                    authServerClient, authServerLocation, verifyResponse, redirectUri);
            assertThat(firstCallback.statusCode()).isEqualTo(302);
            String callback = firstCallback.headers().firstValue("Location").orElseThrow();
            assertThat(callback).startsWith(redirectUri);
            assertThat(HttpFlowUtils.extractQueryParam(callback, "code")).isNotBlank();

            // 6. The browser was remembered, so MFA must now be disabled in the DB…
            ResponseEntity<UserCredentialTestingResponse> accountResponse =
                    accountManagementClient.getTestAccountByUserGUID(userGUID);
            assertThat(accountResponse.getStatusCode().value()).isEqualTo(200);
            assertThat(accountResponse.getBody()).isNotNull();
            assertThat(accountResponse.getBody().mfaEnabled()).isFalse();

            // 7. …and a second login after a "browser restart" completes without an OTT:
            //    the first factor redirects straight back to the saved authorize request
            //    and through to the callback.
            authServerClient.clearSessionCookies();

            HttpResponse<String> secondAuthorize =
                    authServerClient.startOAuth2AuthorizationFlow("WEB_CLIENT", redirectUri, "openid WEB_CLIENT_READ", "test-state-2");
            assertThat(secondAuthorize.statusCode()).isEqualTo(302);
            HttpFlowUtils.followRedirects(authServerClient, authServerLocation, secondAuthorize, redirectUri);

            HttpResponse<String> secondLoginResponse = authServerClient.login(email, TEST_PASSWORD);
            assertThat(secondLoginResponse.statusCode()).isEqualTo(302);
            assertThat(secondLoginResponse.headers().firstValue("Location").orElseThrow()).contains("/oauth2/authorize");

            HttpResponse<String> secondCallback = HttpFlowUtils.followRedirects(
                    authServerClient, authServerLocation, secondLoginResponse, redirectUri);
            assertThat(secondCallback.statusCode()).isEqualTo(302);
            String secondCallbackLocation = secondCallback.headers().firstValue("Location").orElseThrow();
            assertThat(secondCallbackLocation).startsWith(redirectUri);
            assertThat(HttpFlowUtils.extractQueryParam(secondCallbackLocation, "code")).isNotBlank();
        }

        @Test
        void login_withRememberMe_browserNotRemembered() throws Exception {
            // 2. Start the authorization-code flow and log in with "remember me" checked.
            String redirectUri = webClientLocation + "/callback";
            HttpResponse<String> authorizeResponse =
                    authServerClient.startOAuth2AuthorizationFlow("WEB_CLIENT", redirectUri, "openid WEB_CLIENT_READ", "test-state");
            assertThat(authorizeResponse.statusCode()).isEqualTo(302);
            HttpFlowUtils.followRedirects(authServerClient, authServerLocation, authorizeResponse, redirectUri);

            // 3. The first factor lands on the MFA page and issues the persistent
            //    remember-me cookie (the cookie covers the first factor only — the
            //    remember-me provider applies the same MFA check as the password one).
            HttpResponse<String> loginResponse = authServerClient.login(email, TEST_PASSWORD, true /* rememberMe */);
            assertThat(loginResponse.statusCode()).isEqualTo(302);
            assertThat(loginResponse.headers().firstValue("Location").orElseThrow()).endsWith("/ott/login");
            String rememberMeSetCookie = loginResponse.headers().allValues("set-cookie").stream()
                    .filter(header -> header.startsWith("remember-me="))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No remember-me cookie issued on the login response"));
            assertThat(HttpCookie.parse(rememberMeSetCookie).get(0).getValue()).isNotBlank();
            HttpResponse<String> ottPage = authServerClient.getOnSession(authServerLocation + "/ott/login");
            assertThat(ottPage.statusCode()).isEqualTo(200);

            // 4. Obtain the OTT out-of-band and submit it, leaving "Remember this
            //    browser?" unchecked so MFA stays enabled.
            HttpResponse<String> ottResponse = authServerClient.generateOtt(email);
            assertThat(ottResponse.statusCode()).isEqualTo(200);
            String ott = ottResponse.body();
            assertThat(ott).isNotBlank();

            HttpResponse<String> verifyResponse = authServerClient.verifyOtt(ott, false /* rememberBrowser */);
            assertThat(verifyResponse.statusCode()).isEqualTo(302);

            // 5. The upgraded session completes the flow to the callback.
            HttpResponse<String> firstCallback = HttpFlowUtils.followRedirects(
                    authServerClient, authServerLocation, verifyResponse, redirectUri);
            assertThat(firstCallback.statusCode()).isEqualTo(302);
            String callback = firstCallback.headers().firstValue("Location").orElseThrow();
            assertThat(callback).startsWith(redirectUri);
            assertThat(HttpFlowUtils.extractQueryParam(callback, "code")).isNotBlank();

            // 6. The browser was not remembered, so MFA must still be enabled in the DB…
            ResponseEntity<UserCredentialTestingResponse> accountResponse =
                    accountManagementClient.getTestAccountByUserGUID(userGUID);
            assertThat(accountResponse.getStatusCode().value()).isEqualTo(200);
            assertThat(accountResponse.getBody()).isNotNull();
            assertThat(accountResponse.getBody().mfaEnabled()).isTrue();

            // 7. …so after a "browser restart" the remember-me cookie satisfies the first
            //    factor (no login form, no credentials posted) but the flow is still
            //    gated by the MFA page: the chain dead-ends with the OTT page displayed.
            authServerClient.clearSessionCookies();

            HttpResponse<String> secondAuthorize =
                    authServerClient.startOAuth2AuthorizationFlow("WEB_CLIENT", redirectUri, "openid WEB_CLIENT_READ", "test-state-2");
            assertThat(secondAuthorize.statusCode()).isEqualTo(302);
            HttpResponse<String> secondOttPage = HttpFlowUtils.followRedirects(
                    authServerClient, authServerLocation, secondAuthorize, redirectUri);
            assertThat(secondOttPage.statusCode()).isEqualTo(200);
            assertThat(secondOttPage.uri().getPath()).isEqualTo("/ott/login");

            // 8. A fresh OTT completes the second login to the callback.
            HttpResponse<String> secondOttResponse = authServerClient.generateOtt(email);
            assertThat(secondOttResponse.statusCode()).isEqualTo(200);
            String secondOtt = secondOttResponse.body();
            assertThat(secondOtt).isNotBlank();

            HttpResponse<String> secondVerifyResponse = authServerClient.verifyOtt(secondOtt, false /* rememberBrowser */);
            assertThat(secondVerifyResponse.statusCode()).isEqualTo(302);
            HttpResponse<String> secondCallback = HttpFlowUtils.followRedirects(
                    authServerClient, authServerLocation, secondVerifyResponse, redirectUri);
            assertThat(secondCallback.statusCode()).isEqualTo(302);
            String secondCallbackLocation = secondCallback.headers().firstValue("Location").orElseThrow();
            assertThat(secondCallbackLocation).startsWith(redirectUri);
            assertThat(HttpFlowUtils.extractQueryParam(secondCallbackLocation, "code")).isNotBlank();
        }

        @Test
        void login_withRememberMe_browserRemembered() throws Exception {
            // 2. Start the authorization-code flow and log in with "remember me" checked.
            String redirectUri = webClientLocation + "/callback";
            HttpResponse<String> authorizeResponse =
                    authServerClient.startOAuth2AuthorizationFlow("WEB_CLIENT", redirectUri, "openid WEB_CLIENT_READ", "test-state");
            assertThat(authorizeResponse.statusCode()).isEqualTo(302);
            HttpFlowUtils.followRedirects(authServerClient, authServerLocation, authorizeResponse, redirectUri);

            // 3. The first factor lands on the MFA page and issues the persistent
            //    remember-me cookie.
            HttpResponse<String> loginResponse = authServerClient.login(email, TEST_PASSWORD, true /* rememberMe */);
            assertThat(loginResponse.statusCode()).isEqualTo(302);
            assertThat(loginResponse.headers().firstValue("Location").orElseThrow()).endsWith("/ott/login");
            String rememberMeSetCookie = loginResponse.headers().allValues("set-cookie").stream()
                    .filter(header -> header.startsWith("remember-me="))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No remember-me cookie issued on the login response"));
            assertThat(HttpCookie.parse(rememberMeSetCookie).get(0).getValue()).isNotBlank();
            HttpResponse<String> ottPage = authServerClient.getOnSession(authServerLocation + "/ott/login");
            assertThat(ottPage.statusCode()).isEqualTo(200);

            // 4. Obtain the OTT out-of-band and submit it with "Remember this browser?"
            //    checked, which disables MFA for the user.
            HttpResponse<String> ottResponse = authServerClient.generateOtt(email);
            assertThat(ottResponse.statusCode()).isEqualTo(200);
            String ott = ottResponse.body();
            assertThat(ott).isNotBlank();

            HttpResponse<String> verifyResponse = authServerClient.verifyOtt(ott, true /* rememberBrowser */);
            assertThat(verifyResponse.statusCode()).isEqualTo(302);

            // 5. The upgraded session completes the flow to the callback.
            HttpResponse<String> firstCallback = HttpFlowUtils.followRedirects(
                    authServerClient, authServerLocation, verifyResponse, redirectUri);
            assertThat(firstCallback.statusCode()).isEqualTo(302);
            String callback = firstCallback.headers().firstValue("Location").orElseThrow();
            assertThat(callback).startsWith(redirectUri);
            assertThat(HttpFlowUtils.extractQueryParam(callback, "code")).isNotBlank();

            // 6. The browser was remembered, so MFA must now be disabled in the DB…
            ResponseEntity<UserCredentialTestingResponse> accountResponse =
                    accountManagementClient.getTestAccountByUserGUID(userGUID);
            assertThat(accountResponse.getStatusCode().value()).isEqualTo(200);
            assertThat(accountResponse.getBody()).isNotNull();
            assertThat(accountResponse.getBody().mfaEnabled()).isFalse();

            // 7. …so after a "browser restart" the second flow completes with no
            //    interaction at all: the remember-me cookie satisfies the first factor
            //    (no login form, no credentials posted) and MFA is off (no OTT step) —
            //    the redirect chain runs straight through to the callback.
            authServerClient.clearSessionCookies();

            HttpResponse<String> secondAuthorize =
                    authServerClient.startOAuth2AuthorizationFlow("WEB_CLIENT", redirectUri, "openid WEB_CLIENT_READ", "test-state-2");
            assertThat(secondAuthorize.statusCode()).isEqualTo(302);
            HttpResponse<String> secondCallback = HttpFlowUtils.followRedirects(
                    authServerClient, authServerLocation, secondAuthorize, redirectUri);
            assertThat(secondCallback.statusCode()).isEqualTo(302);
            String secondCallbackLocation = secondCallback.headers().firstValue("Location").orElseThrow();
            assertThat(secondCallbackLocation).startsWith(redirectUri);
            String code = HttpFlowUtils.extractQueryParam(secondCallbackLocation, "code");
            assertThat(code).isNotBlank();

            // 8. Exchange the code and confirm the cookie-only login is the same user.
            TokenResponse tokens = oAuth2Client.getAuthorizationGrantToken(code, "WEB_CLIENT", webClientSecret, redirectUri);
            assertThat(tokens.accessToken()).isNotBlank();
            byte[] payloadBytes = Base64.getUrlDecoder().decode(tokens.accessToken().split("\\.")[1]);
            Map<String, Object> claims = new ObjectMapper().readValue(payloadBytes, new TypeReference<>() {});
            assertThat(claims.get("sub")).isEqualTo(email);
        }
    }

}
