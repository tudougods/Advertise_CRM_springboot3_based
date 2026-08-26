package com.internship.crm.payment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.internship.crm.payment.dto.request.CreateRechargeOrderRequest;
import com.internship.crm.payment.dto.response.RechargeOrderResponse;
import com.internship.crm.payment.entity.RechargeOrderStatus;
import com.internship.crm.payment.exception.PaymentErrorCode;
import com.internship.crm.payment.service.RechargeOrderService;
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

@WebMvcTest(controllers = RechargeOrderController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class,
        RequestLoggingFilter.class
})
@DisplayName("充值订单创建查询接口与 RBAC 权限")
@ExtendWith(ReadableTestResultExtension.class)
class RechargeOrderSecurityMvcTest {

    private static final String ORDER_NO = "RCH-0123456789ABCDEF0123456789ABCDEF";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RechargeOrderService rechargeOrderService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("缺少 JWT 创建订单返回统一 401")
    void missingTokenCannotCreateOrder() throws Exception {
        mockMvc.perform(post("/api/v1/payment-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
        verifyNoInteractions(rechargeOrderService);
    }

    @Test
    @DisplayName("OPERATOR 不能创建充值订单")
    void operatorCannotCreateOrder() throws Exception {
        authorize("operator-create-order", user(2L, UserRole.OPERATOR));

        mockMvc.perform(post("/api/v1/payment-orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer operator-create-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));
        verifyNoInteractions(rechargeOrderService);
    }

    @Test
    @DisplayName("ADMIN 创建充值订单返回 201 和 PENDING 订单")
    void adminCanCreateOrder() throws Exception {
        authorize("admin-create-order", user(1L, UserRole.ADMIN));
        when(rechargeOrderService.create(any(CreateRechargeOrderRequest.class)))
                .thenReturn(response());

        mockMvc.perform(post("/api/v1/payment-orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-create-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderNo").value(ORDER_NO))
                .andExpect(jsonPath("$.data.advertiserId").value(7))
                .andExpect(jsonPath("$.data.advertiserAccountId").value(8))
                .andExpect(jsonPath("$.data.amount").value(250.00))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.providerTransactionNo").doesNotExist())
                .andExpect(jsonPath("$.data.paidAt").doesNotExist());
    }

    @Test
    @DisplayName("非法创建请求返回统一 400 且不调用 Service")
    void invalidCreateRequestReturnsBadRequest() throws Exception {
        authorize("admin-invalid-order", user(1L, UserRole.ADMIN));

        mockMvc.perform(post("/api/v1/payment-orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-invalid-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"advertiserId\":0,\"amount\":0.001}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.length()").value(3));
        verifyNoInteractions(rechargeOrderService);
    }

    @Test
    @DisplayName("OPERATOR 可以按订单号查询订单")
    void operatorCanFindOrder() throws Exception {
        authorize("operator-find-order", user(2L, UserRole.OPERATOR));
        when(rechargeOrderService.findByOrderNo(ORDER_NO)).thenReturn(response());

        mockMvc.perform(get("/api/v1/payment-orders/{orderNo}", ORDER_NO)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer operator-find-order"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderNo").value(ORDER_NO))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("缺少 JWT 查询订单返回统一 401")
    void missingTokenCannotFindOrder() throws Exception {
        mockMvc.perform(get("/api/v1/payment-orders/{orderNo}", ORDER_NO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
        verifyNoInteractions(rechargeOrderService);
    }

    @Test
    @DisplayName("订单不存在时返回明确的 404 业务错误")
    void missingOrderReturnsNotFound() throws Exception {
        authorize("operator-missing-order", user(2L, UserRole.OPERATOR));
        when(rechargeOrderService.findByOrderNo("RCH-MISSING"))
                .thenThrow(new BusinessException(PaymentErrorCode.ORDER_NOT_FOUND));

        mockMvc.perform(get("/api/v1/payment-orders/RCH-MISSING")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer operator-missing-order"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("PAYMENT_ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("充值订单不存在"));
    }

    private String validRequest() {
        return "{\"advertiserId\":7,\"amount\":250.00}";
    }

    private RechargeOrderResponse response() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-26T12:00:00Z");
        return new RechargeOrderResponse(
                11L,
                ORDER_NO,
                7L,
                8L,
                new BigDecimal("250.00"),
                RechargeOrderStatus.PENDING,
                null,
                null,
                now,
                now);
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
