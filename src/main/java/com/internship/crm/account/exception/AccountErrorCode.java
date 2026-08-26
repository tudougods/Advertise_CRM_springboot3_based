package com.internship.crm.account.exception;

import com.internship.crm.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** Error codes for advertiser account and ledger operations. */
public enum AccountErrorCode implements ErrorCode {
    ADVERTISER_NOT_FOUND(
            "ACCOUNT_ADVERTISER_NOT_FOUND", "广告主不存在", HttpStatus.NOT_FOUND),
    ACCOUNT_NOT_FOUND(
            "ACCOUNT_NOT_FOUND", "广告主账户不存在", HttpStatus.NOT_FOUND),
    INVALID_BUSINESS_NO(
            "ACCOUNT_INVALID_BUSINESS_NO", "消费业务号不合法", HttpStatus.BAD_REQUEST),
    INVALID_AMOUNT(
            "ACCOUNT_INVALID_AMOUNT", "消费金额必须为最多两位小数的正数", HttpStatus.BAD_REQUEST),
    BUSINESS_NO_ALREADY_EXISTS(
            "ACCOUNT_BUSINESS_NO_ALREADY_EXISTS", "资金业务号已存在", HttpStatus.CONFLICT),
    INSUFFICIENT_BALANCE(
            "ACCOUNT_INSUFFICIENT_BALANCE", "账户余额不足", HttpStatus.CONFLICT),
    DELIVERY_RECORD_NOT_FOUND(
            "ACCOUNT_DELIVERY_RECORD_NOT_FOUND", "关联的投放记录不存在", HttpStatus.NOT_FOUND),
    DELIVERY_RECORD_ADVERTISER_MISMATCH(
            "ACCOUNT_DELIVERY_RECORD_ADVERTISER_MISMATCH",
            "投放记录与账户不属于同一广告主",
            HttpStatus.CONFLICT);

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
