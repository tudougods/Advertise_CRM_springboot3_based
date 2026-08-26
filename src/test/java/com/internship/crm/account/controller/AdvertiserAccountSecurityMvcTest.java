package com.internship.crm.account.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.internship.crm.account.dto.response.AdvertiserAccountResponse;
import com.internship.crm.account.exception.AccountErrorCode;
import com.internship.crm.account.service.AdvertiserAccountService;
import com.internship.crm.auth.security.JwtAuthenticationFilter;
import com.internship.crm.auth.security.RestAccessDeniedHandler;
import com.internship.crm.auth.security.RestAuthenticationEntryPoint;
import com.internship.crm.auth.token.JwtTokenService;
import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.common.exception.GlobalExceptionHandler;
import com.internship.crm.common.filter.RequestLoggingFilter;
import com.internship.crm.config.SecurityConfig;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import com.internship.crm.user.entity.User;
import com.internship.crm.user.entity.UserRole;
import com.internship.crm.user.entity.UserStatus;
import com.internship.crm.user.service.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdvertiserAccountController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class,
        RequestLoggingFilter.class
})
@DisplayName("广告主账户查询接口与 RBAC 权限")
@ExtendWith(ReadableTestResultExtension.class)
class AdvertiserAccountSecurityMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdvertiserAccountService advertiserAccountService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("缺少 JWT 查询账户返回统一 401")
    void missingTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/advertisers/7/account"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("OPERATOR 可以查询广告主账户余额")
    void operatorCanQueryAccount() throws Exception {
        authorize("operator-account", user(2L, UserRole.OPERATOR));
        when(advertiserAccountService.findByAdvertiserId(7L)).thenReturn(accountResponse());

        mockMvc.perform(get("/api/v1/advertisers/7/account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer operator-account"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accountId").value(8))
                .andExpect(jsonPath("$.data.advertiserId").value(7))
                .andExpect(jsonPath("$.data.balance").value(123.40));
    }

    @Test
    @DisplayName("ADMIN 可以查询广告主账户余额")
    void adminCanQueryAccount() throws Exception {
        authorize("admin-account", user(1L, UserRole.ADMIN));
        when(advertiserAccountService.findByAdvertiserId(7L)).thenReturn(accountResponse());

        mockMvc.perform(get("/api/v1/advertisers/7/account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-account"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(123.40));
    }

    @Test
    @DisplayName("非正数广告主 ID 返回统一 400 且不调用 Service")
    void nonPositiveAdvertiserIdReturnsBadRequest() throws Exception {
        authorize("admin-invalid-account", user(1L, UserRole.ADMIN));

        mockMvc.perform(get("/api/v1/advertisers/0/account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-invalid-account"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_ERROR"));
        verifyNoInteractions(advertiserAccountService);
    }

    @Test
    @DisplayName("账户不存在时返回明确的 404 业务错误")
    void missingAccountReturnsNotFound() throws Exception {
        authorize("operator-missing-account", user(2L, UserRole.OPERATOR));
        when(advertiserAccountService.findByAdvertiserId(7L))
                .thenThrow(new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        mockMvc.perform(get("/api/v1/advertisers/7/account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer operator-missing-account"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("广告主账户不存在"));
    }

    private AdvertiserAccountResponse accountResponse() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-26T00:00:00Z");
        return new AdvertiserAccountResponse(
                8L, 7L, new BigDecimal("123.40"), now, now);
    }

    private void authorize(String token, User user) {
        Claims claims = Jwts.claims().subject(user.getId().toString()).build();
        when(jwtTokenService.parseClaims(token)).thenReturn(claims);
        when(userService.findEntityById(user.getId())).thenReturn(Optional.of(user));
    }

    private User user(Long id, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
