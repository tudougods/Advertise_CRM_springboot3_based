package com.internship.crm.common.api;

import java.time.Instant;
import java.util.Objects;

import org.slf4j.MDC;

import com.internship.crm.common.error.ErrorCode;

/**
 * Stable envelope used by CRM business APIs.
 *
 * @param success whether the request completed successfully
 * @param code stable machine-readable result code
 * @param message human-readable result message
 * @param data response payload or error details
 * @param timestamp response creation time in UTC
 * @param requestId request correlation identifier when available
 * @param <T> payload type
 */
public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        Instant timestamp,
        String requestId) {

    public static final String SUCCESS_CODE = "OK";
    public static final String SUCCESS_MESSAGE = "请求成功";

    private static final String REQUEST_ID_MDC_KEY = "requestId";

    public ApiResponse {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
                true,
                SUCCESS_CODE,
                SUCCESS_MESSAGE,
                data,
                Instant.now(),
                currentRequestId());
    }

    public static ApiResponse<Void> successWithoutData() {
        return success(null);
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode, T data) {
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        return failure(errorCode.code(), errorCode.message(), data);
    }

    public static ApiResponse<Void> failure(ErrorCode errorCode) {
        return failure(errorCode, null);
    }

    public static <T> ApiResponse<T> failure(String code, String message, T data) {
        return new ApiResponse<>(
                false,
                code,
                message,
                data,
                Instant.now(),
                currentRequestId());
    }

    private static String currentRequestId() {
        return MDC.get(REQUEST_ID_MDC_KEY);
    }
}
