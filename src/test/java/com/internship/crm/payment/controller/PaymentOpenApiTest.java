package com.internship.crm.payment.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.internship.crm.testsupport.ReadableTestResultExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties =
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("模拟支付 OpenAPI 文档")
@ExtendWith(ReadableTestResultExtension.class)
class PaymentOpenApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("订单、模拟支付和验签回调契约均出现在 OpenAPI 文档中")
    void documentsPaymentEndpointsAndSecurityContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/payment-orders'].post.security[0].bearerAuth")
                        .isArray())
                .andExpect(jsonPath("$.paths['/api/v1/payment-orders'].post.description")
                        .value(containsString("仅 ADMIN")))
                .andExpect(jsonPath("$.paths['/api/v1/payment-orders/{orderNo}'].get.security[0].bearerAuth")
                        .isArray())
                .andExpect(jsonPath("$.paths['/api/v1/payment-orders/{orderNo}'].get.description")
                        .value(containsString("ADMIN 和 OPERATOR")))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/payment-orders/{orderNo}/simulate'].post.security[0].bearerAuth")
                        .isArray())
                .andExpect(jsonPath("$.paths['/api/v1/payment-orders/{orderNo}/simulate'].post.description")
                        .value(containsString("仅 ADMIN")))
                .andExpect(jsonPath("$.paths['/api/v1/payment-callbacks/mock'].post.security")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/payment-callbacks/mock'].post.parameters"
                        + "[?(@.name == 'X-Mock-Payment-Timestamp')].required")
                        .value(hasItem(true)))
                .andExpect(jsonPath("$.paths['/api/v1/payment-callbacks/mock'].post.parameters"
                        + "[?(@.name == 'X-Mock-Payment-Signature')].required")
                        .value(hasItem(true)))
                .andExpect(jsonPath("$.paths['/api/v1/payment-callbacks/mock'].post.requestBody.required")
                        .value(true));
    }
}
