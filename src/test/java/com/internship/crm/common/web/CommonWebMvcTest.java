package com.internship.crm.common.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.internship.crm.common.exception.GlobalExceptionHandler;

@WebMvcTest(controllers = CommonWebTestController.class)
@Import({
        GlobalExceptionHandler.class,
        RequestLoggingFilter.class,
        CommonWebMvcTest.PermitAllTestSecurity.class
})
class CommonWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void successResponseUsesTheCommonEnvelopeAndClientRequestId() throws Exception {
        mockMvc.perform(get("/test/common/success")
                        .header(RequestIdContext.HEADER_NAME, "client-request-123"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdContext.HEADER_NAME, "client-request-123"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("请求成功"))
                .andExpect(jsonPath("$.data.name").value("crm"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.requestId").value("client-request-123"));
    }

    @Test
    void invalidRequestBodyReturnsSortedFieldErrors() throws Exception {
        MvcResult result = mockMvc.perform(post("/test/common/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data[0].field").value("name"))
                .andExpect(jsonPath("$.data[0].message").value("名称不能为空"))
                .andReturn();

        assertRequestIdIsConsistent(result);
    }

    @Test
    void malformedJsonReturnsBadRequestWithoutParserDetails() throws Exception {
        MvcResult result = mockMvc.perform(post("/test/common/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("请求无效"))
                .andReturn();

        assertRequestIdIsConsistent(result);
        assertFalse(result.getResponse().getContentAsString().contains("JsonParseException"));
    }

    @Test
    void typeMismatchReturnsAClientSafeValidationError() throws Exception {
        mockMvc.perform(get("/test/common/type/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data[0].field").value("id"))
                .andExpect(jsonPath("$.data[0].message").value("参数类型不正确"));
    }

    @Test
    void businessExceptionUsesItsHttpStatusCodeAndClientMessage() throws Exception {
        mockMvc.perform(get("/test/common/business-error"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMON_CONFLICT"))
                .andExpect(jsonPath("$.message").value("测试资源已存在"));
    }

    @Test
    void unexpectedExceptionReturnsGenericInternalError() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/common/unexpected-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON_INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("服务器内部错误"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("sensitive internal detail"));
        assertFalse(body.contains("IllegalStateException"));
    }

    @Test
    void unknownRouteReturnsTheCommonNotFoundResponse() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/common/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_NOT_FOUND"))
                .andReturn();

        assertRequestIdIsConsistent(result);
    }

    private void assertRequestIdIsConsistent(MvcResult result) throws Exception {
        String headerRequestId = result.getResponse().getHeader(RequestIdContext.HEADER_NAME);
        JsonNode responseBody = objectMapper.readTree(result.getResponse().getContentAsString());

        assertNotNull(headerRequestId);
        assertTrue(!headerRequestId.isBlank());
        assertEquals(headerRequestId, responseBody.path("requestId").asText());
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class PermitAllTestSecurity {

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                    .build();
        }
    }
}
