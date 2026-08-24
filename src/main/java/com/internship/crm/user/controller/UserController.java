package com.internship.crm.user.controller;

import com.internship.crm.common.response.ApiResponse;
import com.internship.crm.common.response.PageResponse;
import com.internship.crm.user.dto.request.CreateUserRequest;
import com.internship.crm.user.dto.request.UpdateUserRequest;
import com.internship.crm.user.dto.response.UserResponse;
import com.internship.crm.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "用户管理", description = "管理员维护 CRM 用户")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @Operation(summary = "创建用户")
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(userService.create(request)));
    }

    @GetMapping
    @Operation(summary = "分页查询用户列表")
    public ApiResponse<PageResponse<UserResponse>> findAll(
            @Positive @RequestParam(defaultValue = "1") int page,
            @Positive @Max(100) @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(userService.findAll(page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询用户详情")
    public ApiResponse<UserResponse> findById(@Positive @PathVariable Long id) {
        return ApiResponse.success(userService.findById(id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "局部修改用户")
    public ApiResponse<UserResponse> update(
            @Positive @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.success(userService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户")
    public ApiResponse<Void> delete(@Positive @PathVariable Long id) {
        userService.delete(id);
        return ApiResponse.successWithoutData();
    }
}
