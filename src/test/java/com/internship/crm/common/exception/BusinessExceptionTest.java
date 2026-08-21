package com.internship.crm.common.exception;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.internship.crm.common.error.CommonErrorCode;
import com.internship.crm.testsupport.ReadableTestResultExtension;

@DisplayName("业务异常模型")
@ExtendWith(ReadableTestResultExtension.class)
class BusinessExceptionTest {

    @Test
    @DisplayName("业务异常保留错误码、客户端消息和原始原因")
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
