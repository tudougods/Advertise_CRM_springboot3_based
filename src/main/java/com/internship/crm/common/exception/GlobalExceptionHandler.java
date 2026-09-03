package com.internship.crm.common.exception;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
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
        logBusinessException(errorCode, exception);
        return response(errorCode, exception.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<List<FieldValidationError>>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception) {
        return validationResponse(toValidationErrors(exception.getBindingResult().getAllErrors()));
    }

    @ExceptionHandler(BindException.class)
    ResponseEntity<ApiResponse<List<FieldValidationError>>> handleBindException(BindException exception) {
        return validationResponse(toValidationErrors(exception.getBindingResult().getAllErrors()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiResponse<List<FieldValidationError>>> handleConstraintViolation(
            ConstraintViolationException exception) {
        List<FieldValidationError> errors = exception.getConstraintViolations().stream()
                .map(violation -> new FieldValidationError(
                        leafPropertyName(violation.getPropertyPath().toString()),
                        violation.getMessage()))
                .sorted(fieldErrorComparator())
                .toList();
        return validationResponse(errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiResponse<List<FieldValidationError>>> handleHandlerMethodValidation(
            HandlerMethodValidationException exception) {
        List<FieldValidationError> errors = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldValidationError(
                                parameterName(result.getMethodParameter().getParameterName(),
                                        result.getMethodParameter().getParameterIndex()),
                                error.getDefaultMessage() == null
                                        ? "参数值无效"
                                        : error.getDefaultMessage())))
                .sorted(fieldErrorComparator())
                .toList();
        return validationResponse(errors);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiResponse<List<FieldValidationError>>> handleMissingRequestParameter(
            MissingServletRequestParameterException exception) {
        return validationResponse(List.of(new FieldValidationError(
                exception.getParameterName(),
                "缺少必填参数")));
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    ResponseEntity<ApiResponse<Void>> handleRequestBinding(ServletRequestBindingException exception) {
        return response(CommonErrorCode.BAD_REQUEST, null);
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

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(CommonErrorCode.METHOD_NOT_ALLOWED.status());
        if (exception.getSupportedHttpMethods() != null) {
            response.allow(exception.getSupportedHttpMethods().toArray(org.springframework.http.HttpMethod[]::new));
        }
        return response.body(ApiResponse.failure(
                CommonErrorCode.METHOD_NOT_ALLOWED.code(),
                CommonErrorCode.METHOD_NOT_ALLOWED.message(),
                null));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException exception) {
        return response(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE, null);
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

    private List<FieldValidationError> toValidationErrors(List<ObjectError> errors) {
        return errors.stream()
                .map(error -> new FieldValidationError(
                        error instanceof FieldError fieldError
                                ? fieldError.getField()
                                : error.getObjectName(),
                        error.getDefaultMessage() == null ? "字段值无效" : error.getDefaultMessage()))
                .sorted(fieldErrorComparator())
                .toList();
    }

    private String parameterName(String discoveredName, int parameterIndex) {
        return discoveredName == null ? "arg" + parameterIndex : discoveredName;
    }

    private String leafPropertyName(String propertyPath) {
        int separator = propertyPath.lastIndexOf('.');
        return separator < 0 ? propertyPath : propertyPath.substring(separator + 1);
    }

    private Comparator<FieldValidationError> fieldErrorComparator() {
        return Comparator.comparing(
                        (FieldValidationError error) -> Objects.requireNonNull(error).field())
                .thenComparing(error -> Objects.requireNonNull(error).message());
    }

    private void logBusinessException(ErrorCode errorCode, BusinessException exception) {
        HttpStatus status = errorCode.status();
        if (status.is5xxServerError()) {
            log.error("Business operation failed: code={} status={}",
                    errorCode.code(), status.value(), exception);
            return;
        }
        if (status == HttpStatus.UNAUTHORIZED
                || status == HttpStatus.FORBIDDEN
                || status == HttpStatus.CONFLICT
                || status == HttpStatus.TOO_MANY_REQUESTS) {
            log.warn("Business request rejected: code={} status={}",
                    errorCode.code(), status.value());
            return;
        }
        log.debug("Expected business request rejection: code={} status={}",
                errorCode.code(), status.value());
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
