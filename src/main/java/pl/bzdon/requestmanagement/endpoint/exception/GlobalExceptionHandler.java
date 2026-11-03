package pl.bzdon.requestmanagement.endpoint.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import pl.bzdon.requestmanagement.domain.exception.EditNotAllowedException;
import pl.bzdon.requestmanagement.domain.exception.FieldTooLongException;
import pl.bzdon.requestmanagement.domain.exception.InvalidStateTransitionException;
import pl.bzdon.requestmanagement.domain.exception.MissingRequiredFieldException;
import pl.bzdon.requestmanagement.domain.exception.RequestNotFoundException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Maps every error to a problem details response and logs it at ERROR in one format.
 * Framework errors (validation, malformed JSON, bad parameter types) go through {@link #handleExceptionInternal},
 * which the base class calls for all of them; anything unexpected becomes a 500.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode status,
                                                                  @NonNull WebRequest request) {
        List<ValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> new ValidationError(error.getField(), error.getDefaultMessage()))
            .toList();
        return handleExceptionInternal(ex, withErrors(ex.getBody(), errors), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            @NonNull HttpHeaders headers,
                                                                            @NonNull HttpStatusCode status,
                                                                            @NonNull WebRequest request) {
        List<ValidationError> errors = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            String parameter = result.getMethodParameter().getParameterName();
            result.getResolvableErrors()
                .forEach(error -> errors.add(new ValidationError(parameter, error.getDefaultMessage())));
        }
        return handleExceptionInternal(ex, withErrors(ex.getBody(), errors), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(@NonNull Exception ex,
                                                             @Nullable Object body,
                                                             @NonNull HttpHeaders headers,
                                                             @NonNull HttpStatusCode statusCode,
                                                             @NonNull WebRequest request) {
        log(describe(request), statusCode, describe(ex, body), ex);
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    @ExceptionHandler(RequestNotFoundException.class)
    ProblemDetail handleNotFound(RequestNotFoundException ex, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.NOT_FOUND, "Request not found", ex, request);
        problem.setProperty("requestId", ex.getId());
        return problem;
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    ProblemDetail handleInvalidTransition(InvalidStateTransitionException ex, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Invalid state transition", ex, request);
        problem.setProperty("currentState", ex.getFrom());
        problem.setProperty("action", ex.getAction());
        return problem;
    }

    @ExceptionHandler(EditNotAllowedException.class)
    ProblemDetail handleEditNotAllowed(EditNotAllowedException ex, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Edit not allowed", ex, request);
        problem.setProperty("currentState", ex.getState());
        return problem;
    }

    @ExceptionHandler(MissingRequiredFieldException.class)
    ProblemDetail handleMissingField(MissingRequiredFieldException ex, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Missing required field", ex, request);
        problem.setProperty("field", ex.getField());
        return problem;
    }

    @ExceptionHandler(FieldTooLongException.class)
    ProblemDetail handleFieldTooLong(FieldTooLongException ex, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Field too long", ex, request);
        problem.setProperty("field", ex.getField());
        problem.setProperty("maxLength", ex.getMaxLength());
        return problem;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleConcurrentModification(OptimisticLockingFailureException ex, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Concurrent modification",
            "The request was modified concurrently, please retry", ex, request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", "An unexpected error occurred", ex, request);
    }

    private static ProblemDetail withErrors(ProblemDetail problem, List<ValidationError> errors) {
        problem.setDetail("Validation failed");
        problem.setProperty("errors", errors.stream()
            .sorted(Comparator.comparing(ValidationError::field))
            .toList());
        return problem;
    }

    private static ProblemDetail problem(HttpStatus status, String title, Exception ex, HttpServletRequest request) {
        return problem(status, title, ex.getMessage(), ex, request);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, Exception ex,
                                         HttpServletRequest request) {
        log(describe(request), status, ex.getMessage(), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }

    /**
     * Client errors are logged with their reason only; server errors also with the stack trace, needed to find the cause.
     */
    private static void log(String request, HttpStatusCode status, String reason, Exception ex) {
        if (status.is5xxServerError()) {
            log.error("Failed {} with {}: {}", request, status, reason, ex);
        } else {
            log.error("Rejected {} with {}: {}", request, status, reason);
        }
    }

    private static String describe(HttpServletRequest request) {
        return request.getMethod() + " " + request.getRequestURI();
    }

    private static String describe(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest
            ? describe(servletRequest.getRequest())
            : request.getDescription(false);
    }

    private static String describe(Exception ex, @Nullable Object body) {
        if (body instanceof ProblemDetail problem) {
            Object errors = problem.getProperties() == null ? null : problem.getProperties().get("errors");
            return errors == null ? problem.getDetail() : problem.getDetail() + " " + errors;
        }
        return ex.getMessage();
    }
}
