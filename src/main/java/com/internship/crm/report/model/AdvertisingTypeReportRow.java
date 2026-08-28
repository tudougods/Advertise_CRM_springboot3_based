package com.internship.crm.report.model;

import com.internship.crm.report.dto.response.AdvertisingTypeDeliveryReportResponse;
import com.internship.crm.report.dto.response.DeliveryMetricsResponse;
import java.math.BigDecimal;

public record AdvertisingTypeReportRow(
        Long advertisingTypeId,
        String advertisingTypeCode,
        String advertisingTypeName,
        long impressions,
        long clicks,
        long conversions,
        BigDecimal spend,
        BigDecimal ctr,
        BigDecimal cvr,
        BigDecimal cpc) {

    public AdvertisingTypeDeliveryReportResponse toResponse() {
        return new AdvertisingTypeDeliveryReportResponse(
                advertisingTypeId,
                advertisingTypeCode,
                advertisingTypeName,
                new DeliveryMetricsResponse(
                        impressions, clicks, conversions, spend, ctr, cvr, cpc));
    }
}
