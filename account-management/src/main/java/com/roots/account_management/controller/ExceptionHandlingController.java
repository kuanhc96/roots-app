package com.roots.account_management.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.roots.account_management.exception.EmailAlreadyExistsException;
import com.roots.account_management.exception.InvalidRequestException;
import com.roots.account_management.exception.UserCredentialNotFoundException;

@RestControllerAdvice
public class ExceptionHandlingController {

    @ExceptionHandler(InvalidRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleInvalidRequest(InvalidRequestException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(UserCredentialNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleResourceNotFound(UserCredentialNotFoundException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleEmailAlreadyExists(EmailAlreadyExistsException ex) {
        return Map.of("error", ex.getMessage());
    }
}
