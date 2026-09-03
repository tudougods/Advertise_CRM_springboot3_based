package com.internship.crm.auth.security;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

@DisplayName("认证授权拒绝日志")
@ExtendWith(ReadableTestResultExtension.class)
class SecurityHandlerLoggingTest {

    @Test
    @DisplayName("未认证请求记录安全 WARN 且不输出 Token 或异常文本")
    void authenticationFailureLogsSafeWarning() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(RestAuthenticationEntryPoint.class);
        ListAppender<ILoggingEvent> appender = attach(logger);
        try {
            MockHttpServletRequest request = request("GET", "/api/v1/users");
            MockHttpServletResponse response = new MockHttpServletResponse();
            new RestAuthenticationEntryPoint(JsonMapper.builder().findAndAddModules().build())
                    .commence(
                            request,
                            response,
                            new InsufficientAuthenticationException("Bearer secret-jwt"));

            assertSecurityWarning(
                    appender,
                    "Authentication required: code=AUTH_UNAUTHORIZED method=GET path=/api/v1/users",
                    "secret-jwt");
            assertEquals(401, response.getStatus());
        } finally {
            detach(logger, appender);
        }
    }

    @Test
    @DisplayName("无权限请求记录安全 WARN 且不输出认证头或异常文本")
    void accessDeniedLogsSafeWarning() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(RestAccessDeniedHandler.class);
        ListAppender<ILoggingEvent> appender = attach(logger);
        try {
            MockHttpServletRequest request = request("DELETE", "/api/v1/users/7");
            MockHttpServletResponse response = new MockHttpServletResponse();
            new RestAccessDeniedHandler(JsonMapper.builder().findAndAddModules().build())
                    .handle(request, response, new AccessDeniedException("role details secret"));

            assertSecurityWarning(
                    appender,
                    "Authorization denied: code=AUTH_ACCESS_DENIED method=DELETE path=/api/v1/users/7",
                    "role details secret");
            assertEquals(403, response.getStatus());
        } finally {
            detach(logger, appender);
        }
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("Authorization", "Bearer header-secret-jwt");
        request.setQueryString("token=query-secret-jwt");
        return request;
    }

    private ListAppender<ILoggingEvent> attach(Logger logger) {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void detach(Logger logger, ListAppender<ILoggingEvent> appender) {
        logger.detachAppender(appender);
        appender.stop();
    }

    private void assertSecurityWarning(
            ListAppender<ILoggingEvent> appender,
            String expectedMessage,
            String exceptionSecret) {
        ILoggingEvent event = appender.list.get(0);
        String message = event.getFormattedMessage();
        assertAll(
                () -> assertEquals(1, appender.list.size()),
                () -> assertEquals(Level.WARN, event.getLevel()),
                () -> assertEquals(expectedMessage, message),
                () -> assertTrue(message.contains("method=")),
                () -> assertTrue(message.contains("path=")),
                () -> assertFalse(message.contains("header-secret-jwt")),
                () -> assertFalse(message.contains("query-secret-jwt")),
                () -> assertFalse(message.contains(exceptionSecret)));
    }
}
