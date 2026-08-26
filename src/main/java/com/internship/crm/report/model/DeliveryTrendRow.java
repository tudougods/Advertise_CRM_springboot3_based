package com.internship.crm.report.model;

import com.internship.crm.report.dto.response.DeliveryMetricsResponse;
import com.internship.crm.report.dto.response.DeliveryTrendPointResponse;
import java.math.BigDecimal;
import java.time.LocalDate;

public record DeliveryTrendRow(
        LocalDate periodStart,
        long impressions,
        long clicks,
        long conversions,
        BigDecimal spend,
        BigDecimal ctr,
        BigDecimal cvr,
        BigDecimal cpc) {

    public DeliveryTrendPointResponse toResponse() {
        return new DeliveryTrendPointResponse(
                periodStart,
                new DeliveryMetricsResponse(
                        impressions,
                        clicks,
                        conversions,
                        spend,
                        ctr,
                        cvr,
                        cpc));
    }
}
