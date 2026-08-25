package com.internship.crm.advertiser.exception;

import com.internship.crm.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AdvertiserErrorCode implements ErrorCode {
    ADVERTISER_NOT_FOUND("ADVERTISER_NOT_FOUND", "广告主不存在", HttpStatus.NOT_FOUND),
    ADVERTISER_NAME_ALREADY_EXISTS(
            "ADVERTISER_NAME_ALREADY_EXISTS", "广告主名称已存在", HttpStatus.CONFLICT),
    REGISTRATION_NO_ALREADY_EXISTS(
            "ADVERTISER_REGISTRATION_NO_ALREADY_EXISTS", "广告主注册编号已存在", HttpStatus.CONFLICT),
    CATEGORY_NOT_FOUND("ADVERTISER_CATEGORY_NOT_FOUND", "广告主分类不存在", HttpStatus.NOT_FOUND),
    CATEGORY_NAME_ALREADY_EXISTS(
            "ADVERTISER_CATEGORY_NAME_ALREADY_EXISTS", "广告主分类名称已存在", HttpStatus.CONFLICT),
    CATEGORY_DISABLED("ADVERTISER_CATEGORY_DISABLED", "不能分配已禁用的广告主分类", HttpStatus.BAD_REQUEST),
    OWNER_NOT_FOUND("ADVERTISER_OWNER_NOT_FOUND", "广告主负责人不存在", HttpStatus.NOT_FOUND),
    OWNER_DISABLED("ADVERTISER_OWNER_DISABLED", "不能分配已禁用的广告主负责人", HttpStatus.BAD_REQUEST),
    ADVERTISER_HAS_BUSINESS_DATA(
            "ADVERTISER_HAS_BUSINESS_DATA", "广告主存在业务历史，不能删除", HttpStatus.CONFLICT),
    NO_FIELDS_TO_UPDATE(
            "ADVERTISER_NO_FIELDS_TO_UPDATE", "至少需要提供一个待修改字段", HttpStatus.BAD_REQUEST),
    CATEGORY_NO_FIELDS_TO_UPDATE(
            "ADVERTISER_CATEGORY_NO_FIELDS_TO_UPDATE", "至少需要提供一个待修改字段", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus status;

    AdvertiserErrorCode(String code, String message, HttpStatus status) {
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
