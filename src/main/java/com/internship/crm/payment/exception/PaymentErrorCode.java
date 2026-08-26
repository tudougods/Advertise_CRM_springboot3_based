package com.internship.crm.payment.exception;

import com.internship.crm.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** Error codes for simulated payment order and callback operations. */
public enum PaymentErrorCode implements ErrorCode {
    ADVERTISER_NOT_FOUND(
            "PAYMENT_ADVERTISER_NOT_FOUND", "广告主不存在", HttpStatus.NOT_FOUND),
    ACCOUNT_NOT_FOUND(
            "PAYMENT_ACCOUNT_NOT_FOUND", "广告主账户不存在", HttpStatus.NOT_FOUND),
    INVALID_AMOUNT(
            "PAYMENT_INVALID_AMOUNT", "充值金额必须为最多两位小数的正数", HttpStatus.BAD_REQUEST),
    INVALID_ORDER_NO(
            "PAYMENT_INVALID_ORDER_NO", "充值订单号不合法", HttpStatus.BAD_REQUEST),
    ORDER_NOT_FOUND(
            "PAYMENT_ORDER_NOT_FOUND", "充值订单不存在", HttpStatus.NOT_FOUND),
    INVALID_PROVIDER_TRANSACTION_NO(
            "PAYMENT_INVALID_PROVIDER_TRANSACTION_NO",
            "支付平台交易号不合法",
            HttpStatus.BAD_REQUEST),
    INVALID_STATUS_TRANSITION(
            "PAYMENT_INVALID_STATUS_TRANSITION",
            "充值订单当前状态不允许执行该操作",
            HttpStatus.CONFLICT),
    ORDER_UPDATE_CONFLICT(
            "PAYMENT_ORDER_UPDATE_CONFLICT",
            "充值订单状态更新冲突",
            HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus status;

    PaymentErrorCode(String code, String message, HttpStatus status) {
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
