package com.example.banking.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BankingException.class)
    ProblemDetail banking(BankingException e) { return problem(e.getStatus(), e.getMessage()); }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Request validation failed");
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("field", f.getField(), "message", String.valueOf(f.getDefaultMessage()))).toList();
        detail.setProperty("errors", errors);
        return handleExceptionInternal(e, detail, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(e, problem(HttpStatus.BAD_REQUEST, "Malformed JSON or invalid request fields"),
                headers, status, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail validation(ConstraintViolationException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request parameters");
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    ProblemDetail concurrency(ConcurrencyFailureException e) {
        return problem(HttpStatus.CONFLICT, "Account is busy; retry the request using the same transfer key");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException e) {
        LOG.error("Database integrity failure", e);
        return problem(HttpStatus.CONFLICT, "Operation conflicts with database constraints");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        LOG.error("Unexpected API failure", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
