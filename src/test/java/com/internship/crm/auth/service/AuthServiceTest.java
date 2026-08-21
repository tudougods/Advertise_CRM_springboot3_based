package com.internship.crm.auth.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.internship.crm.auth.api.AuthResponse;
import com.internship.crm.auth.api.LoginRequest;
import com.internship.crm.auth.api.RegisterRequest;
import com.internship.crm.auth.error.AuthErrorCode;
import com.internship.crm.auth.token.JwtTokenService;
import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import com.internship.crm.user.api.UserResponse;
import com.internship.crm.user.domain.User;
import com.internship.crm.user.domain.UserRole;
import com.internship.crm.user.domain.UserStatus;
import com.internship.crm.user.service.UserService;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@DisplayName("注册与登录业务规则")
@ExtendWith({MockitoExtension.class, ReadableTestResultExtension.class})
class AuthServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userService, passwordEncoder, jwtTokenService);
    }

    @Test
    @DisplayName("注册请求创建普通用户并返回不含密码的用户信息")
    void registerCreatesAnOperatorAndReturnsAPublicResponse() {
        RegisterRequest request = new RegisterRequest(
                "new.user",
                "SecurePassword123!",
                "新用户",
                "new.user@example.com");
        User registered = user(1L, UserStatus.ACTIVE);
        when(userService.registerOperator(
                request.username(), request.password(), request.displayName(), request.email()))
                .thenReturn(registered);

        UserResponse response = authService.register(request);

        assertAll(
                () -> assertEquals(1L, response.id()),
                () -> assertEquals(UserRole.OPERATOR, response.role()),
                () -> assertFalse(response.toString().contains("password-hash")));
    }

    @Test
    @DisplayName("启用账号使用正确密码登录后更新登录时间并返回 JWT")
    void activeUserWithCorrectPasswordReceivesAJwt() {
        User user = user(2L, UserStatus.ACTIVE);
        when(userService.findByUsername("operator")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct-password", "password-hash")).thenReturn(true);
        when(jwtTokenService.issueToken(user)).thenReturn("signed.jwt.token");
        when(jwtTokenService.expirationSeconds()).thenReturn(3600L);

        AuthResponse response = authService.login(new LoginRequest("  operator  ", "correct-password"));

        assertAll(
                () -> assertEquals("signed.jwt.token", response.accessToken()),
                () -> assertEquals("Bearer", response.tokenType()),
                () -> assertEquals(3600L, response.expiresIn()),
                () -> assertEquals(2L, response.user().id()));
        verify(userService).recordSuccessfulLogin(user);
    }

    @Test
    @DisplayName("错误密码统一返回认证失败且不签发 Token")
    void wrongPasswordReturnsGenericInvalidCredentials() {
        User user = user(3L, UserStatus.ACTIVE);
        when(userService.findByUsername("operator")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "password-hash")).thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.login(new LoginRequest("operator", "wrong-password")));

        assertSame(AuthErrorCode.INVALID_CREDENTIALS, exception.errorCode());
        verify(jwtTokenService, never()).issueToken(user);
        verify(userService, never()).recordSuccessfulLogin(user);
    }

    @Test
    @DisplayName("不存在的账号返回与错误密码相同的认证失败")
    void unknownUsernameDoesNotRevealWhetherTheAccountExists() {
        when(userService.findByUsername("missing")).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.login(new LoginRequest("missing", "any-password")));

        assertAll(
                () -> assertSame(AuthErrorCode.INVALID_CREDENTIALS, exception.errorCode()),
                () -> assertEquals("用户名或密码不正确", exception.getMessage()));
        verify(passwordEncoder, never()).matches("any-password", "password-hash");
    }

    @Test
    @DisplayName("禁用账号不能登录且不会校验密码或签发 Token")
    void disabledAccountCannotLogin() {
        User disabled = user(4L, UserStatus.DISABLED);
        when(userService.findByUsername("operator")).thenReturn(Optional.of(disabled));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.login(new LoginRequest("operator", "correct-password")));

        assertSame(AuthErrorCode.INVALID_CREDENTIALS, exception.errorCode());
        verify(passwordEncoder, never()).matches("correct-password", "password-hash");
        verify(jwtTokenService, never()).issueToken(disabled);
    }

    private User user(Long id, UserStatus status) {
        OffsetDateTime now = OffsetDateTime.now();
        User user = new User();
        user.setId(id);
        user.setUsername("operator");
        user.setPasswordHash("password-hash");
        user.setDisplayName("运营用户");
        user.setEmail("operator@example.com");
        user.setRole(UserRole.OPERATOR);
        user.setStatus(status);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return user;
    }
}
