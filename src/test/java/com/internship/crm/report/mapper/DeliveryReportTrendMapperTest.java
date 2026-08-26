package com.internship.crm.report.mapper;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.internship.crm.advertiser.dto.request.CreateAdvertiserRequest;
import com.internship.crm.advertiser.dto.response.AdvertiserResponse;
import com.internship.crm.advertiser.service.AdvertiserService;
import com.internship.crm.delivery.entity.AdvertisingDeliveryRecord;
import com.internship.crm.delivery.mapper.AdvertisingDeliveryRecordMapper;
import com.internship.crm.delivery.mapper.AdvertisingTypeMapper;
import com.internship.crm.report.model.DeliveryReportCriteria;
import com.internship.crm.report.model.DeliveryTrendRow;
import com.internship.crm.report.model.ReportTimeGranularity;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties =
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@Transactional
@DisplayName("投放趋势报表聚合 SQL")
@ExtendWith(ReadableTestResultExtension.class)
class DeliveryReportTrendMapperTest {

    @Autowired
    private AdvertiserService advertiserService;

    @Autowired
    private AdvertisingTypeMapper advertisingTypeMapper;

    @Autowired
    private AdvertisingDeliveryRecordMapper deliveryRecordMapper;

    @Autowired
    private DeliveryReportMapper deliveryReportMapper;

    private Long advertiserId;
    private Long searchTypeId;

    @BeforeEach
    void setUp() {
        AdvertiserResponse advertiser = advertiserService.create(new CreateAdvertiserRequest(
                "report-trend-" + UUID.randomUUID(),
                null, null, null, null, null, null, null));
        AdvertiserResponse otherAdvertiser = advertiserService.create(new CreateAdvertiserRequest(
                "report-trend-other-" + UUID.randomUUID(),
                null, null, null, null, null, null, null));
        advertiserId = advertiser.id();
        searchTypeId = advertisingTypeMapper.findByCodeIgnoreCase("SEARCH").orElseThrow().getId();
        Long displayTypeId =
                advertisingTypeMapper.findByCodeIgnoreCase("DISPLAY").orElseThrow().getId();

        insertRecord(LocalDate.of(2026, 1, 31), 1_000L, 100L, 10L, "200.00");
        insertRecord(LocalDate.of(2026, 2, 1), 500L, 50L, 5L, "100.00");
        insertRecord(LocalDate.of(2026, 2, 2), 2_000L, 100L, 10L, "300.00");
        insertRecord(LocalDate.of(2026, 2, 28), 1_000L, 0L, 0L, "50.00");
        insertRecord(LocalDate.of(2026, 3, 1), 1_000L, 100L, 20L, "400.00");
        insertRecord(otherAdvertiser.id(), searchTypeId, LocalDate.of(2026, 2, 10),
                9_000L, 900L, 90L, "900.00");
        insertRecord(advertiserId, displayTypeId, LocalDate.of(2026, 2, 10),
                8_000L, 800L, 80L, "800.00");
    }

    @Test
    @DisplayName("日趋势保留业务日期并按日期升序返回")
    void groupsByDayWithoutTimezoneShift() {
        List<DeliveryTrendRow> rows = selectTrend(ReportTimeGranularity.DAY);

        assertAll(
                () -> assertEquals(5, rows.size()),
                () -> assertEquals(LocalDate.of(2026, 1, 31), rows.get(0).periodStart()),
                () -> assertEquals(LocalDate.of(2026, 3, 1), rows.get(4).periodStart()),
                () -> assertMetrics(rows.get(0), 1_000L, 100L, 10L,
                        "200.00", "0.1000", "0.1000", "2.00"));
    }

    @Test
    @DisplayName("周趋势以周一为起点并正确合并跨月周")
    void groupsByIsoWeekAcrossMonthBoundary() {
        List<DeliveryTrendRow> rows = selectTrend(ReportTimeGranularity.WEEK);

        assertAll(
                () -> assertEquals(3, rows.size()),
                () -> assertEquals(LocalDate.of(2026, 1, 26), rows.get(0).periodStart()),
                () -> assertMetrics(rows.get(0), 1_500L, 150L, 15L,
                        "300.00", "0.1000", "0.1000", "2.00"),
                () -> assertEquals(LocalDate.of(2026, 2, 23), rows.get(2).periodStart()),
                () -> assertMetrics(rows.get(2), 2_000L, 100L, 20L,
                        "450.00", "0.0500", "0.2000", "4.50"));
    }

    @Test
    @DisplayName("月趋势按自然月汇总并重新计算比率")
    void groupsByCalendarMonthAndRecalculatesRatios() {
        List<DeliveryTrendRow> rows = selectTrend(ReportTimeGranularity.MONTH);

        assertAll(
                () -> assertEquals(3, rows.size()),
                () -> assertEquals(LocalDate.of(2026, 2, 1), rows.get(1).periodStart()),
                () -> assertMetrics(rows.get(1), 3_500L, 150L, 15L,
                        "450.00", "0.0429", "0.1000", "3.00"));
    }

    @Test
    @DisplayName("没有匹配记录时趋势返回空列表")
    void returnsEmptyTrendWhenNoRowsMatch() {
        DeliveryReportCriteria emptyCriteria = new DeliveryReportCriteria(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 31),
                advertiserId,
                "SEARCH");

        List<DeliveryTrendRow> rows = deliveryReportMapper.selectTrend(
                emptyCriteria, ReportTimeGranularity.DAY.sqlValue());

        assertEquals(List.of(), rows);
    }

    private List<DeliveryTrendRow> selectTrend(ReportTimeGranularity granularity) {
        DeliveryReportCriteria criteria = new DeliveryReportCriteria(
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 3, 1),
                advertiserId,
                "SEARCH");
        return deliveryReportMapper.selectTrend(criteria, granularity.sqlValue());
    }

    private void insertRecord(
            LocalDate recordDate,
            long impressions,
            long clicks,
            long conversions,
            String spend) {
        insertRecord(
                advertiserId,
                searchTypeId,
                recordDate,
                impressions,
                clicks,
                conversions,
                spend);
    }

    private void insertRecord(
            Long recordAdvertiserId,
            Long advertisingTypeId,
            LocalDate recordDate,
            long impressions,
            long clicks,
            long conversions,
            String spend) {
        OffsetDateTime now = OffsetDateTime.now();
        AdvertisingDeliveryRecord record = new AdvertisingDeliveryRecord();
        record.setExternalRecordNo("REPORT-TREND-" + UUID.randomUUID());
        record.setAdvertiserId(recordAdvertiserId);
        record.setAdvertisingTypeId(advertisingTypeId);
        record.setRecordDate(recordDate);
        record.setImpressions(impressions);
        record.setClicks(clicks);
        record.setConversions(conversions);
        record.setSpend(new BigDecimal(spend));
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        deliveryRecordMapper.insert(record);
    }

    private void assertMetrics(
            DeliveryTrendRow row,
            long impressions,
            long clicks,
            long conversions,
            String spend,
            String ctr,
            String cvr,
            String cpc) {
        assertAll(
                () -> assertEquals(impressions, row.impressions()),
                () -> assertEquals(clicks, row.clicks()),
                () -> assertEquals(conversions, row.conversions()),
                () -> assertEquals(new BigDecimal(spend), row.spend()),
                () -> assertEquals(new BigDecimal(ctr), row.ctr()),
                () -> assertEquals(new BigDecimal(cvr), row.cvr()),
                () -> assertEquals(new BigDecimal(cpc), row.cpc()));
    }
}
