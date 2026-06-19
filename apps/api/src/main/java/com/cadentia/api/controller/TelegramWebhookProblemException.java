package com.cadentia.api.controller;

import com.cadentia.generated.model.TelegramProblemResponse;
import org.springframework.http.HttpStatus;

public class TelegramWebhookProblemException extends RuntimeException {

    private final HttpStatus status;
    private final TelegramProblemResponse problem;

    public TelegramWebhookProblemException(HttpStatus status, TelegramProblemResponse problem) {
        super(problem.getDetail());
        this.status = status;
        this.problem = problem;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public TelegramProblemResponse getProblem() {
        return problem;
    }
}
