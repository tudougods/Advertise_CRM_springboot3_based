package com.internship.crm.report.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.Objects;

@Schema(description = "一个时间桶内的投放汇总指标")
public record DeliveryTrendPointResponse(
        @Schema(description = "时间桶开始日期；周粒度从周一开始", example = "2026-08-01")
        LocalDate periodStart,
        @Schema(description = "该时间桶内的投放指标")
        DeliveryMetricsResponse metrics) {

    public DeliveryTrendPointResponse {
        Objects.requireNonNull(periodStart, "periodStart must not be null");
        Objects.requireNonNull(metrics, "metrics must not be null");
    }
}
