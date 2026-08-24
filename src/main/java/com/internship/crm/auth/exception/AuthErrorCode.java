package com.internship.crm.auth.exception;

import com.internship.crm.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AuthErrorCode implements ErrorCode {
    INVALID_CREDENTIALS("AUTH_INVALID_CREDENTIALS", "用户名或密码不正确", HttpStatus.UNAUTHORIZED),
    RATE_LIMITED("AUTH_RATE_LIMITED", "请求过于频繁，请稍后再试", HttpStatus.TOO_MANY_REQUESTS),
    UNAUTHORIZED("AUTH_UNAUTHORIZED", "请先登录或提供有效的访问令牌", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("AUTH_ACCESS_DENIED", "当前账号没有执行此操作的权限", HttpStatus.FORBIDDEN);

    private final String code;
    private final String message;
    private final HttpStatus status;

    AuthErrorCode(String code, String message, HttpStatus status) {
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
