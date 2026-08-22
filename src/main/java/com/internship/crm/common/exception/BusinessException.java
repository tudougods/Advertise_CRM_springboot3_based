package com.internship.crm.common.exception;

import java.util.Objects;

/**
 * Expected failure caused by a business rule rather than a system fault.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.message(), null);
    }

    public BusinessException(ErrorCode errorCode, String clientMessage) {
        this(errorCode, clientMessage, null);
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        this(errorCode, errorCode.message(), cause);
    }

    public BusinessException(ErrorCode errorCode, String clientMessage, Throwable cause) {
        super(Objects.requireNonNull(clientMessage, "clientMessage must not be null"), cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
