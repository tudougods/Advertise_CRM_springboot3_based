package com.internship.crm.auth.web;

import com.internship.crm.auth.api.AuthResponse;
import com.internship.crm.auth.api.LoginRequest;
import com.internship.crm.auth.api.RegisterRequest;
import com.internship.crm.auth.service.AuthService;
import com.internship.crm.common.api.ApiResponse;
import com.internship.crm.user.api.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "认证", description = "用户注册与登录")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "注册普通用户", description = "公开注册始终创建 OPERATOR 角色")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(authService.register(request)));
    }

    @PostMapping("/login")
    @Operation(summary = "登录并获取 JWT")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }
}
