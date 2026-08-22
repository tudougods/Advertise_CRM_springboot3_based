package com.internship.crm.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Contract implemented by common and business-module error codes.
 */
public interface ErrorCode {

    String code();

    String message();

    HttpStatus status();
}
