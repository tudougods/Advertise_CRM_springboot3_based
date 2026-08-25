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
    DELIVERY_RECORD_NOT_FOUND(
            "DELIVERY_RECORD_NOT_FOUND", "投放记录不存在", HttpStatus.NOT_FOUND),
    INVALID_DATE_RANGE(
            "DELIVERY_INVALID_DATE_RANGE", "开始日期不能晚于结束日期", HttpStatus.BAD_REQUEST),
    DATE_RANGE_TOO_LARGE(
            "DELIVERY_DATE_RANGE_TOO_LARGE", "投放记录查询日期范围不能超过 366 天", HttpStatus.BAD_REQUEST),
    NO_FIELDS_TO_UPDATE(
            "DELIVERY_NO_FIELDS_TO_UPDATE", "至少需要提供一个待修改字段", HttpStatus.BAD_REQUEST),
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
