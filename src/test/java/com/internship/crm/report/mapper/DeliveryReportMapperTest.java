package com.internship.crm.report.mapper;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.internship.crm.advertiser.dto.request.CreateAdvertiserRequest;
import com.internship.crm.advertiser.dto.response.AdvertiserResponse;
import com.internship.crm.advertiser.service.AdvertiserService;
import com.internship.crm.delivery.entity.AdvertisingDeliveryRecord;
import com.internship.crm.delivery.mapper.AdvertisingDeliveryRecordMapper;
import com.internship.crm.delivery.mapper.AdvertisingTypeMapper;
import com.internship.crm.report.dto.response.DeliveryMetricsResponse;
import com.internship.crm.report.model.DeliveryReportCriteria;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
@DisplayName("投放总览报表聚合 SQL")
@ExtendWith(ReadableTestResultExtension.class)
class DeliveryReportMapperTest {

    @Autowired
    private AdvertiserService advertiserService;

    @Autowired
    private AdvertisingTypeMapper advertisingTypeMapper;

    @Autowired
    private AdvertisingDeliveryRecordMapper deliveryRecordMapper;

    @Autowired
    private DeliveryReportMapper deliveryReportMapper;

    private Long firstAdvertiserId;
    private Long secondAdvertiserId;

    @BeforeEach
    void setUp() {
        firstAdvertiserId = createAdvertiser("report-overview-first").id();
        secondAdvertiserId = createAdvertiser("report-overview-second").id();
        Long searchTypeId = advertisingTypeMapper.findByCodeIgnoreCase("SEARCH").orElseThrow().getId();
        Long displayTypeId = advertisingTypeMapper.findByCodeIgnoreCase("DISPLAY").orElseThrow().getId();

        insertRecord(firstAdvertiserId, searchTypeId, LocalDate.of(2026, 8, 1),
                1_000L, 100L, 10L, "200.00");
        insertRecord(firstAdvertiserId, displayTypeId, LocalDate.of(2026, 8, 2),
                500L, 0L, 0L, "50.00");
        insertRecord(secondAdvertiserId, searchTypeId, LocalDate.of(2026, 8, 3),
                2_000L, 200L, 20L, "400.00");
        insertRecord(firstAdvertiserId, searchTypeId, LocalDate.of(2026, 7, 31),
                9_999L, 999L, 99L, "999.00");
    }

    @Test
    @DisplayName("固定数据集的总量和比率与人工计算一致")
    void aggregatesOverviewFromFixedDataset() {
        DeliveryMetricsResponse result = deliveryReportMapper.selectOverview(criteria(null, null));

        assertMetrics(result, 3_500L, 300L, 30L,
                "650.00", "0.0857", "0.1000", "2.17");
    }

    @Test
    @DisplayName("广告主和广告类型筛选只汇总匹配的投放记录")
    void appliesAdvertiserAndTypeFilters() {
        DeliveryMetricsResponse advertiserResult =
                deliveryReportMapper.selectOverview(criteria(firstAdvertiserId, null));
        DeliveryMetricsResponse searchResult =
                deliveryReportMapper.selectOverview(criteria(null, "SEARCH"));
        DeliveryMetricsResponse displayResult =
                deliveryReportMapper.selectOverview(criteria(null, "DISPLAY"));

        assertAll(
                () -> assertMetrics(advertiserResult, 1_500L, 100L, 10L,
                        "250.00", "0.0667", "0.1000", "2.50"),
                () -> assertMetrics(searchResult, 3_000L, 300L, 30L,
                        "600.00", "0.1000", "0.1000", "2.00"),
                () -> assertMetrics(displayResult, 500L, 0L, 0L,
                        "50.00", "0.0000", "0.0000", "0.00"));
    }

    @Test
    @DisplayName("空数据和零分母返回稳定的零指标")
    void returnsZeroMetricsForEmptyDataset() {
        DeliveryMetricsResponse result = deliveryReportMapper.selectOverview(
                new DeliveryReportCriteria(
                        LocalDate.of(2025, 1, 1),
                        LocalDate.of(2025, 1, 31),
                        null,
                        null));

        assertMetrics(result, 0L, 0L, 0L,
                "0.00", "0.0000", "0.0000", "0.00");
    }

    private DeliveryReportCriteria criteria(Long advertiserId, String advertisingTypeCode) {
        return new DeliveryReportCriteria(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                advertiserId,
                advertisingTypeCode);
    }

    private AdvertiserResponse createAdvertiser(String prefix) {
        return advertiserService.create(new CreateAdvertiserRequest(
                prefix + "-" + UUID.randomUUID(),
                null,
                null,
                null,
                null,
                null,
                null,
                null));
    }

    private void insertRecord(
            Long advertiserId,
            Long advertisingTypeId,
            LocalDate recordDate,
            long impressions,
            long clicks,
            long conversions,
            String spend) {
        OffsetDateTime now = OffsetDateTime.now();
        AdvertisingDeliveryRecord record = new AdvertisingDeliveryRecord();
        record.setExternalRecordNo("REPORT-OVERVIEW-" + UUID.randomUUID());
        record.setAdvertiserId(advertiserId);
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
            DeliveryMetricsResponse result,
            long impressions,
            long clicks,
            long conversions,
            String spend,
            String ctr,
            String cvr,
            String cpc) {
        assertAll(
                () -> assertEquals(impressions, result.impressions()),
                () -> assertEquals(clicks, result.clicks()),
                () -> assertEquals(conversions, result.conversions()),
                () -> assertEquals(new BigDecimal(spend), result.spend()),
                () -> assertEquals(new BigDecimal(ctr), result.ctr()),
                () -> assertEquals(new BigDecimal(cvr), result.cvr()),
                () -> assertEquals(new BigDecimal(cpc), result.cpc()));
    }
}
