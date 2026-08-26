package com.internship.crm.report.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "报表时间粒度")
public enum ReportTimeGranularity {
    DAY,
    WEEK,
    MONTH
}
