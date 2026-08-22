package com.internship.crm.common.filter;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
}
