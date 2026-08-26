package com.internship.crm.payment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.internship.crm.payment.dto.response.MockPaymentCallbackResponse;
import com.internship.crm.payment.entity.PaymentCallbackStatus;
import com.internship.crm.payment.exception.PaymentErrorCode;
import com.internship.crm.payment.service.MockPaymentCallbackService;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import com.internship.crm.user.service.UserService;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = MockPaymentCallbackController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class,
        RequestLoggingFilter.class
})
@ActiveProfiles("test")
@DisplayName("模拟支付回调公开端点与错误契约")
@ExtendWith(ReadableTestResultExtension.class)
class MockPaymentCallbackSecurityMvcTest {

    private static final String PAYLOAD = """
            {"eventId":"evt-1","orderNo":"RCH-1","advertiserId":1,
             "amount":250.00,"outcome":"SUCCESS","providerTransactionNo":"txn-1"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MockPaymentCallbackService callbackService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("回调端点无需 JWT 并原样传递验签头与请求体")
    void callbackEndpointIsPublic() throws Exception {
        when(callbackService.receive(eq("1787792523"), eq("sha256=abc"), any(byte[].class)))
                .thenReturn(new MockPaymentCallbackResponse(
                        "evt-1",
                        "RCH-1",
                        PaymentCallbackStatus.RECEIVED,
                        false,
                        OffsetDateTime.parse("2026-08-27T01:02:03Z")));

        mockMvc.perform(post("/api/v1/payment-callbacks/mock")
                        .header(MockPaymentCallbackController.TIMESTAMP_HEADER, "1787792523")
                        .header(MockPaymentCallbackController.SIGNATURE_HEADER, "sha256=abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.eventId").value("evt-1"))
                .andExpect(jsonPath("$.data.callbackStatus").value("RECEIVED"))
                .andExpect(jsonPath("$.data.duplicate").value(false));
    }

    @Test
    @DisplayName("验签失败通过统一错误响应返回 401")
    void invalidSignatureReturnsUnauthorized() throws Exception {
        when(callbackService.receive(any(), any(), any(byte[].class)))
                .thenThrow(new BusinessException(PaymentErrorCode.CALLBACK_SIGNATURE_INVALID));

        mockMvc.perform(post("/api/v1/payment-callbacks/mock")
                        .header(MockPaymentCallbackController.TIMESTAMP_HEADER, "1787792523")
                        .header(MockPaymentCallbackController.SIGNATURE_HEADER, "sha256=bad")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("PAYMENT_CALLBACK_SIGNATURE_INVALID"));
    }
}
