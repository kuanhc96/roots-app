package com.roots.account_management.validator;

import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Component;

import com.roots.account_management.dto.request.CreateAccountRequest;
import com.roots.account_management.dto.request.DeleteAccountsRequest;
import com.roots.account_management.dto.request.UpdateMfaRequest;
import com.roots.account_management.exception.InvalidRequestException;

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

    public void validateAccountLookup(String email, String userGUID) {
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasUserGUID = userGUID != null && !userGUID.isBlank();

        if (hasEmail && hasUserGUID) {
            throw new InvalidRequestException("Provide either email or userGUID, not both");
        }
        if (!hasEmail && !hasUserGUID) {
            throw new InvalidRequestException("Provide either email or userGUID");
        }
    }

    public void validateSearchInput(String email, String name, int maxCount) {
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasName = name != null && !name.isBlank();

        if (hasEmail && hasName) {
            throw new InvalidRequestException("Provide either email or name, not both");
        }
        if (!hasEmail && !hasName) {
            throw new InvalidRequestException("Provide either email or name");
        }
        if (maxCount <= 0) {
            throw new InvalidRequestException("maxCount must be greater than 0");
        }
    }

    public void validateDeleteAccountsRequest(DeleteAccountsRequest request, int maxDeleteCount) {
        if (request == null || ObjectUtils.isEmpty(request.userGUIDs())) {
            throw new InvalidRequestException("userGUIDs must contain at least one value");
        }

        List<String> userGUIDs = request.userGUIDs();
        if (userGUIDs.size() > maxDeleteCount) {
            throw new InvalidRequestException("userGUIDs must contain at most " + maxDeleteCount + " values");
        }

        boolean hasBlank = userGUIDs.stream().anyMatch(guid -> guid == null || guid.isBlank());
        if (hasBlank) {
            throw new InvalidRequestException("userGUIDs must not contain blank values");
        }
    }

    public void validateUserGUID(String userGUID) {
        if (userGUID == null || userGUID.isBlank()) {
            throw new InvalidRequestException("userGUID is required");
        }
    }

    public void validateUpdateMfaRequest(UpdateMfaRequest request) {
        if (request == null || request.mfaEnabled() == null) {
            throw new InvalidRequestException("mfaEnabled is required");
        }
    }

    public void validatePagination(int page, int size, int maxSize) {
        if (page < 0) {
            throw new InvalidRequestException("page must be greater than or equal to 0");
        }
        if (size <= 0) {
            throw new InvalidRequestException("size must be greater than 0");
        }
        if (size > maxSize) {
            throw new InvalidRequestException("size must be less than or equal to " + maxSize);
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

    private void validatePassword(String password) {
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
}
