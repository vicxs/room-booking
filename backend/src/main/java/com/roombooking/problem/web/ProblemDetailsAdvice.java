package com.roombooking.problem.web;

import com.roombooking.contract.model.FieldError;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every API error as Problem Details (ADR-0003). Spring MVC's own exceptions, such as an unreadable body or a path
 * variable of the wrong type, keep Spring's {@code about:blank} Problem. Bean Validation failures add an
 * {@code errors} extension with one item per offending field. Anything unexpected becomes a 500 Problem that reveals
 * no internal details. Features map their own exceptions in their web layer.
 */
@RestControllerAdvice
class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        ProblemDetail problem = exception.getBody();
        Problems.addErrors(problem, errors);
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    @ExceptionHandler(Exception.class)
    @Nullable
    ResponseEntity<Object> handleUnexpected(Exception exception, WebRequest request) {
        logger.error("Unexpected error while handling " + request.getDescription(false), exception);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "An unexpected error occurred.");
        return handleExceptionInternal(exception, problem, new HttpHeaders(), status, request);
    }

    /**
     * Spring picks {@code application/problem+json} only when the client does not accept {@code application/json}.
     * Setting it here makes every error use it.
     */
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        HttpHeaders problemHeaders = new HttpHeaders();
        problemHeaders.putAll(headers);
        problemHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return super.createResponseEntity(body, problemHeaders, statusCode, request);
    }
}
