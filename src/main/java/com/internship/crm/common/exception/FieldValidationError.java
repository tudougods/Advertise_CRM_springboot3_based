package com.internship.crm.common.exception;

import java.util.Objects;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Client-safe description of one invalid request field or parameter.
 */
@Schema(description = "字段或请求参数校验错误")
public record FieldValidationError(
        @Schema(description = "错误字段或参数名称", example = "username")
        String field,
        @Schema(description = "可安全展示给客户端的错误信息", example = "用户名不能为空")
        String message) {

    public FieldValidationError {
        Objects.requireNonNull(field, "field must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }
}
