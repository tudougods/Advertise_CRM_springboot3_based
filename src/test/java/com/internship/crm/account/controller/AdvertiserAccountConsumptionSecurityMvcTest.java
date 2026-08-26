package com.internship.crm.account.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.internship.crm.account.dto.response.AdvertiserAccountTransactionResponse;
import com.internship.crm.account.entity.AccountTransactionType;
import com.internship.crm.account.exception.AccountErrorCode;
import com.internship.crm.account.service.AdvertiserAccountConsumptionService;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdvertiserAccountConsumptionController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class,
        RequestLoggingFilter.class
})
@DisplayName("广告主账户消费接口与 RBAC 权限")
@ExtendWith(ReadableTestResultExtension.class)
class AdvertiserAccountConsumptionSecurityMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdvertiserAccountConsumptionService consumptionService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("缺少 JWT 创建消费返回统一 401")
    void missingTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/advertisers/7/account/consumptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("OPERATOR 不能创建账户消费")
    void operatorCannotCreateConsumption() throws Exception {
        authorize("operator-consumption", user(2L, UserRole.OPERATOR));

        mockMvc.perform(post("/api/v1/advertisers/7/account/consumptions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer operator-consumption")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));
        verifyNoInteractions(consumptionService);
    }

    @Test
    @DisplayName("ADMIN 创建消费返回 201 并记录当前操作用户")
    void adminCanCreateConsumption() throws Exception {
        authorize("admin-consumption", user(3L, UserRole.ADMIN));
        when(consumptionService.consume(any(), any(), any())).thenReturn(response());

        mockMvc.perform(post("/api/v1/advertisers/7/account/consumptions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-consumption")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.businessNo").value("CONSUMPTION-001"))
                .andExpect(jsonPath("$.data.transactionType").value("CONSUMPTION"))
                .andExpect(jsonPath("$.data.balanceAfter").value(70.00));
        verify(consumptionService).consume(
                org.mockito.ArgumentMatchers.eq(7L),
                any(),
                org.mockito.ArgumentMatchers.eq(3L));
    }

    @Test
    @DisplayName("非法消费金额返回统一 400 且不调用 Service")
    void invalidAmountReturnsBadRequest() throws Exception {
        authorize("admin-invalid-consumption", user(3L, UserRole.ADMIN));

        mockMvc.perform(post("/api/v1/advertisers/7/account/consumptions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-invalid-consumption")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "businessNo": "CONSUMPTION-001",
                                  "amount": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_ERROR"));
        verifyNoInteractions(consumptionService);
    }

    @Test
    @DisplayName("余额不足返回明确的 409 业务错误")
    void insufficientBalanceReturnsConflict() throws Exception {
        authorize("admin-insufficient", user(3L, UserRole.ADMIN));
        when(consumptionService.consume(any(), any(), any()))
                .thenThrow(new BusinessException(AccountErrorCode.INSUFFICIENT_BALANCE));

        mockMvc.perform(post("/api/v1/advertisers/7/account/consumptions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-insufficient")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_INSUFFICIENT_BALANCE"))
                .andExpect(jsonPath("$.message").value("账户余额不足"));
    }

    private String validRequest() {
        return """
                {
                  "businessNo": "CONSUMPTION-001",
                  "amount": 30.00,
                  "deliveryRecordId": 11,
                  "remark": "搜索广告结算"
                }
                """;
    }

    private AdvertiserAccountTransactionResponse response() {
        return new AdvertiserAccountTransactionResponse(
                21L,
                8L,
                "CONSUMPTION-001",
                AccountTransactionType.CONSUMPTION,
                new BigDecimal("30.00"),
                new BigDecimal("70.00"),
                11L,
                null,
                "搜索广告结算",
                3L,
                OffsetDateTime.parse("2026-08-26T00:00:00Z"));
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
