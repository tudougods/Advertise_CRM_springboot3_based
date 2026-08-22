package com.internship.crm.auth.dto.response;

import com.internship.crm.user.dto.response.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "登录成功响应")
public record AuthResponse(
        @Schema(description = "JWT 访问令牌")
        String accessToken,
        @Schema(example = "Bearer")
        String tokenType,
        @Schema(description = "令牌剩余有效时间，单位为秒", example = "3600")
        long expiresIn,
        UserResponse user) {
}
