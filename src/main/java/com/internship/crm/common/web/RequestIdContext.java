package com.internship.crm.common.web;

import org.slf4j.MDC;

/**
 * Access to the request correlation identifier stored in the logging context.
 */
public final class RequestIdContext {

    public static final String HEADER_NAME = "X-Request-ID";
    public static final String MDC_KEY = "requestId";

    private RequestIdContext() {
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    static void set(String requestId) {
        MDC.put(MDC_KEY, requestId);
    }

    static void clear() {
        MDC.remove(MDC_KEY);
    }
}
