package com.internship.crm.payment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.internship.crm.auth.security.JwtAuthenticationFilter;
import com.internship.crm.auth.security.RestAccessDeniedHandler;
import com.internship.crm.auth.security.RestAuthenticationEntryPoint;
import com.internship.crm.auth.token.JwtTokenService;
import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.common.exception.GlobalExceptionHandler;
import com.internship.crm.common.filter.RequestLoggingFilter;
import com.internship.crm.config.SecurityConfig;
import com.internship.crm.payment.dto.request.SimulateRechargePaymentRequest;
import com.internship.crm.payment.dto.response.RechargeOrderResponse;
import com.internship.crm.payment.entity.RechargeOrderStatus;
import com.internship.crm.payment.exception.PaymentErrorCode;
import com.internship.crm.payment.service.MockPaymentSimulationService;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = MockPaymentSimulationController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class,
        RequestLoggingFilter.class
})
@ActiveProfiles("test")
@DisplayName("本地模拟支付接口与 RBAC 权限")
@ExtendWith(ReadableTestResultExtension.class)
class MockPaymentSimulationSecurityMvcTest {

    private static final String ORDER_NO = "RCH-0123456789ABCDEF0123456789ABCDEF";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MockPaymentSimulationService simulationService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("ADMIN 可以模拟支付成功")
    void adminCanSimulateSuccess() throws Exception {
        authorize("admin-simulate", user(1L, UserRole.ADMIN));
        when(simulationService.simulate(
                org.mockito.ArgumentMatchers.eq(ORDER_NO),
                any(SimulateRechargePaymentRequest.class)))
                .thenReturn(successResponse());

        mockMvc.perform(post("/api/v1/payment-orders/{orderNo}/simulate", ORDER_NO)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"SUCCESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.providerTransactionNo").value("MOCK-TXN-001"))
                .andExpect(jsonPath("$.data.paidAt").exists());
    }

    @Test
    @DisplayName("OPERATOR 不能调用模拟支付")
    void operatorCannotSimulatePayment() throws Exception {
        authorize("operator-simulate", user(2L, UserRole.OPERATOR));

        mockMvc.perform(post("/api/v1/payment-orders/{orderNo}/simulate", ORDER_NO)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer operator-simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"FAILED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));
        verifyNoInteractions(simulationService);
    }

    @Test
    @DisplayName("缺少 JWT 调用模拟支付返回统一 401")
    void missingTokenCannotSimulatePayment() throws Exception {
        mockMvc.perform(post("/api/v1/payment-orders/{orderNo}/simulate", ORDER_NO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"SUCCESS\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
        verifyNoInteractions(simulationService);
    }

    @Test
    @DisplayName("缺少模拟结果返回统一参数校验错误")
    void missingOutcomeReturnsBadRequest() throws Exception {
        authorize("admin-missing-outcome", user(1L, UserRole.ADMIN));

        mockMvc.perform(post("/api/v1/payment-orders/{orderNo}/simulate", ORDER_NO)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-missing-outcome")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_ERROR"));
        verifyNoInteractions(simulationService);
    }

    @Test
    @DisplayName("订单不存在时返回明确 404")
    void missingOrderReturnsNotFound() throws Exception {
        authorize("admin-missing-simulation", user(1L, UserRole.ADMIN));
        when(simulationService.simulate(
                org.mockito.ArgumentMatchers.eq("RCH-MISSING"),
                any(SimulateRechargePaymentRequest.class)))
                .thenThrow(new BusinessException(PaymentErrorCode.ORDER_NOT_FOUND));

        mockMvc.perform(post("/api/v1/payment-orders/RCH-MISSING/simulate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-missing-simulation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"FAILED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("终态订单再次模拟返回明确 409")
    void terminalOrderReturnsConflict() throws Exception {
        authorize("admin-terminal-simulation", user(1L, UserRole.ADMIN));
        when(simulationService.simulate(
                org.mockito.ArgumentMatchers.eq(ORDER_NO),
                any(SimulateRechargePaymentRequest.class)))
                .thenThrow(new BusinessException(PaymentErrorCode.INVALID_STATUS_TRANSITION));

        mockMvc.perform(post("/api/v1/payment-orders/{orderNo}/simulate", ORDER_NO)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-terminal-simulation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"SUCCESS\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_INVALID_STATUS_TRANSITION"));
    }

    private RechargeOrderResponse successResponse() {
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-08-26T23:00:00Z");
        OffsetDateTime paidAt = OffsetDateTime.parse("2026-08-27T00:00:00Z");
        return new RechargeOrderResponse(
                11L,
                ORDER_NO,
                7L,
                8L,
                new BigDecimal("250.00"),
                RechargeOrderStatus.SUCCESS,
                "MOCK-TXN-001",
                paidAt,
                createdAt,
                paidAt);
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
