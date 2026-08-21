package com.internship.crm.common.api;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import com.internship.crm.common.error.CommonErrorCode;

class ApiResponseTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void successUsesStableEnvelopeAndCurrentRequestId() {
        MDC.put("requestId", "request-123");

        ApiResponse<Map<String, String>> response = ApiResponse.success(Map.of("name", "crm"));

        assertAll(
                () -> assertTrue(response.success()),
                () -> assertEquals(ApiResponse.SUCCESS_CODE, response.code()),
                () -> assertEquals(ApiResponse.SUCCESS_MESSAGE, response.message()),
                () -> assertEquals(Map.of("name", "crm"), response.data()),
                () -> assertNotNull(response.timestamp()),
                () -> assertEquals("request-123", response.requestId()));
    }

    @Test
    void failureUsesTheProvidedErrorCodeAndDetails() {
        Map<String, String> details = Map.of("username", "用户名已存在");

        ApiResponse<Map<String, String>> response = ApiResponse.failure(
                CommonErrorCode.CONFLICT,
                details);

        assertAll(
                () -> assertFalse(response.success()),
                () -> assertEquals("COMMON_CONFLICT", response.code()),
                () -> assertEquals("请求与当前资源状态冲突", response.message()),
                () -> assertEquals(details, response.data()),
                () -> assertNotNull(response.timestamp()));
    }
}
