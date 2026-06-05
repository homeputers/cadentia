package com.cadentia.api.controller;

import com.cadentia.generated.model.WorkflowProblemCode;
import com.cadentia.generated.model.WorkflowProblemResponse;
import com.cadentia.rehearsal.RehearsalWorkflowException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = RehearsalWorkflowController.class)
public class RehearsalWorkflowExceptionHandler {

    @ExceptionHandler(RehearsalWorkflowException.class)
    public ResponseEntity<WorkflowProblemResponse> handleWorkflowException(RehearsalWorkflowException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new WorkflowProblemResponse(WorkflowProblemCode.TRANSITION_BLOCKED, ex.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<WorkflowProblemResponse> handleResponseStatus(ResponseStatusException ex) {
        WorkflowProblemCode code = ex.getStatusCode() == HttpStatus.CONFLICT
                ? WorkflowProblemCode.VERSION_CONFLICT
                : WorkflowProblemCode.INVALID_STATUS_CODE;
        return ResponseEntity.status(ex.getStatusCode())
                .body(new WorkflowProblemResponse(code, ex.getReason() == null ? code.getValue() : ex.getReason()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class})
    public ResponseEntity<WorkflowProblemResponse> handleValidation(Exception ex) {
        return ResponseEntity.badRequest()
                .body(new WorkflowProblemResponse(WorkflowProblemCode.INVALID_STATUS_CODE, "Invalid rehearsal workflow payload."));
    }
}
