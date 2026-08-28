package com.internship.crm.report.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "广告主报表排序字段")
public enum AdvertiserReportSortField {
    IMPRESSIONS,
    CLICKS,
    CONVERSIONS,
    SPEND,
    CTR,
    CVR,
    CPC
}
