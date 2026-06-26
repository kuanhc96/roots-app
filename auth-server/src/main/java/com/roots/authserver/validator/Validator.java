package com.roots.authserver.validator;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.roots.authserver.dto.request.CreateAccountRequest;
import com.roots.authserver.exception.InvalidRequestException;

/**
 * Holds validation logic shared across flows. {@link #validatePassword} is the
 * single source of truth for the password complexity policy, reused by account
 * creation ({@link #validateCreateAccountRequest}) and the forgot-password reset flow.
 */
@Component
public class Validator {

    private static final int MAX_NAME_LENGTH = 255;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");

    public void validateCreateAccountRequest(CreateAccountRequest request) {
        validateName(request.name());
        validateEmail(request.email());
        validatePassword(request.password());
    }

    public void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new InvalidRequestException("Password is required");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new InvalidRequestException("Password must be at least 8 characters");
        }
        if (!UPPERCASE.matcher(password).find()) {
            throw new InvalidRequestException("Password must include at least one uppercase letter");
        }
        if (!LOWERCASE.matcher(password).find()) {
            throw new InvalidRequestException("Password must include at least one lowercase letter");
        }
        if (!DIGIT.matcher(password).find()) {
            throw new InvalidRequestException("Password must include at least one number");
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidRequestException("Name is required");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new InvalidRequestException("Name must be 255 characters or fewer");
        }
    }

    private void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new InvalidRequestException("Email is required");
        }
        if (!email.contains("@")) {
            throw new InvalidRequestException("Email must contain an \"@\"");
        }
    }
}
