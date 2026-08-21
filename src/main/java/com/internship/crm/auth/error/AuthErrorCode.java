package com.internship.crm.auth.error;

import com.internship.crm.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AuthErrorCode implements ErrorCode {
    INVALID_CREDENTIALS("AUTH_INVALID_CREDENTIALS", "用户名或密码不正确", HttpStatus.UNAUTHORIZED),
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
