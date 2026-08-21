package com.internship.crm.common.error;

import org.springframework.http.HttpStatus;

/**
 * Contract implemented by common and business-module error codes.
 */
public interface ErrorCode {

    String code();

    String message();

    HttpStatus status();
}
