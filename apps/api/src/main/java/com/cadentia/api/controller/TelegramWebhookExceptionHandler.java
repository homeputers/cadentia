package com.cadentia.api.controller;

import com.cadentia.generated.model.TelegramProblemResponse;
import com.cadentia.generated.model.TelegramValidationError;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice(assignableTypes = TelegramWebhookController.class)
public class TelegramWebhookExceptionHandler {

    private final TelegramWebhookProblemFactory problemFactory;

    public TelegramWebhookExceptionHandler() {
        this(new TelegramWebhookProblemFactory());
    }

    public TelegramWebhookExceptionHandler(TelegramWebhookProblemFactory problemFactory) {
        this.problemFactory = problemFactory;
    }

    @ExceptionHandler(TelegramWebhookProblemException.class)
    public ResponseEntity<TelegramProblemResponse> handle(TelegramWebhookProblemException exception) {
        return ResponseEntity.status(exception.getStatus()).body(exception.getProblem());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<TelegramProblemResponse> handleValidation(MethodArgumentNotValidException exception, WebRequest request) {
        String correlationId = correlationId(request);
        List<TelegramValidationError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new TelegramValidationError()
                        .field("/" + fieldError.getField().replace('.', '/'))
                        .code("REQUIRED_INT64")
                        .message("Required Telegram update field is missing or invalid."))
                .toList();
        TelegramProblemResponse problem = problemFactory.problem(
                HttpStatus.BAD_REQUEST,
                "invalid-telegram-update",
                "Telegram update failed validation.",
                correlationId,
                errors);
        return ResponseEntity.badRequest().body(problem);
    }

    private String correlationId(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            String correlationId = servletWebRequest.getRequest().getHeader("X-Correlation-ID");
            if (org.springframework.util.StringUtils.hasText(correlationId)) {
                return correlationId;
            }
            String requestId = servletWebRequest.getRequest().getHeader("X-Request-ID");
            if (org.springframework.util.StringUtils.hasText(requestId)) {
                return requestId;
            }
        }
        return java.util.UUID.randomUUID().toString();
    }
}
