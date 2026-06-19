package com.cadentia.api.controller;

import com.cadentia.generated.model.TelegramProblemResponse;
import com.cadentia.generated.model.TelegramValidationError;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class TelegramWebhookProblemFactory {

    public TelegramProblemResponse problem(HttpStatus status, String type, String detail, String correlationId) {
        return new TelegramProblemResponse()
                .type(URI.create("https://cadentia.local/problems/telegram/" + type))
                .title(status.getReasonPhrase())
                .status(status.value())
                .detail(detail)
                .correlationId(correlationId);
    }

    public TelegramProblemResponse problem(
            HttpStatus status,
            String type,
            String detail,
            String correlationId,
            List<TelegramValidationError> errors) {
        TelegramProblemResponse response = problem(status, type, detail, correlationId);
        errors.forEach(response::addErrorsItem);
        return response;
    }
}
