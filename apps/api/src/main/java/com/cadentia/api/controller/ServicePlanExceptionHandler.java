package com.cadentia.api.controller;

import com.cadentia.serviceplan.ServicePlanPublishConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ServicePlanExceptionHandler {

    @ExceptionHandler(ServicePlanPublishConflictException.class)
    public ResponseEntity<String> handlePublishConflict(ServicePlanPublishConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(exception.getMessage());
    }
}
