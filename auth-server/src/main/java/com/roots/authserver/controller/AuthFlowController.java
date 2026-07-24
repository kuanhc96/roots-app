package com.roots.authserver.controller;

import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ott.JdbcOneTimeTokenService;
import org.springframework.security.authentication.ott.OneTimeTokenAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriUtils;

import com.roots.authserver.dto.request.CreateAccountRequest;
import com.roots.authserver.enums.ErrorCode;
import com.roots.authserver.exception.EmailAlreadyExistsException;
import com.roots.authserver.exception.InvalidRequestException;
import com.roots.authserver.principal.CreateAccountPendingAuthenticationToken;
import com.roots.authserver.principal.GuestAuthenticationToken;
import com.roots.authserver.principal.MfaAuthenticationToken;
import com.roots.authserver.principal.MfaPendingAuthenticationToken;
import com.roots.authserver.exception.SocialLoginException;
import com.roots.authserver.principal.PasswordChangePendingAuthenticationToken;
import com.roots.authserver.principal.SocialLoginAuthenticationToken;
import com.roots.authserver.service.GoogleAuthorizeService;
import com.roots.authserver.service.InMemoryOneTimePinService;
import com.roots.authserver.service.MagicLinkService;
import com.roots.authserver.service.SocialLoginService;
import com.roots.authserver.service.UserCredentialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Tag(
        name = "Auth flows",
        description = "Browser-facing form-post endpoints of the interactive login flows: "
                + "OTT (MFA) verification, account-creation magic-link verification, "
                + "forgot-password reset, and guest login. Page GETs are generally not "
                + "handled here — the Nuxt SPA shell is served for any non-API path by the "
                + "static-resource fallback (SpaFallbackConfig) and the client router takes "
                + "over. The one exception is the page paths that share a URL with a POST "
                + "mapping in this class: see forwardSpaShell."
)
@Controller
@RequiredArgsConstructor
public class AuthFlowController {
    /** Must match the path of {@link #googleCallback}. */
    private static final String GOOGLE_CALLBACK_PATH = "/login/google/callback";

    private final InMemoryOneTimePinService inMemoryOneTimePinService;
    private final JdbcOneTimeTokenService jdbcOneTimeTokenService;
    private final UserCredentialService userCredentialService;
    private final UserDetailsService userDetailsService;
    private final MagicLinkService magicLinkService;
    private final SocialLoginService socialLoginService;
    private final GoogleAuthorizeService googleAuthorizeService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    @Value("${web-client.location:http://localhost:3000}")
    private String webClientLocation;

    // This server's own externally reachable base URL — what the browser can hit. Used
    // to build the Google redirect_uri ({external-location}/login/google/callback),
    // which must byte-for-byte match a redirect URI registered in Google Cloud Console.
    @Value("${auth-server.external-location:http://localhost:9000}")
    private String authServerExternalLocation;

    @Operation(
            summary = "Serve the SPA shell for page paths that also have a POST mapping",
            description = "When a URL matches a @RequestMapping path but not its method, "
                    + "Spring MVC responds 405 instead of falling through to the "
                    + "static-resource fallback — so the pages living on the same paths as "
                    + "this controller's form POSTs need an explicit GET forward to the SPA "
                    + "shell. Every other page path is served by SpaFallbackConfig.",
            responses = @ApiResponse(responseCode = "200", description = "The Nuxt SPA shell",
                    content = @Content(mediaType = "text/html"))
    )
    @GetMapping({"/ott/login", "/magic-link/login", "/reset-password", "/signup"})
    public String forwardSpaShell() {
        return "forward:/index.html";
    }

    @Operation(
            summary = "Create an account (public signup form post)",
            description = "Browser form post from the Nuxt /signup page. Re-validates the fields "
                    + "server-side and creates the credential with a default member role, MFA "
                    + "enabled, and email unverified. On success, establishes the "
                    + "email-verification pending session (CreateAccountPendingAuthenticationToken), "
                    + "emails the magic link, and redirects to /signup/success — no separate login "
                    + "round trip. Rejections redirect back to /signup with an error code and the "
                    + "submitted name/email for prefill.",
            responses = @ApiResponse(responseCode = "302",
                    description = "Redirect to /signup/success on success, or back to /signup with "
                            + "e=invalid_request (validation failure) or e=email_taken (duplicate email)",
                    content = @Content)
    )
    @PostMapping("/signup")
    public String signup(
            @Parameter(description = "Display name; 255 characters or fewer")
            @RequestParam(required = false) String name,
            @Parameter(description = "Email address; becomes the login username")
            @RequestParam(required = false) String email,
            @Parameter(description = "Password; must satisfy the shared complexity policy")
            @RequestParam(required = false) String password,
            HttpServletRequest request,
            HttpServletResponse response) {
        try {
            userCredentialService.createAccount(new CreateAccountRequest(name, email, password));
        } catch (InvalidRequestException e) {
            return signupErrorRedirect(ErrorCode.INVALID_REQUEST, name, email);
        } catch (EmailAlreadyExistsException e) {
            return signupErrorRedirect(ErrorCode.EMAIL_TAKEN, name, email);
        }

        // The account was created in this very request, so the first factor is proven
        // without a second /login round trip: establish the email-verification pending
        // session directly, exactly as a form login with an unverified email would.
        UserDetails user = userDetailsService.loadUserByUsername(email);
        CreateAccountPendingAuthenticationToken pending = new CreateAccountPendingAuthenticationToken(user);
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(pending);
        SecurityContextHolder.setContext(securityContext);
        securityContextRepository.saveContext(securityContext, request, response);

        magicLinkService.issueAndEmail(email);
        return "redirect:/signup/success";
    }

    private static String signupErrorRedirect(ErrorCode code, String name, String email) {
        // Strict percent-encoding (space -> %20, + -> %2B) so the values survive both
        // the Location header and Vue Router's query parsing on the signup page.
        return "redirect:/signup?e=" + code
                + "&name=" + UriUtils.encode(StringUtils.isBlank(name) ? "" : name, StandardCharsets.UTF_8)
                + "&email=" + UriUtils.encode(StringUtils.isBlank(name) ? "" : email, StandardCharsets.UTF_8);
    }

    @Operation(
            summary = "Verify the MFA one-time token",
            description = "Verifies the submitted one-time token against the session's MFA-pending "
                    + "user (an MfaPendingAuthenticationToken must be in the session, else redirect "
                    + "to /login). If rememberBrowser=true, disables MFA for the user. On success, "
                    + "upgrades the session to a fully authenticated MfaAuthenticationToken and "
                    + "redirects to the saved OAuth2 authorization request; an invalid token redirects "
                    + "back to /ott/login with an error.",
            responses = @ApiResponse(responseCode = "302",
                    description = "Redirect to the saved OAuth2 request on success, or back to "
                            + "/ott/login or /login with an error",
                    content = @Content)
    )
    @PostMapping("/ott/login")
    public String verifyOtt(
            @Parameter(description = "The one-time token the user received")
            @RequestParam String ott,
            @Parameter(description = "When true, disables MFA for the user (\"Remember this browser?\")")
            @RequestParam(required = false, defaultValue = "false") boolean rememberBrowser,
            HttpServletRequest request,
            HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(!(authentication instanceof MfaPendingAuthenticationToken pending)) {
            return "redirect:/login";
        }

        UserDetails user = (UserDetails) pending.getPrincipal();
        var consumedToken = inMemoryOneTimePinService.consume(new OneTimeTokenAuthenticationToken(ott));
        if (consumedToken == null || !consumedToken.getUsername().equals(user.getUsername())) {
            return "redirect:/ott/login?e=" + ErrorCode.INVALID_TOKEN;
        } else {
            if (rememberBrowser) {
                userCredentialService.disableMfa(user.getUsername());
            }

            MfaAuthenticationToken full = new MfaAuthenticationToken(user, user.getAuthorities());
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(full);
            SecurityContextHolder.setContext(securityContext);
            securityContextRepository.saveContext(securityContext, request, response);

            SavedRequest savedRequest = new HttpSessionRequestCache().getRequest(request, response);
            if (savedRequest != null) {
                return "redirect:" + savedRequest.getRedirectUrl();
            } else {
                return "redirect:/ott/login?e=" + ErrorCode.OAUTH_REDIRECT_FAILED;
            }
        }
    }

    @Operation(
            summary = "Verify the account-creation magic link",
            description = "Completes email verification for a new account. Requires a "
                    + "CreateAccountPendingAuthenticationToken in the session (else redirect to "
                    + "/login). The Nuxt magic-link page reads the token from the emailed link's "
                    + "query string and posts it as a hidden field — consuming the token takes a "
                    + "deliberate button click, so an email client that merely prefetches the GET "
                    + "cannot burn it. Consumes the token, verifies it belongs to the pending "
                    + "account, marks the email verified, upgrades the session, and redirects to "
                    + "the saved OAuth2 request; a missing or invalid token redirects back with "
                    + "e=invalid_token.",
            responses = @ApiResponse(responseCode = "302",
                    description = "Redirect to the saved OAuth2 request (or the web-client base URL "
                            + "when none exists), or back to /magic-link/login or /login with an error",
                    content = @Content)
    )
    @PostMapping("/magic-link/login")
    public String verifyMagicLink(
            @Parameter(description = "The magic-link token from the emailed link, posted by the Nuxt page")
            @RequestParam(required = false) String magicLinkToken,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(!(authentication instanceof CreateAccountPendingAuthenticationToken pending)) {
            return "redirect:/login";
        }

        if (StringUtils.isBlank(magicLinkToken)) {
            return "redirect:/magic-link/login?e=" + ErrorCode.INVALID_TOKEN;
        }

        UserDetails user = (UserDetails) pending.getPrincipal();
        var consumedToken = jdbcOneTimeTokenService.consume(new OneTimeTokenAuthenticationToken(magicLinkToken));
        if (consumedToken == null || !consumedToken.getUsername().equals(user.getUsername())) {
            return "redirect:/magic-link/login?e=" + ErrorCode.INVALID_TOKEN;
        } else {
            userCredentialService.verifyEmail(user.getUsername());

            MfaAuthenticationToken full = new MfaAuthenticationToken(user, user.getAuthorities());
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(full);
            SecurityContextHolder.setContext(securityContext);
            securityContextRepository.saveContext(securityContext, request, response);

            SavedRequest savedRequest = new HttpSessionRequestCache().getRequest(request, response);
            if (savedRequest != null) {
                return "redirect:" + savedRequest.getRedirectUrl();
            } else {
                // No in-progress OAuth2 flow (direct /signup visit). Hand off to web-client,
                // which initiates /oauth2/authorize with its own state. The session is now
                // authenticated, so a code is issued and the callback succeeds.
                return "redirect:" + webClientLocation;
            }
        }
    }

    @Operation(
            summary = "Complete the forgot-password reset",
            description = "Sets the new password for a temp-password login. Requires a "
                    + "PasswordChangePendingAuthenticationToken in the session (else redirect to "
                    + "/login). Validates the new password against the shared complexity policy "
                    + "(failure redirects back with e=invalid_password), stores it, clears the "
                    + "password-change flag, marks the email verified, upgrades the session, and "
                    + "redirects to the saved OAuth2 request.",
            responses = @ApiResponse(responseCode = "302",
                    description = "Redirect to the saved OAuth2 request (or the web-client base URL "
                            + "when none exists), or back to /reset-password or /login with an error",
                    content = @Content)
    )
    @PostMapping("/reset-password")
    public String resetPassword(
            @Parameter(description = "The new password; must satisfy the shared complexity policy")
            @RequestParam String newPassword,
            HttpServletRequest request,
            HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof PasswordChangePendingAuthenticationToken pending)) {
            return "redirect:/login";
        }

        UserDetails user = (UserDetails) pending.getPrincipal();
        try {
            userCredentialService.completePasswordReset(user.getUsername(), newPassword);
        } catch (InvalidRequestException e) {
            return "redirect:/reset-password?e=" + ErrorCode.INVALID_PASSWORD;
        }

        MfaAuthenticationToken full = new MfaAuthenticationToken(user, user.getAuthorities());
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(full);
        SecurityContextHolder.setContext(securityContext);
        securityContextRepository.saveContext(securityContext, request, response);

        SavedRequest savedRequest = new HttpSessionRequestCache().getRequest(request, response);
        if (savedRequest != null) {
            return "redirect:" + savedRequest.getRedirectUrl();
        } else {
            return "redirect:" + webClientLocation;
        }
    }

    @Operation(
            summary = "Kick off Google Sign-In",
            description = "Browser navigation from the Login form's Google button (a plain link, "
                    + "no JS SDK involved). Mints an oauth_state, stores it in Redis keyed by this "
                    + "session's id (5 min TTL), and redirects the browser to Google's authorization "
                    + "endpoint with client_id, scope, and redirect_uri=" + GOOGLE_CALLBACK_PATH
                    + " filled in. Unconditional — an already-authenticated Google session on the "
                    + "browser's side just completes the flow silently.",
            responses = @ApiResponse(responseCode = "302",
                    description = "Redirect to Google's authorization endpoint", content = @Content)
    )
    @GetMapping("/login/google/authorize")
    public String authorizeWithGoogle(HttpServletRequest request) {
        String sessionId = request.getSession(true).getId();
        return "redirect:" + googleAuthorizeService.buildAuthorizeRedirect(
                sessionId, authServerExternalLocation + GOOGLE_CALLBACK_PATH);
    }

    @Operation(
            summary = "Complete Google Sign-In",
            description = "Where Google sends the browser back with the authorization code (this "
                    + "path is the registered redirect_uri — see authorizeWithGoogle). Validates the "
                    + "returned state against the one minted for this session (single-use — consumed "
                    + "whether it matches or not), exchanges the code for tokens server-side (the "
                    + "client secret never reaches the browser), verifies the resulting id_token "
                    + "(signature, issuer, expiry, audience), and resolves it to a local account "
                    + "sub-first: an existing social binding wins; otherwise a verified matching "
                    + "email is linked, or a new account is created (member role, email verified, "
                    + "unusable random password). The session becomes a fully authenticated "
                    + "SocialLoginAuthenticationToken — Google is trusted as both factors, so no OTT "
                    + "step runs regardless of is_mfa_enabled — and the browser is redirected to the "
                    + "saved OAuth2 authorization request. Any failure (Google's own error param, a "
                    + "state mismatch, or a rejected exchange) redirects to /login with one generic "
                    + "code; the specific reason is log-only.",
            responses = @ApiResponse(responseCode = "302",
                    description = "Redirect to the saved OAuth2 request (or the web-client base URL "
                            + "when none exists), or to /login with e=social_login_failed",
                    content = @Content)
    )
    @GetMapping(GOOGLE_CALLBACK_PATH)
    public String googleCallback(
            @Parameter(description = "The authorization code, present on success")
            @RequestParam(required = false) String code,
            @Parameter(description = "The state minted by authorizeWithGoogle, echoed back by Google")
            @RequestParam(required = false) String state,
            @Parameter(description = "Present when the user denied consent or Google rejected the request")
            @RequestParam(required = false) String error,
            HttpServletRequest request,
            HttpServletResponse response) {
        String sessionId = request.getSession(true).getId();
        boolean stateValid = googleAuthorizeService.consumeAndValidateState(sessionId, state);

        if (error != null) {
            log.warn("Google authorization failed for session {}: {}", sessionId, error);
            return "redirect:/login?e=" + ErrorCode.SOCIAL_LOGIN_FAILED;
        }
        if (StringUtils.isBlank(code) || !stateValid) {
            log.warn("Google callback for session {} is missing code or failed state validation", sessionId);
            return "redirect:/login?e=" + ErrorCode.SOCIAL_LOGIN_FAILED;
        }

        String email;
        try {
            email = socialLoginService.loginWithGoogleCode(code, authServerExternalLocation + GOOGLE_CALLBACK_PATH);
        } catch (SocialLoginException e) {
            // One generic code for every failure mode; the specific reason is log-only.
            log.warn("Google login rejected: {}", e.getMessage());
            return "redirect:/login?e=" + ErrorCode.SOCIAL_LOGIN_FAILED;
        }

        UserDetails user = userDetailsService.loadUserByUsername(email);
        SocialLoginAuthenticationToken full = new SocialLoginAuthenticationToken(user, user.getAuthorities());
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(full);
        SecurityContextHolder.setContext(securityContext);
        securityContextRepository.saveContext(securityContext, request, response);

        SavedRequest savedRequest = new HttpSessionRequestCache().getRequest(request, response);
        if (savedRequest != null) {
            return "redirect:" + savedRequest.getRedirectUrl();
        } else {
            // No in-progress OAuth2 flow (direct /login visit). Hand off to web-client,
            // which initiates /oauth2/authorize with its own state.
            return "redirect:" + webClientLocation;
        }
    }

    @Operation(
            summary = "Log in as guest",
            description = "Authenticates the session as a synthetic guest user — no credentials or "
                    + "MFA; username \"guest\" with authority GUEST — and redirects to the saved "
                    + "OAuth2 authorization request.",
            responses = @ApiResponse(responseCode = "302",
                    description = "Redirect to the saved OAuth2 request on success, or back to "
                            + "/login with an error when none exists",
                    content = @Content)
    )
    @PostMapping("/login/guest")
    public String loginAsGuest(HttpServletRequest request, HttpServletResponse response) {
        Authentication guestAuth = authenticationManager.authenticate(new GuestAuthenticationToken());
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(guestAuth);
        SecurityContextHolder.setContext(securityContext);
        securityContextRepository.saveContext(securityContext, request, response);

        SavedRequest savedRequest = new HttpSessionRequestCache().getRequest(request, response);
        if (savedRequest != null) {
            return "redirect:" + savedRequest.getRedirectUrl();
        } else {
            return "redirect:/login?e=" + ErrorCode.OAUTH_REDIRECT_FAILED;
        }
    }
}
