package com.internship.crm.report.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

@Schema(description = "广告主维度的投放汇总")
public record AdvertiserDeliveryReportResponse(
        @Schema(description = "广告主 ID", example = "1")
        Long advertiserId,
        @Schema(description = "广告主名称", example = "示例科技有限公司")
        String advertiserName,
        @Schema(description = "该广告主的投放指标")
        DeliveryMetricsResponse metrics) {

    public AdvertiserDeliveryReportResponse {
        Objects.requireNonNull(advertiserId, "advertiserId must not be null");
        Objects.requireNonNull(advertiserName, "advertiserName must not be null");
        Objects.requireNonNull(metrics, "metrics must not be null");
    }
}
