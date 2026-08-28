package com.internship.crm.report.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

@Schema(description = "广告类型维度的投放汇总")
public record AdvertisingTypeDeliveryReportResponse(
        @Schema(description = "广告类型 ID", example = "1")
        Long advertisingTypeId,
        @Schema(description = "广告类型编码", example = "SEARCH")
        String advertisingTypeCode,
        @Schema(description = "广告类型名称", example = "搜索广告")
        String advertisingTypeName,
        @Schema(description = "该广告类型的投放指标")
        DeliveryMetricsResponse metrics) {

    public AdvertisingTypeDeliveryReportResponse {
        Objects.requireNonNull(advertisingTypeId, "advertisingTypeId must not be null");
        Objects.requireNonNull(advertisingTypeCode, "advertisingTypeCode must not be null");
        Objects.requireNonNull(advertisingTypeName, "advertisingTypeName must not be null");
        Objects.requireNonNull(metrics, "metrics must not be null");
    }
}
