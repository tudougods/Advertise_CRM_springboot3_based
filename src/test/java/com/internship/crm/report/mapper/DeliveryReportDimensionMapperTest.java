package com.internship.crm.report.mapper;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.internship.crm.advertiser.dto.request.CreateAdvertiserRequest;
import com.internship.crm.advertiser.dto.response.AdvertiserResponse;
import com.internship.crm.advertiser.service.AdvertiserService;
import com.internship.crm.delivery.entity.AdvertisingDeliveryRecord;
import com.internship.crm.delivery.mapper.AdvertisingDeliveryRecordMapper;
import com.internship.crm.delivery.mapper.AdvertisingTypeMapper;
import com.internship.crm.report.model.AdvertiserReportRow;
import com.internship.crm.report.model.AdvertiserReportSortField;
import com.internship.crm.report.model.AdvertisingTypeReportRow;
import com.internship.crm.report.model.DeliveryReportCriteria;
import com.internship.crm.report.model.ReportSortDirection;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties =
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@Transactional
@DisplayName("投放多维报表聚合 SQL")
@ExtendWith(ReadableTestResultExtension.class)
class DeliveryReportDimensionMapperTest {

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
    private Long thirdAdvertiserId;
    private Long searchTypeId;
    private Long displayTypeId;

    @BeforeEach
    void setUp() {
        firstAdvertiserId = createAdvertiser("dimension-first").id();
        secondAdvertiserId = createAdvertiser("dimension-second").id();
        thirdAdvertiserId = createAdvertiser("dimension-third").id();
        searchTypeId = advertisingTypeMapper.findByCodeIgnoreCase("SEARCH").orElseThrow().getId();
        displayTypeId = advertisingTypeMapper.findByCodeIgnoreCase("DISPLAY").orElseThrow().getId();

        insertRecord(firstAdvertiserId, searchTypeId, LocalDate.of(2026, 8, 1),
                1_000L, 100L, 10L, "200.00");
        insertRecord(firstAdvertiserId, displayTypeId, LocalDate.of(2026, 8, 2),
                500L, 50L, 5L, "100.00");
        insertRecord(secondAdvertiserId, searchTypeId, LocalDate.of(2026, 8, 3),
                2_000L, 100L, 20L, "500.00");
        insertRecord(thirdAdvertiserId, displayTypeId, LocalDate.of(2026, 8, 4),
                1_000L, 0L, 0L, "50.00");
        insertRecord(firstAdvertiserId, searchTypeId, LocalDate.of(2026, 7, 31),
                9_000L, 900L, 90L, "900.00");
    }

    @Test
    @DisplayName("广告主汇总分页和总数使用相同过滤范围")
    void advertiserDimensionPaginatesWithMatchingCount() {
        DeliveryReportCriteria criteria = criteria(null, null);

        long total = deliveryReportMapper.countByAdvertiser(criteria);
        List<AdvertiserReportRow> firstPage = deliveryReportMapper.selectByAdvertiser(
                criteria, "SPEND", "DESC", 2, 0);
        List<AdvertiserReportRow> secondPage = deliveryReportMapper.selectByAdvertiser(
                criteria, "SPEND", "DESC", 2, 2);

        assertAll(
                () -> assertEquals(3L, total),
                () -> assertEquals(List.of(secondAdvertiserId, firstAdvertiserId),
                        firstPage.stream().map(AdvertiserReportRow::advertiserId).toList()),
                () -> assertEquals(List.of(thirdAdvertiserId),
                        secondPage.stream().map(AdvertiserReportRow::advertiserId).toList()),
                () -> assertAdvertiserMetrics(firstPage.get(1),
                        1_500L, 150L, 15L, "300.00", "0.1000", "0.1000", "2.00"));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("sortScenarios")
    @DisplayName("广告主汇总仅按白名单指标和方向排序")
    void advertiserDimensionSortsByWhitelistedMetric(
            AdvertiserReportSortField sortField,
            ReportSortDirection direction,
            String expectedAdvertiser) {
        List<AdvertiserReportRow> rows = deliveryReportMapper.selectByAdvertiser(
                criteria(null, null), sortField.name(), direction.name(), 10, 0);

        assertEquals(resolveAdvertiserId(expectedAdvertiser), rows.get(0).advertiserId());
    }

    @Test
    @DisplayName("广告类型筛选同时作用于广告主数据查询和 COUNT")
    void advertiserDataAndCountShareTypeFilter() {
        DeliveryReportCriteria criteria = criteria(null, "SEARCH");

        long total = deliveryReportMapper.countByAdvertiser(criteria);
        List<AdvertiserReportRow> rows = deliveryReportMapper.selectByAdvertiser(
                criteria, "SPEND", "DESC", 10, 0);

        assertAll(
                () -> assertEquals(2L, total),
                () -> assertEquals(List.of(secondAdvertiserId, firstAdvertiserId),
                        rows.stream().map(AdvertiserReportRow::advertiserId).toList()));
    }

    @Test
    @DisplayName("广告类型维度按花费汇总并稳定排序")
    void advertisingTypeDimensionAggregatesMetrics() {
        List<AdvertisingTypeReportRow> rows =
                deliveryReportMapper.selectByAdvertisingType(criteria(null, null));

        assertAll(
                () -> assertEquals(2, rows.size()),
                () -> assertEquals("SEARCH", rows.get(0).advertisingTypeCode()),
                () -> assertTypeMetrics(rows.get(0),
                        3_000L, 200L, 30L, "700.00", "0.0667", "0.1500", "3.50"),
                () -> assertEquals("DISPLAY", rows.get(1).advertisingTypeCode()),
                () -> assertTypeMetrics(rows.get(1),
                        1_500L, 50L, 5L, "150.00", "0.0333", "0.1000", "3.00"));
    }

    @Test
    @DisplayName("广告主筛选只返回该广告主涉及的广告类型指标")
    void advertisingTypeDimensionAppliesAdvertiserFilter() {
        List<AdvertisingTypeReportRow> rows = deliveryReportMapper.selectByAdvertisingType(
                criteria(firstAdvertiserId, null));

        assertAll(
                () -> assertEquals(2, rows.size()),
                () -> assertEquals("SEARCH", rows.get(0).advertisingTypeCode()),
                () -> assertEquals(new BigDecimal("200.00"), rows.get(0).spend()),
                () -> assertEquals("DISPLAY", rows.get(1).advertisingTypeCode()),
                () -> assertEquals(new BigDecimal("100.00"), rows.get(1).spend()));
    }

    @Test
    @DisplayName("无匹配记录时分组报表返回零总数和空列表")
    void dimensionsReturnEmptyResultsWhenNoRowsMatch() {
        DeliveryReportCriteria criteria = new DeliveryReportCriteria(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 31),
                null,
                null);

        assertAll(
                () -> assertEquals(0L, deliveryReportMapper.countByAdvertiser(criteria)),
                () -> assertEquals(List.of(), deliveryReportMapper.selectByAdvertiser(
                        criteria, "SPEND", "DESC", 10, 0)),
                () -> assertEquals(List.of(),
                        deliveryReportMapper.selectByAdvertisingType(criteria)));
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
                null, null, null, null, null, null, null));
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
        record.setExternalRecordNo("REPORT-DIMENSION-" + UUID.randomUUID());
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

    private Long resolveAdvertiserId(String advertiser) {
        return switch (advertiser) {
            case "FIRST" -> firstAdvertiserId;
            case "SECOND" -> secondAdvertiserId;
            case "THIRD" -> thirdAdvertiserId;
            default -> throw new IllegalArgumentException("unknown advertiser: " + advertiser);
        };
    }

    private void assertAdvertiserMetrics(
            AdvertiserReportRow row,
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

    private void assertTypeMetrics(
            AdvertisingTypeReportRow row,
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

    private static Stream<Arguments> sortScenarios() {
        return Stream.of(
                Arguments.of(AdvertiserReportSortField.IMPRESSIONS, ReportSortDirection.DESC, "SECOND"),
                Arguments.of(AdvertiserReportSortField.CLICKS, ReportSortDirection.DESC, "FIRST"),
                Arguments.of(AdvertiserReportSortField.CONVERSIONS, ReportSortDirection.DESC, "SECOND"),
                Arguments.of(AdvertiserReportSortField.SPEND, ReportSortDirection.DESC, "SECOND"),
                Arguments.of(AdvertiserReportSortField.CTR, ReportSortDirection.ASC, "THIRD"),
                Arguments.of(AdvertiserReportSortField.CVR, ReportSortDirection.DESC, "SECOND"),
                Arguments.of(AdvertiserReportSortField.CPC, ReportSortDirection.DESC, "SECOND"));
    }
}
