package com.internship.crm.account.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.internship.crm.account.dto.response.AdvertiserAccountTransactionResponse;
import com.internship.crm.account.entity.AccountTransactionType;
import com.internship.crm.account.exception.AccountErrorCode;
import com.internship.crm.account.service.AdvertiserAccountTransactionService;
import com.internship.crm.auth.security.JwtAuthenticationFilter;
import com.internship.crm.auth.security.RestAccessDeniedHandler;
import com.internship.crm.auth.security.RestAuthenticationEntryPoint;
import com.internship.crm.auth.token.JwtTokenService;
import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.common.exception.GlobalExceptionHandler;
import com.internship.crm.common.filter.RequestLoggingFilter;
import com.internship.crm.common.response.PageResponse;
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
import java.util.List;
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

@WebMvcTest(controllers = AdvertiserAccountTransactionController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class,
        RequestLoggingFilter.class
})
@DisplayName("广告主账户流水分页接口与 RBAC 权限")
@ExtendWith(ReadableTestResultExtension.class)
class AdvertiserAccountTransactionSecurityMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdvertiserAccountTransactionService transactionService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("缺少 JWT 查询流水返回统一 401")
    void missingTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/advertisers/7/account/transactions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("OPERATOR 可以按类型和时间范围分页查询流水")
    void operatorCanQueryTransactions() throws Exception {
        authorize("operator-transactions", user(2L, UserRole.OPERATOR));
        when(transactionService.findAll(any(), any(), any(), any(), anyLong(), anyLong()))
                .thenReturn(pageResponse());

        mockMvc.perform(get("/api/v1/advertisers/7/account/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer operator-transactions")
                        .param("transactionType", "CONSUMPTION")
                        .param("startTime", "2026-08-01T00:00:00Z")
                        .param("endTime", "2026-08-31T23:59:59Z")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].businessNo").value("CONSUMPTION-001"))
                .andExpect(jsonPath("$.data.items[0].transactionType").value("CONSUMPTION"))
                .andExpect(jsonPath("$.data.items[0].balanceAfter").value(70.00))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("ADMIN 可以查询空流水页")
    void adminCanQueryEmptyPage() throws Exception {
        authorize("admin-transactions", user(1L, UserRole.ADMIN));
        when(transactionService.findAll(any(), any(), any(), any(), anyLong(), anyLong()))
                .thenReturn(PageResponse.of(List.of(), 1, 20, 0));

        mockMvc.perform(get("/api/v1/advertisers/7/account/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalPages").value(0));
    }

    @Test
    @DisplayName("非法流水类型返回统一 400 且不调用 Service")
    void invalidTransactionTypeReturnsBadRequest() throws Exception {
        authorize("admin-invalid-type", user(1L, UserRole.ADMIN));

        mockMvc.perform(get("/api/v1/advertisers/7/account/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-invalid-type")
                        .param("transactionType", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_ERROR"));
        verifyNoInteractions(transactionService);
    }

    @Test
    @DisplayName("分页大小超过上限返回统一 400")
    void excessivePageSizeReturnsBadRequest() throws Exception {
        authorize("admin-invalid-page", user(1L, UserRole.ADMIN));

        mockMvc.perform(get("/api/v1/advertisers/7/account/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-invalid-page")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_ERROR"));
        verifyNoInteractions(transactionService);
    }

    @Test
    @DisplayName("不完整时间范围返回明确的 400 业务错误")
    void incompleteTimeRangeReturnsBadRequest() throws Exception {
        authorize("admin-incomplete-time", user(1L, UserRole.ADMIN));
        when(transactionService.findAll(any(), any(), any(), any(), anyLong(), anyLong()))
                .thenThrow(new BusinessException(
                        AccountErrorCode.INCOMPLETE_TRANSACTION_TIME_RANGE));

        mockMvc.perform(get("/api/v1/advertisers/7/account/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-incomplete-time")
                        .param("startTime", "2026-08-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("ACCOUNT_INCOMPLETE_TRANSACTION_TIME_RANGE"));
    }

    private PageResponse<AdvertiserAccountTransactionResponse> pageResponse() {
        AdvertiserAccountTransactionResponse transaction =
                new AdvertiserAccountTransactionResponse(
                        21L,
                        8L,
                        "CONSUMPTION-001",
                        AccountTransactionType.CONSUMPTION,
                        new BigDecimal("30.00"),
                        new BigDecimal("70.00"),
                        null,
                        null,
                        null,
                        3L,
                        OffsetDateTime.parse("2026-08-26T00:00:00Z"));
        return PageResponse.of(List.of(transaction), 1, 20, 1);
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
