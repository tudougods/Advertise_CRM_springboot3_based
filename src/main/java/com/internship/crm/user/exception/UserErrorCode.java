package com.internship.crm.user.exception;

import com.internship.crm.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum UserErrorCode implements ErrorCode {
    USER_NOT_FOUND("USER_NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND),
    USERNAME_ALREADY_EXISTS("USER_USERNAME_ALREADY_EXISTS", "用户名已存在", HttpStatus.CONFLICT),
    EMAIL_ALREADY_EXISTS("USER_EMAIL_ALREADY_EXISTS", "邮箱已被使用", HttpStatus.CONFLICT),
    LAST_ACTIVE_ADMIN_REQUIRED(
            "USER_LAST_ACTIVE_ADMIN_REQUIRED",
            "系统必须至少保留一个启用的管理员",
            HttpStatus.CONFLICT),
    NO_FIELDS_TO_UPDATE("USER_NO_FIELDS_TO_UPDATE", "至少需要提供一个待修改字段", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus status;

    UserErrorCode(String code, String message, HttpStatus status) {
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
