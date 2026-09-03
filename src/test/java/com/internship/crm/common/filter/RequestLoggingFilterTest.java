package com.internship.crm.common.filter;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.internship.crm.testsupport.ReadableTestResultExtension;

@DisplayName("请求编号与日志过滤器")
@ExtendWith(ReadableTestResultExtension.class)
class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @AfterEach
    void clearRequestContext() {
        RequestIdContext.clear();
    }

    @Test
    @DisplayName("保留安全的客户端 requestId 并在请求后清理上下文")
    void preservesASafeClientRequestIdAndClearsMdcAfterTheRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        request.addHeader(RequestIdContext.HEADER_NAME, "client-request_123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestIdInsideChain = new AtomicReference<>();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            requestIdInsideChain.set(RequestIdContext.current());
            ((MockHttpServletResponse) servletResponse).setStatus(204);
        });

        assertAll(
                () -> assertEquals("client-request_123", requestIdInsideChain.get()),
                () -> assertEquals("client-request_123", response.getHeader(RequestIdContext.HEADER_NAME)),
                () -> assertEquals(204, response.getStatus()),
                () -> assertNull(RequestIdContext.current()));
    }

    @Test
    @DisplayName("替换不安全的客户端 requestId")
    void replacesAnUnsafeClientRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        request.addHeader(RequestIdContext.HEADER_NAME, "unsafe request id\nvalue");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> generatedRequestId = new AtomicReference<>();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                generatedRequestId.set(RequestIdContext.current()));

        assertAll(
                () -> assertNotNull(generatedRequestId.get()),
                () -> assertNotEquals("unsafe request id\nvalue", generatedRequestId.get()),
                () -> assertEquals(generatedRequestId.get(),
                        response.getHeader(RequestIdContext.HEADER_NAME)),
                () -> assertNull(RequestIdContext.current()));
    }

    @Test
    @DisplayName("完成日志包含请求要素和 requestId 且不记录敏感请求内容")
    void completionLogContainsCorrelationFieldsWithoutSensitiveRequestContent() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test/login");
            request.addHeader(RequestIdContext.HEADER_NAME, "request-log-123");
            request.addHeader("Authorization", "Bearer jwt-secret-value");
            request.setQueryString("password=query-secret");
            request.setContent("{\"password\":\"body-secret\"}".getBytes());
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, (servletRequest, servletResponse) ->
                    ((MockHttpServletResponse) servletResponse).setStatus(201));

            ILoggingEvent event = appender.list.get(0);
            String message = event.getFormattedMessage();
            assertAll(
                    () -> assertEquals(1, appender.list.size()),
                    () -> assertEquals(Level.INFO, event.getLevel()),
                    () -> assertEquals("request-log-123",
                            event.getMDCPropertyMap().get(RequestIdContext.MDC_KEY)),
                    () -> assertTrue(message.contains("method=POST")),
                    () -> assertTrue(message.contains("path=/test/login")),
                    () -> assertTrue(message.contains("status=201")),
                    () -> assertTrue(message.contains("durationMs=")),
                    () -> assertFalse(message.contains("jwt-secret-value")),
                    () -> assertFalse(message.contains("query-secret")),
                    () -> assertFalse(message.contains("body-secret")));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
