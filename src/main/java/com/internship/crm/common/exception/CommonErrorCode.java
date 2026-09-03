package com.internship.crm.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Error codes shared by all HTTP API modules.
 */
public enum CommonErrorCode implements ErrorCode {

    VALIDATION_ERROR(
            "COMMON_VALIDATION_ERROR",
            "请求参数校验失败",
            HttpStatus.BAD_REQUEST),
    BAD_REQUEST(
            "COMMON_BAD_REQUEST",
            "请求无效",
            HttpStatus.BAD_REQUEST),
    NOT_FOUND(
            "COMMON_NOT_FOUND",
            "请求的资源不存在",
            HttpStatus.NOT_FOUND),
    CONFLICT(
            "COMMON_CONFLICT",
            "请求与当前资源状态冲突",
            HttpStatus.CONFLICT),
    METHOD_NOT_ALLOWED(
            "COMMON_METHOD_NOT_ALLOWED",
            "请求方法不支持",
            HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE(
            "COMMON_UNSUPPORTED_MEDIA_TYPE",
            "请求媒体类型不支持",
            HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    INTERNAL_ERROR(
            "COMMON_INTERNAL_ERROR",
            "服务器内部错误",
            HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;

    CommonErrorCode(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public HttpStatus status() {
        return status;
    }
}
