package com.internship.crm.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "内部员工账号注册申请；提交后等待管理员激活")
public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(max = 50, message = "用户名不能超过 50 个字符")
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "用户名只能包含字母、数字、点、下划线和连字符")
        @Schema(example = "operator01")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 72, message = "密码长度必须为 8 到 72 个字符")
        @Schema(example = "SecurePassword123!")
        String password,

        @NotBlank(message = "显示名称不能为空")
        @Size(max = 100, message = "显示名称不能超过 100 个字符")
        @Schema(example = "运营人员一号")
        String displayName,

        @Email(message = "邮箱格式不正确")
        @Size(max = 254, message = "邮箱不能超过 254 个字符")
        @Schema(example = "operator01@example.com")
        String email) {
}
