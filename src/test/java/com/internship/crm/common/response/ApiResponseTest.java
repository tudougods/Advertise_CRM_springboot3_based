package com.internship.crm.common.response;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;

import com.internship.crm.common.exception.CommonErrorCode;
import com.internship.crm.testsupport.ReadableTestResultExtension;

@DisplayName("统一 API 响应结构")
@ExtendWith(ReadableTestResultExtension.class)
class ApiResponseTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("成功响应包含统一字段和当前 requestId")
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
    @DisplayName("失败响应包含指定错误码和错误详情")
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

    @Test
    @DisplayName("无数据成功响应仍保留完整统一字段")
    void successWithoutDataKeepsTheStableEnvelope() {
        MDC.put("requestId", "request-no-data");

        ApiResponse<Void> response = ApiResponse.successWithoutData();

        assertAll(
                () -> assertTrue(response.success()),
                () -> assertEquals(ApiResponse.SUCCESS_CODE, response.code()),
                () -> assertEquals(ApiResponse.SUCCESS_MESSAGE, response.message()),
                () -> assertNull(response.data()),
                () -> assertNotNull(response.timestamp()),
                () -> assertEquals("request-no-data", response.requestId()));
    }

    @Test
    @DisplayName("响应拒绝空白结果码和消息")
    void rejectsBlankCodeAndMessage() {
        Instant now = Instant.now();

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ApiResponse<>(false, " ", "请求失败", null, now, null)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ApiResponse<>(false, "COMMON_BAD_REQUEST", " ", null, now, null)));
    }

    @Test
    @DisplayName("响应拒绝成功标志与结果码矛盾")
    void rejectsInconsistentSuccessAndCode() {
        Instant now = Instant.now();

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ApiResponse<>(
                                true,
                                CommonErrorCode.BAD_REQUEST.code(),
                                CommonErrorCode.BAD_REQUEST.message(),
                                null,
                                now,
                                null)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ApiResponse<>(
                                false,
                                ApiResponse.SUCCESS_CODE,
                                ApiResponse.SUCCESS_MESSAGE,
                                null,
                                now,
                                null)));
    }
}
