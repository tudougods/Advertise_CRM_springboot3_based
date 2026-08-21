package com.internship.crm.common.web;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @AfterEach
    void clearRequestContext() {
        RequestIdContext.clear();
    }

    @Test
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
