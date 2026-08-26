package com.internship.crm.report.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.internship.crm.report.dto.request.DeliveryReportQuery;
import com.internship.crm.report.dto.response.DeliveryMetricsResponse;
import com.internship.crm.report.mapper.DeliveryReportMapper;
import com.internship.crm.report.model.DeliveryReportCriteria;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("投放总览报表 Service")
@ExtendWith({MockitoExtension.class, ReadableTestResultExtension.class})
class DeliveryReportServiceTest {

    @Mock
    private DeliveryReportMapper deliveryReportMapper;

    @Mock
    private DeliveryReportQueryNormalizer queryNormalizer;

    @Test
    @DisplayName("规范化筛选条件后直接返回数据库聚合结果")
    void overviewUsesNormalizedCriteria() {
        DeliveryReportQuery query = new DeliveryReportQuery(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                7L,
                "search");
        DeliveryReportCriteria criteria = new DeliveryReportCriteria(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                7L,
                "SEARCH");
        DeliveryMetricsResponse expected = new DeliveryMetricsResponse(
                1_000L,
                100L,
                10L,
                new BigDecimal("200.00"),
                new BigDecimal("0.1000"),
                new BigDecimal("0.1000"),
                new BigDecimal("2.00"));
        when(queryNormalizer.normalize(query)).thenReturn(criteria);
        when(deliveryReportMapper.selectOverview(criteria)).thenReturn(expected);

        DeliveryReportService service =
                new DeliveryReportService(deliveryReportMapper, queryNormalizer);
        DeliveryMetricsResponse actual = service.overview(query);

        assertSame(expected, actual);
        verify(queryNormalizer).normalize(query);
        verify(deliveryReportMapper).selectOverview(criteria);
    }
}
