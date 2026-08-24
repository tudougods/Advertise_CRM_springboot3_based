package com.internship.crm.auth.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.internship.crm.auth.dto.response.AuthResponse;
import com.internship.crm.auth.service.AuthService;
import com.internship.crm.auth.token.JwtTokenService;
import com.internship.crm.auth.controller.AuthController;
import com.internship.crm.common.exception.GlobalExceptionHandler;
import com.internship.crm.common.exception.RateLimitExceededException;
import com.internship.crm.auth.exception.AuthErrorCode;
import com.internship.crm.common.filter.RequestLoggingFilter;
import com.internship.crm.config.SecurityConfig;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import com.internship.crm.user.dto.request.CreateUserRequest;
import com.internship.crm.user.dto.request.UpdateUserRequest;
import com.internship.crm.user.dto.response.UserResponse;
import com.internship.crm.user.entity.User;
import com.internship.crm.user.entity.UserRole;
import com.internship.crm.user.entity.UserStatus;
import com.internship.crm.user.service.UserService;
import com.internship.crm.user.controller.UserController;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {AuthController.class, UserController.class})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class,
        RequestLoggingFilter.class
})
@DisplayName("用户接口与 RBAC 权限")
@ExtendWith(ReadableTestResultExtension.class)
class UserSecurityMvcTest {

    private static final @NonNull MediaType JSON = Objects.requireNonNull(MediaType.APPLICATION_JSON);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @Test
    @DisplayName("注册接口允许匿名提交待审批申请且响应不包含密码字段")
    void registrationIsPublicAndDoesNotExposePasswords() throws Exception {
        UserResponse response = response(10L, UserRole.OPERATOR, UserStatus.PENDING);
        when(authService.register(any(), anyString())).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(JSON)
                        .content("""
                                {
                                  "username": "new.user",
                                  "password": "SecurePassword123!",
                                  "displayName": "新用户",
                                  "email": "new.user@example.com"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.role").value("OPERATOR"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("登录接口允许匿名访问并返回 Bearer JWT")
    void loginIsPublicAndReturnsABearerToken() throws Exception {
        when(authService.login(any(), anyString())).thenReturn(new AuthResponse(
                "signed.jwt.token",
                "Bearer",
                3600,
                response(11L, UserRole.OPERATOR, UserStatus.ACTIVE)));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(JSON)
                        .content("""
                                {
                                  "username": "operator",
                                  "password": "SecurePassword123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("signed.jwt.token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600));
    }

    @Test
    @DisplayName("认证请求超限返回统一 429 和 Retry-After")
    void rateLimitedAuthenticationReturnsTheCommonResponse() throws Exception {
        when(authService.login(any(), anyString()))
                .thenThrow(new RateLimitExceededException(AuthErrorCode.RATE_LIMITED, 45));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(JSON)
                        .content("""
                                {
                                  "username": "operator",
                                  "password": "SecurePassword123!"
                                }
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "45"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_RATE_LIMITED"))
                .andExpect(jsonPath("$.message").value("请求过于频繁，请稍后再试"));
    }

    @Test
    @DisplayName("缺少 JWT 访问用户管理接口返回统一 401")
    void missingTokenReturnsTheCommonUnauthorizedResponse() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("请先登录或提供有效的访问令牌"));
    }

    @Test
    @DisplayName("无效 JWT 访问用户管理接口返回统一 401")
    void invalidTokenReturnsTheCommonUnauthorizedResponse() throws Exception {
        when(jwtTokenService.parseClaims("invalid-token"))
                .thenThrow(new MalformedJwtException("invalid test token"));

        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("已禁用账号即使持有有效 JWT 也返回 401")
    void disabledAccountCannotUseAnExistingToken() throws Exception {
        authorize("disabled-token", user(20L, UserRole.ADMIN, UserStatus.DISABLED));

        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer disabled-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("待审批账号即使持有有效 JWT 也返回 401")
    void pendingAccountCannotUseAnExistingToken() throws Exception {
        authorize("pending-token", user(22L, UserRole.OPERATOR, UserStatus.PENDING));

        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer pending-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("OPERATOR 使用有效 JWT 访问用户管理接口返回统一 403")
    void operatorCannotAccessUserAdministration() throws Exception {
        authorize("operator-token", user(21L, UserRole.OPERATOR, UserStatus.ACTIVE));

        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer operator-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));

        verify(userService, never()).findAll();
    }

    @Test
    @DisplayName("ADMIN 可以查询用户列表")
    void adminCanListUsers() throws Exception {
        authorize("admin-list-token", user(1L, UserRole.ADMIN, UserStatus.ACTIVE));
        when(userService.findAll()).thenReturn(List.of(
                response(1L, UserRole.ADMIN, UserStatus.ACTIVE),
                response(2L, UserRole.OPERATOR, UserStatus.ACTIVE)));

        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-list-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].role").value("ADMIN"));
    }

    @Test
    @DisplayName("ADMIN 可以创建用户且请求密码不会出现在响应中")
    void adminCanCreateAUserWithoutExposingTheRequestPassword() throws Exception {
        authorize("admin-create-token", user(1L, UserRole.ADMIN, UserStatus.ACTIVE));
        when(userService.create(any(CreateUserRequest.class)))
                .thenReturn(response(30L, UserRole.OPERATOR, UserStatus.ACTIVE));

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-create-token")
                        .contentType(JSON)
                        .content("""
                                {
                                  "username": "created.user",
                                  "password": "SecurePassword123!",
                                  "displayName": "创建用户",
                                  "email": "created.user@example.com",
                                  "role": "OPERATOR"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(30))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("ADMIN 可以查询单个用户详情")
    void adminCanReadAUser() throws Exception {
        authorize("admin-read-token", user(1L, UserRole.ADMIN, UserStatus.ACTIVE));
        when(userService.findById(31L)).thenReturn(response(31L, UserRole.OPERATOR, UserStatus.ACTIVE));

        mockMvc.perform(get("/api/v1/users/31")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-read-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(31));
    }

    @Test
    @DisplayName("ADMIN 可以局部修改用户角色和状态")
    void adminCanUpdateAUser() throws Exception {
        authorize("admin-update-token", user(1L, UserRole.ADMIN, UserStatus.ACTIVE));
        when(userService.update(any(Long.class), any(UpdateUserRequest.class)))
                .thenReturn(response(32L, UserRole.ADMIN, UserStatus.DISABLED));

        mockMvc.perform(patch("/api/v1/users/32")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-update-token")
                        .contentType(JSON)
                        .content("""
                                {
                                  "role": "ADMIN",
                                  "status": "DISABLED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
    }

    @Test
    @DisplayName("ADMIN 可以物理删除用户")
    void adminCanDeleteAUser() throws Exception {
        authorize("admin-delete-token", user(1L, UserRole.ADMIN, UserStatus.ACTIVE));

        mockMvc.perform(delete("/api/v1/users/33")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-delete-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(userService).delete(33L);
    }

    @Test
    @DisplayName("非法注册参数由统一校验响应拒绝")
    void invalidRegistrationReturnsTheCommonValidationResponse() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    private void authorize(String token, User user) {
        Claims claims = Jwts.claims().subject(user.getId().toString()).build();
        when(jwtTokenService.parseClaims(token)).thenReturn(claims);
        when(userService.findEntityById(user.getId())).thenReturn(Optional.of(user));
    }

    private User user(Long id, UserRole role, UserStatus status) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        user.setRole(role);
        user.setStatus(status);
        return user;
    }

    private UserResponse response(Long id, UserRole role, UserStatus status) {
        OffsetDateTime now = OffsetDateTime.now();
        return new UserResponse(
                id,
                "user" + id,
                "用户 " + id,
                "user" + id + "@example.com",
                role,
                status,
                null,
                now,
                now);
    }
}
