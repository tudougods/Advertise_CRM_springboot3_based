package com.internship.crm.common.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Objects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.internship.crm.common.exception.GlobalExceptionHandler;
import com.internship.crm.auth.token.JwtTokenService;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import com.internship.crm.user.service.UserService;

@WebMvcTest(controllers = {
        CommonWebTestController.class,
        CommonMethodValidationTestController.class
})
@Import({
        GlobalExceptionHandler.class,
        RequestLoggingFilter.class,
        CommonWebMvcTest.PermitAllTestSecurity.class
})
@DisplayName("通用 Web 响应与异常处理")
@ExtendWith(ReadableTestResultExtension.class)
class CommonWebMvcTest {

    private static final @NonNull MediaType JSON = Objects.requireNonNull(MediaType.APPLICATION_JSON);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("成功请求返回统一响应并沿用客户端 requestId")
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
    @DisplayName("请求体校验失败返回排序后的字段错误")
    void invalidRequestBodyReturnsSortedFieldErrors() throws Exception {
        MvcResult result = mockMvc.perform(post("/test/common/validate")
                        .contentType(JSON)
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
    @DisplayName("JSON 格式错误返回安全的 400 响应")
    void malformedJsonReturnsBadRequestWithoutParserDetails() throws Exception {
        MvcResult result = mockMvc.perform(post("/test/common/validate")
                        .contentType(JSON)
                        .content("{invalid-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("请求无效"))
                .andReturn();

        assertRequestIdIsConsistent(result);
        assertFalse(result.getResponse().getContentAsString().contains("JsonParseException"));
    }

    @Test
    @DisplayName("缺少必填查询参数返回带字段信息的统一 400")
    void missingRequiredParameterReturnsFieldValidationError() throws Exception {
        mockMvc.perform(get("/test/common/required-parameter"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data[0].field").value("query"))
                .andExpect(jsonPath("$.data[0].message").value("缺少必填参数"));
    }

    @Test
    @DisplayName("Spring 方法参数校验返回具体字段而不是空错误详情")
    void handlerMethodValidationReturnsFieldDetails() throws Exception {
        mockMvc.perform(get("/test/method-validation").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data[0].field").value("page"))
                .andExpect(jsonPath("$.data[0].message").value("页码必须为正数"));
    }

    @Test
    @DisplayName("不支持的 HTTP 方法返回统一 405 而不是 500")
    void unsupportedMethodReturnsMethodNotAllowed() throws Exception {
        mockMvc.perform(post("/test/common/success"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("COMMON_METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").value("请求方法不支持"));
    }

    @Test
    @DisplayName("不支持的媒体类型返回统一 415 而不是 500")
    void unsupportedMediaTypeReturnsSafeResponse() throws Exception {
        mockMvc.perform(post("/test/common/validate")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("sensitive payload"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("COMMON_UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.message").value("请求媒体类型不支持"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("参数类型错误返回客户端安全的校验响应")
    void typeMismatchReturnsAClientSafeValidationError() throws Exception {
        mockMvc.perform(get("/test/common/type/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data[0].field").value("id"))
                .andExpect(jsonPath("$.data[0].message").value("参数类型不正确"));
    }

    @Test
    @DisplayName("业务异常返回对应 HTTP 状态码和客户端消息")
    void businessExceptionUsesItsHttpStatusCodeAndClientMessage() throws Exception {
        mockMvc.perform(get("/test/common/business-error"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMON_CONFLICT"))
                .andExpect(jsonPath("$.message").value("测试资源已存在"));
    }

    @Test
    @DisplayName("未知异常返回通用 500 且不泄露内部信息")
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
    @DisplayName("不存在的接口返回统一 404 响应")
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
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                    .build();
        }
    }
}
