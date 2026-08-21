package com.internship.crm.common.api;

import java.time.Instant;
import java.util.Objects;

import io.swagger.v3.oas.annotations.media.Schema;

import com.internship.crm.common.error.ErrorCode;
import com.internship.crm.common.web.RequestIdContext;

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
@Schema(description = "统一 API 响应结构")
public record ApiResponse<T>(
        @Schema(description = "请求是否成功", example = "true")
        boolean success,
        @Schema(description = "稳定的机器可读结果码", example = "OK")
        String code,
        @Schema(description = "面向客户端的结果说明", example = "请求成功")
        String message,
        @Schema(description = "业务数据或安全的错误详情")
        T data,
        @Schema(description = "UTC 响应时间", example = "2026-08-21T08:00:00Z")
        Instant timestamp,
        @Schema(description = "请求追踪标识", example = "550e8400-e29b-41d4-a716-446655440000")
        String requestId) {

    public static final String SUCCESS_CODE = "OK";
    public static final String SUCCESS_MESSAGE = "请求成功";

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
        return RequestIdContext.current();
    }
}
