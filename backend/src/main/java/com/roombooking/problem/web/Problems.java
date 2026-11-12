package com.roombooking.problem.web;

import com.roombooking.contract.model.FieldError;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * Problem Details (ADR-0003) that features answer from their own web layer, in the same shape
 * {@link ProblemDetailsAdvice} gives every other error.
 */
public final class Problems {

    private static final Comparator<FieldError> BY_FIELD_THEN_MESSAGE =
            Comparator.comparing(FieldError::getField).thenComparing(FieldError::getMessage);

    private Problems() {}

    /** A 400 {@code about:blank} Problem listing each offending field, the same shape as a Bean Validation failure. */
    public static ResponseEntity<ProblemDetail> invalid(List<FieldError> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request content.");
        addErrors(problem, errors);
        return respond(problem);
    }

    /** Answers the Problem as {@code application/problem+json}, even when the client accepts plain JSON. */
    public static ResponseEntity<ProblemDetail> respond(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    /** Adds the {@code errors} extension, sorted by field and then by message so its order is stable. */
    static void addErrors(ProblemDetail problem, List<FieldError> errors) {
        problem.setProperty(
                "errors", errors.stream().sorted(BY_FIELD_THEN_MESSAGE).toList());
    }
}
