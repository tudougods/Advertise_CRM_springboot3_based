package com.internship.crm.report.model;

import com.internship.crm.report.dto.response.AdvertiserDeliveryReportResponse;
import com.internship.crm.report.dto.response.DeliveryMetricsResponse;
import java.math.BigDecimal;

public record AdvertiserReportRow(
        Long advertiserId,
        String advertiserName,
        long impressions,
        long clicks,
        long conversions,
        BigDecimal spend,
        BigDecimal ctr,
        BigDecimal cvr,
        BigDecimal cpc) {

    public AdvertiserDeliveryReportResponse toResponse() {
        return new AdvertiserDeliveryReportResponse(
                advertiserId,
                advertiserName,
                new DeliveryMetricsResponse(
                        impressions, clicks, conversions, spend, ctr, cvr, cpc));
    }
}
