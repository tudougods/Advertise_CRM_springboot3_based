package com.internship.crm.report.dto.response;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.internship.crm.testsupport.ReadableTestResultExtension;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@DisplayName("投放报表统一指标响应")
@ExtendWith(ReadableTestResultExtension.class)
class DeliveryMetricsResponseTest {

    @Test
    @DisplayName("金额和比率按接口约定统一精度")
    void normalizesMetricScales() {
        DeliveryMetricsResponse response = new DeliveryMetricsResponse(
                100L,
                25L,
                5L,
                new BigDecimal("12.345"),
                new BigDecimal("0.25444"),
                new BigDecimal("0.19996"),
                new BigDecimal("0.493"));

        assertAll(
                () -> assertEquals(new BigDecimal("12.35"), response.spend()),
                () -> assertEquals(new BigDecimal("0.2544"), response.ctr()),
                () -> assertEquals(new BigDecimal("0.2000"), response.cvr()),
                () -> assertEquals(new BigDecimal("0.49"), response.cpc()));
    }

    @Test
    @DisplayName("空报表返回全部为零且精度稳定的指标")
    void createsStableEmptyMetrics() {
        DeliveryMetricsResponse response = DeliveryMetricsResponse.empty();

        assertAll(
                () -> assertEquals(0L, response.impressions()),
                () -> assertEquals(0L, response.clicks()),
                () -> assertEquals(0L, response.conversions()),
                () -> assertEquals(new BigDecimal("0.00"), response.spend()),
                () -> assertEquals(new BigDecimal("0.0000"), response.ctr()),
                () -> assertEquals(new BigDecimal("0.0000"), response.cvr()),
                () -> assertEquals(new BigDecimal("0.00"), response.cpc()));
    }
}
