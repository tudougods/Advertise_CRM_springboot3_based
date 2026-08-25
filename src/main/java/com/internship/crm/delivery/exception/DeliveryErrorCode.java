package com.internship.crm.delivery.exception;

import com.internship.crm.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum DeliveryErrorCode implements ErrorCode {
    ADVERTISER_NOT_FOUND(
            "DELIVERY_ADVERTISER_NOT_FOUND", "广告主不存在", HttpStatus.NOT_FOUND),
    ADVERTISER_DISABLED(
            "DELIVERY_ADVERTISER_DISABLED", "不能为已禁用的广告主录入投放数据", HttpStatus.BAD_REQUEST),
    ADVERTISING_TYPE_NOT_FOUND(
            "DELIVERY_ADVERTISING_TYPE_NOT_FOUND", "广告类型不存在", HttpStatus.NOT_FOUND),
    ADVERTISING_TYPE_DISABLED(
            "DELIVERY_ADVERTISING_TYPE_DISABLED", "不能使用已禁用的广告类型", HttpStatus.BAD_REQUEST),
    EXTERNAL_RECORD_NO_ALREADY_EXISTS(
            "DELIVERY_EXTERNAL_RECORD_NO_ALREADY_EXISTS", "外部投放记录号已存在", HttpStatus.CONFLICT),
    INVALID_METRICS(
            "DELIVERY_INVALID_METRICS", "投放指标或花费不符合漏斗规则", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus status;

    DeliveryErrorCode(String code, String message, HttpStatus status) {
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
