package com.internship.crm.common.exception;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import com.internship.crm.common.error.CommonErrorCode;

class BusinessExceptionTest {

    @Test
    void keepsTheErrorCodeClientMessageAndCause() {
        IllegalStateException cause = new IllegalStateException("internal detail");

        BusinessException exception = new BusinessException(
                CommonErrorCode.CONFLICT,
                "用户名已存在",
                cause);

        assertAll(
                () -> assertSame(CommonErrorCode.CONFLICT, exception.errorCode()),
                () -> assertEquals("用户名已存在", exception.getMessage()),
                () -> assertSame(cause, exception.getCause()));
    }
}
