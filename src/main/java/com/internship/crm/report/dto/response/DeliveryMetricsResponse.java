package com.internship.crm.report.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Schema(description = "投放报表统一指标；比率使用 0 到 1 之间的小数表示")
public record DeliveryMetricsResponse(
        @Schema(description = "展示量", example = "10000")
        long impressions,
        @Schema(description = "点击量", example = "500")
        long clicks,
        @Schema(description = "转化量", example = "25")
        long conversions,
        @Schema(description = "总花费，保留两位小数", example = "1200.00")
        BigDecimal spend,
        @Schema(description = "点击率，点击量除以展示量，保留四位小数", example = "0.0500")
        BigDecimal ctr,
        @Schema(description = "转化率，转化量除以点击量，保留四位小数", example = "0.0500")
        BigDecimal cvr,
        @Schema(description = "平均每次点击花费，保留两位小数", example = "2.40")
        BigDecimal cpc) {

    public DeliveryMetricsResponse {
        spend = normalize(Objects.requireNonNull(spend, "spend must not be null"), 2);
        ctr = normalize(Objects.requireNonNull(ctr, "ctr must not be null"), 4);
        cvr = normalize(Objects.requireNonNull(cvr, "cvr must not be null"), 4);
        cpc = normalize(Objects.requireNonNull(cpc, "cpc must not be null"), 2);
    }

    public static DeliveryMetricsResponse empty() {
        return new DeliveryMetricsResponse(
                0,
                0,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }

    private static BigDecimal normalize(BigDecimal value, int scale) {
        return value.setScale(scale, RoundingMode.HALF_UP);
    }
}
