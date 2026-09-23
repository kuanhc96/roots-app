package com.roots.authserver.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogoutTokenService {

    private final JwtEncoder jwtEncoder;

    /**
     * Generates a signed LogoutToken JWT for notifying an RP of logout.
     * The session ID is hashed using SHA-256 to match the `sid` claim in id_tokens.
     */
    public String generateLogoutToken(String clientId, String iss, Authentication authentication,
                                      HttpSession session) throws NoSuchAlgorithmException {
        String jti = UUID.randomUUID().toString();
        String hashedSid = createSessionIdHash(session.getId());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(iss)
                .subject(authentication.getName())
                .audience(List.of(clientId))
                .issuedAt(Instant.now())
                .claim("sid", hashedSid)
                .claim("jti", jti)
                .claim("events", Map.of("http://schemas.openid.net/event/backchannel-logout", Map.of()))
                .build();

        JwsHeader header = JwsHeader.with(() -> "RS256").build();
        String encodedJwt = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return encodedJwt;
    }

    /**
     * Asynchronously sends LogoutTokens to all RPs that have back-channel logout enabled.
     * Errors are logged per-RP so one failing client doesn't block the others or the logout flow.
     */
    @Async
    public void sendBackChannelLogoutAsync(List<RegisteredClient> registeredClients, String clientId,
                                           String iss, Authentication authentication, HttpSession session) {
        for (RegisteredClient client : registeredClients) {
            try {
                Boolean sessionRequired = client.getClientSettings()
                        .getSetting("backchannel-logout-session-required");
                String backChannelLogoutUri = client.getClientSettings()
                        .getSetting("backchannel-logout-uri");

                if (StringUtils.hasText(backChannelLogoutUri) && Boolean.TRUE.equals(sessionRequired)) {
                    String logoutToken = generateLogoutToken(client.getClientId(), iss, authentication, session);
                    sendLogoutTokenToRp(backChannelLogoutUri, logoutToken, client.getClientId());
                }
            } catch (Exception e) {
                log.warn("Failed to send back-channel logout token for client {}", client.getClientId(), e);
            }
        }
    }

    /**
     * POSTs the LogoutToken to the RP's back-channel logout endpoint.
     * Uses a 5-second timeout to prevent slow RPs from hanging the logout flow.
     */
    private void sendLogoutTokenToRp(String backChannelLogoutUri, String logoutToken, String clientId) {
        RestClient restClient = RestClient.builder()
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout(5000);
                    setReadTimeout(5000);
                }})
                .build();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("logout_token", logoutToken);

        try {
            restClient.post()
                    .uri(backChannelLogoutUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();

            log.debug("Successfully sent back-channel logout token to client {} at {}", clientId,
                    backChannelLogoutUri);
        } catch (Exception e) {
            log.warn("Failed to send back-channel logout request to client {} at {}: {}",
                    clientId, backChannelLogoutUri, e.getMessage());
        }
    }

    /**
     * Hashes the session ID using SHA-256 and returns it as a Base64-URL-encoded string
     * (without padding), matching the format used in id_token sid claims.
     */
    private static String createSessionIdHash(String sessionId) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(sessionId.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }
}
