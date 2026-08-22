package com.internship.crm.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "用户登录请求")
public record LoginRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(max = 50, message = "用户名不能超过 50 个字符")
        @Schema(example = "operator01")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(max = 72, message = "密码不能超过 72 个字符")
        @Schema(example = "SecurePassword123!")
        String password) {
}
