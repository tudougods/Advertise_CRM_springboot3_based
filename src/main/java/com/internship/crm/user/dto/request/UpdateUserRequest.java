package com.internship.crm.user.dto.request;

import com.internship.crm.user.entity.UserRole;
import com.internship.crm.user.entity.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "管理员局部修改用户请求；未提供的字段保持不变")
public record UpdateUserRequest(
        @Size(min = 1, max = 100, message = "显示名称长度必须为 1 到 100 个字符")
        @Pattern(regexp = ".*\\S.*", message = "显示名称不能为空白")
        String displayName,

        @Email(message = "邮箱格式不正确")
        @Size(max = 254, message = "邮箱不能超过 254 个字符")
        @Pattern(regexp = ".*\\S.*", message = "邮箱不能为空白")
        String email,

        @Size(min = 8, max = 72, message = "密码长度必须为 8 到 72 个字符")
        @Pattern(regexp = ".*\\S.*", message = "密码不能为空白")
        @Schema(description = "可选的新密码；响应中不会返回")
        String password,

        UserRole role,

        UserStatus status) {
}
