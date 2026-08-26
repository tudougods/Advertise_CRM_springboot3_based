package com.internship.crm.account.exception;

import com.internship.crm.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** Error codes for advertiser account and ledger operations. */
public enum AccountErrorCode implements ErrorCode {
    ADVERTISER_NOT_FOUND(
            "ACCOUNT_ADVERTISER_NOT_FOUND", "广告主不存在", HttpStatus.NOT_FOUND),
    ACCOUNT_NOT_FOUND(
            "ACCOUNT_NOT_FOUND", "广告主账户不存在", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus status;

    AccountErrorCode(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public HttpStatus status() {
        return status;
    }
}
