package com.roots.authserver.exception;

/**
 * A Google (or future provider) login could not be completed: the id_token failed
 * verification, carried an unverified email, or was missing required claims. The
 * controller answers every case with the same generic redirect
 * ({@code /login?e=social_login_failed}) — the specific reason goes to the log only.
 */
public class SocialLoginException extends RuntimeException {
    public SocialLoginException(String message) {
        super(message);
    }

    public SocialLoginException(String message, Throwable cause) {
        super(message, cause);
    }
}
