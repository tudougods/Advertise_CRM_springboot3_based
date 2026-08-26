package com.internship.crm.report.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.internship.crm.common.response.PageResponse;
import com.internship.crm.report.dto.request.DeliveryReportQuery;
import com.internship.crm.report.dto.response.AdvertiserDeliveryReportResponse;
import com.internship.crm.report.dto.response.AdvertisingTypeDeliveryReportResponse;
import com.internship.crm.report.dto.response.DeliveryMetricsResponse;
import com.internship.crm.report.dto.response.DeliveryTrendPointResponse;
import com.internship.crm.report.mapper.DeliveryReportMapper;
import com.internship.crm.report.model.AdvertiserReportRow;
import com.internship.crm.report.model.AdvertiserReportSortField;
import com.internship.crm.report.model.AdvertisingTypeReportRow;
import com.internship.crm.report.model.DeliveryReportCriteria;
import com.internship.crm.report.model.DeliveryTrendRow;
import com.internship.crm.report.model.ReportSortDirection;
import com.internship.crm.report.model.ReportTimeGranularity;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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

    @Test
    @DisplayName("趋势查询使用枚举白名单并把数据库行转换为时间桶响应")
    void trendUsesWhitelistedGranularityAndMapsRows() {
        DeliveryReportQuery query = new DeliveryReportQuery(null, null, null, null);
        DeliveryReportCriteria criteria = new DeliveryReportCriteria(
                LocalDate.of(2026, 7, 28),
                LocalDate.of(2026, 8, 26),
                null,
                null);
        DeliveryTrendRow row = new DeliveryTrendRow(
                LocalDate.of(2026, 8, 1),
                1_000L,
                100L,
                10L,
                new BigDecimal("200.00"),
                new BigDecimal("0.1000"),
                new BigDecimal("0.1000"),
                new BigDecimal("2.00"));
        when(queryNormalizer.normalize(query)).thenReturn(criteria);
        when(deliveryReportMapper.selectTrend(criteria, "month")).thenReturn(List.of(row));

        DeliveryReportService service =
                new DeliveryReportService(deliveryReportMapper, queryNormalizer);
        List<DeliveryTrendPointResponse> result =
                service.trend(query, ReportTimeGranularity.MONTH);

        assertEquals(1, result.size());
        assertEquals(LocalDate.of(2026, 8, 1), result.get(0).periodStart());
        assertEquals(new BigDecimal("0.1000"), result.get(0).metrics().ctr());
        verify(deliveryReportMapper).selectTrend(criteria, "month");
    }

    @Test
    @DisplayName("广告主维度使用白名单排序并返回完整分页元数据")
    void advertiserDimensionUsesSafeSortAndPagination() {
        DeliveryReportQuery query = new DeliveryReportQuery(null, null, null, null);
        DeliveryReportCriteria criteria = new DeliveryReportCriteria(
                LocalDate.of(2026, 7, 28),
                LocalDate.of(2026, 8, 26),
                null,
                null);
        AdvertiserReportRow row = new AdvertiserReportRow(
                7L, "示例广告主", 1_000L, 100L, 10L,
                new BigDecimal("200.00"), new BigDecimal("0.1000"),
                new BigDecimal("0.1000"), new BigDecimal("2.00"));
        when(queryNormalizer.normalize(query)).thenReturn(criteria);
        when(deliveryReportMapper.countByAdvertiser(criteria)).thenReturn(3L);
        when(deliveryReportMapper.selectByAdvertiser(
                criteria, "CTR", "ASC", 2L, 2L)).thenReturn(List.of(row));

        DeliveryReportService service =
                new DeliveryReportService(deliveryReportMapper, queryNormalizer);
        PageResponse<AdvertiserDeliveryReportResponse> result = service.byAdvertiser(
                query, 2, 2, AdvertiserReportSortField.CTR, ReportSortDirection.ASC);

        assertEquals(1, result.items().size());
        assertEquals(2, result.page());
        assertEquals(3, result.total());
        assertEquals(2, result.totalPages());
        assertEquals("示例广告主", result.items().get(0).advertiserName());
        verify(deliveryReportMapper).selectByAdvertiser(
                criteria, "CTR", "ASC", 2L, 2L);
    }

    @Test
    @DisplayName("没有广告主汇总记录时直接返回空页")
    void advertiserDimensionReturnsEmptyPageWithoutDataQuery() {
        DeliveryReportQuery query = new DeliveryReportQuery(null, null, null, null);
        DeliveryReportCriteria criteria = new DeliveryReportCriteria(
                LocalDate.of(2026, 7, 28),
                LocalDate.of(2026, 8, 26),
                null,
                null);
        when(queryNormalizer.normalize(query)).thenReturn(criteria);
        when(deliveryReportMapper.countByAdvertiser(criteria)).thenReturn(0L);

        DeliveryReportService service =
                new DeliveryReportService(deliveryReportMapper, queryNormalizer);
        PageResponse<AdvertiserDeliveryReportResponse> result = service.byAdvertiser(
                query, 3, 20, AdvertiserReportSortField.SPEND, ReportSortDirection.DESC);

        assertEquals(List.of(), result.items());
        assertEquals(0, result.total());
        verify(deliveryReportMapper, never()).selectByAdvertiser(
                any(), any(), any(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("广告类型维度把数据库分组行转换为统一指标响应")
    void advertisingTypeDimensionMapsRows() {
        DeliveryReportQuery query = new DeliveryReportQuery(null, null, null, null);
        DeliveryReportCriteria criteria = new DeliveryReportCriteria(
                LocalDate.of(2026, 7, 28),
                LocalDate.of(2026, 8, 26),
                null,
                null);
        AdvertisingTypeReportRow row = new AdvertisingTypeReportRow(
                1L, "SEARCH", "搜索广告", 1_000L, 100L, 10L,
                new BigDecimal("200.00"), new BigDecimal("0.1000"),
                new BigDecimal("0.1000"), new BigDecimal("2.00"));
        when(queryNormalizer.normalize(query)).thenReturn(criteria);
        when(deliveryReportMapper.selectByAdvertisingType(criteria)).thenReturn(List.of(row));

        DeliveryReportService service =
                new DeliveryReportService(deliveryReportMapper, queryNormalizer);
        List<AdvertisingTypeDeliveryReportResponse> result =
                service.byAdvertisingType(query);

        assertEquals(1, result.size());
        assertEquals("SEARCH", result.get(0).advertisingTypeCode());
        assertEquals(new BigDecimal("200.00"), result.get(0).metrics().spend());
    }
}
