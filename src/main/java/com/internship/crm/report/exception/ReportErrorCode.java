package com.internship.crm.report.exception;

import com.internship.crm.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ReportErrorCode implements ErrorCode {
    INVALID_DATE_RANGE(
            "REPORT_INVALID_DATE_RANGE", "开始日期不能晚于结束日期", HttpStatus.BAD_REQUEST),
    DATE_RANGE_TOO_LARGE(
            "REPORT_DATE_RANGE_TOO_LARGE", "报表查询日期范围不能超过 366 天", HttpStatus.BAD_REQUEST),
    INCOMPLETE_DATE_RANGE(
            "REPORT_INCOMPLETE_DATE_RANGE", "开始日期和结束日期必须同时提供", HttpStatus.BAD_REQUEST),
    BLANK_ADVERTISING_TYPE_CODE(
            "REPORT_BLANK_ADVERTISING_TYPE_CODE", "广告类型编码不能为空白", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus status;

    ReportErrorCode(String code, String message, HttpStatus status) {
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
