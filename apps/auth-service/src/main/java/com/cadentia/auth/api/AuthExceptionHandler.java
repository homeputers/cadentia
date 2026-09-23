package com.cadentia.auth.api;

import com.cadentia.auth.service.AuthException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(AuthException.class)
    ResponseEntity<AuthProblem> handleAuthException(AuthException exception) {
        return ResponseEntity.status(exception.status()).body(new AuthProblem(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<AuthProblem> handleValidation(MethodArgumentNotValidException exception) {
        return ResponseEntity.badRequest().body(new AuthProblem("VALIDATION_ERROR", "The request contains invalid fields."));
    }

    @ExceptionHandler({IllegalArgumentException.class})
    ResponseEntity<AuthProblem> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new AuthProblem("INVALID_REQUEST", "The request could not be processed."));
    }
}
