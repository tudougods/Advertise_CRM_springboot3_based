package com.internship.crm.common.exception;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolationException;

import com.internship.crm.common.response.ApiResponse;

/**
 * Converts application and Spring MVC exceptions into the common API envelope.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String INVALID_TYPE_MESSAGE = "参数类型不正确";

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ApiResponse<Void>> handleRateLimitExceeded(RateLimitExceededException exception) {
        ErrorCode errorCode = exception.errorCode();
        log.warn("Rate limit rejected a request: code={} retryAfterSeconds={}",
                errorCode.code(), exception.retryAfterSeconds());
        return ResponseEntity
                .status(errorCode.status())
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.retryAfterSeconds()))
                .body(ApiResponse.failure(errorCode.code(), exception.getMessage(), null));
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.errorCode();
        log.warn("Business request rejected: code={}", errorCode.code());
        return response(errorCode, exception.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<List<FieldValidationError>>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception) {
        return validationResponse(toFieldErrors(exception.getBindingResult().getFieldErrors()));
    }

    @ExceptionHandler(BindException.class)
    ResponseEntity<ApiResponse<List<FieldValidationError>>> handleBindException(BindException exception) {
        return validationResponse(toFieldErrors(exception.getBindingResult().getFieldErrors()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiResponse<List<FieldValidationError>>> handleConstraintViolation(
            ConstraintViolationException exception) {
        List<FieldValidationError> errors = exception.getConstraintViolations().stream()
                .map(violation -> new FieldValidationError(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()))
                .sorted(fieldErrorComparator())
                .toList();
        return validationResponse(errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiResponse<Void>> handleHandlerMethodValidation(
            HandlerMethodValidationException exception) {
        return response(CommonErrorCode.VALIDATION_ERROR, null);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, ConversionFailedException.class})
    ResponseEntity<ApiResponse<List<FieldValidationError>>> handleTypeMismatch(Exception exception) {
        String field = exception instanceof MethodArgumentTypeMismatchException mismatch
                ? mismatch.getName()
                : "parameter";
        return validationResponse(List.of(new FieldValidationError(field, INVALID_TYPE_MESSAGE)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Void>> handleUnreadableMessage(HttpMessageNotReadableException exception) {
        return response(CommonErrorCode.BAD_REQUEST, null);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    ResponseEntity<ApiResponse<Void>> handleNotFound(Exception exception) {
        return response(CommonErrorCode.NOT_FOUND, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        log.warn("Database constraint rejected a request: type={}",
                exception.getMostSpecificCause().getClass().getSimpleName());
        return response(CommonErrorCode.CONFLICT, null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        log.error("Unhandled application exception", exception);
        return response(CommonErrorCode.INTERNAL_ERROR, null);
    }

    private ResponseEntity<ApiResponse<List<FieldValidationError>>> validationResponse(
            List<FieldValidationError> errors) {
        return response(CommonErrorCode.VALIDATION_ERROR, errors);
    }

    private List<FieldValidationError> toFieldErrors(List<FieldError> fieldErrors) {
        return fieldErrors.stream()
                .map(error -> new FieldValidationError(
                        error.getField(),
                        error.getDefaultMessage() == null ? "字段值无效" : error.getDefaultMessage()))
                .sorted(fieldErrorComparator())
                .toList();
    }

    private Comparator<FieldValidationError> fieldErrorComparator() {
        return Comparator.comparing(
                        (FieldValidationError error) -> Objects.requireNonNull(error).field())
                .thenComparing(error -> Objects.requireNonNull(error).message());
    }

    private <T> ResponseEntity<ApiResponse<T>> response(ErrorCode errorCode, T data) {
        return response(errorCode, errorCode.message(), data);
    }

    private <T> ResponseEntity<ApiResponse<T>> response(ErrorCode errorCode, String message, T data) {
        return ResponseEntity
                .status(Objects.requireNonNull(errorCode.status()))
                .body(ApiResponse.failure(errorCode.code(), message, data));
    }
}
