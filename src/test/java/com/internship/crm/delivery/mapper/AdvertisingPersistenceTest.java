package com.internship.crm.delivery.mapper;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.internship.crm.advertiser.dto.request.CreateAdvertiserRequest;
import com.internship.crm.advertiser.dto.response.AdvertiserResponse;
import com.internship.crm.advertiser.service.AdvertiserService;
import com.internship.crm.delivery.entity.AdvertisingDeliveryRecord;
import com.internship.crm.delivery.entity.AdvertisingType;
import com.internship.crm.delivery.entity.AdvertisingTypeStatus;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties =
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@Transactional
@DisplayName("广告投放数据库结构与持久层")
@ExtendWith(ReadableTestResultExtension.class)
class AdvertisingPersistenceTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AdvertiserService advertiserService;

    @Autowired
    private AdvertisingTypeMapper advertisingTypeMapper;

    @Autowired
    private AdvertisingDeliveryRecordMapper deliveryRecordMapper;

    @Test
    @DisplayName("V3 创建两张广告投放表并预置四种广告类型")
    void v3CreatesAdvertisingTablesAndSeedData() {
        MigrationInfo migration = Arrays.stream(flyway.info().applied())
                .filter(info -> info.getVersion() != null)
                .filter(info -> "3".equals(info.getVersion().getVersion()))
                .findFirst()
                .orElse(null);
        Long tableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('advertising_types', 'advertising_delivery_records')
                """, Long.class);
        Long seedCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM advertising_types
                WHERE code IN ('SEARCH', 'DISPLAY', 'VIDEO', 'SOCIAL')
                """, Long.class);

        assertNotNull(migration, "数据库中应当存在 Flyway V3 的迁移记录");
        assertAll(
                () -> assertEquals(MigrationState.SUCCESS, migration.getState()),
                () -> assertEquals(2L, tableCount),
                () -> assertEquals(4L, seedCount));
    }

    @Test
    @DisplayName("Mapper 可以按编码查询类型并完整持久化投放记录")
    void mappersPersistAndReadAdvertisingRecord() {
        AdvertiserResponse advertiser = createAdvertiser();
        AdvertisingType type = advertisingTypeMapper.findByCodeIgnoreCase("search").orElseThrow();
        AdvertisingDeliveryRecord record = validRecord(advertiser.id(), type.getId());

        deliveryRecordMapper.insert(record);

        AdvertisingDeliveryRecord reloaded = deliveryRecordMapper.selectById(record.getId());
        assertAll(
                () -> assertNotNull(record.getId()),
                () -> assertNotNull(reloaded),
                () -> assertEquals(record.getExternalRecordNo(), reloaded.getExternalRecordNo()),
                () -> assertEquals(advertiser.id(), reloaded.getAdvertiserId()),
                () -> assertEquals(type.getId(), reloaded.getAdvertisingTypeId()),
                () -> assertEquals(LocalDate.of(2026, 8, 25), reloaded.getRecordDate()),
                () -> assertEquals(10_000L, reloaded.getImpressions()),
                () -> assertEquals(500L, reloaded.getClicks()),
                () -> assertEquals(30L, reloaded.getConversions()),
                () -> assertEquals(0, new BigDecimal("300.00").compareTo(reloaded.getSpend())),
                () -> assertTrue(deliveryRecordMapper
                        .findByExternalRecordNo(record.getExternalRecordNo())
                        .isPresent()));
    }

    @Test
    @DisplayName("广告类型编码不区分大小写唯一")
    void advertisingTypeCodeIsUniqueIgnoringCase() {
        OffsetDateTime now = OffsetDateTime.now();
        AdvertisingType duplicate = new AdvertisingType();
        duplicate.setCode("search");
        duplicate.setName("重复搜索广告");
        duplicate.setStatus(AdvertisingTypeStatus.ACTIVE);
        duplicate.setCreatedAt(now);
        duplicate.setUpdatedAt(now);

        assertThrows(DataIntegrityViolationException.class,
                () -> advertisingTypeMapper.insert(duplicate));
    }

    @Test
    @DisplayName("外部投放记录号不能重复")
    void externalRecordNumberIsUnique() {
        AdvertiserResponse advertiser = createAdvertiser();
        Long typeId = advertisingTypeMapper.findByCodeIgnoreCase("DISPLAY").orElseThrow().getId();
        AdvertisingDeliveryRecord first = validRecord(advertiser.id(), typeId);
        deliveryRecordMapper.insert(first);
        AdvertisingDeliveryRecord duplicate = validRecord(advertiser.id(), typeId);
        duplicate.setExternalRecordNo(first.getExternalRecordNo());

        assertThrows(DataIntegrityViolationException.class,
                () -> deliveryRecordMapper.insert(duplicate));
    }

    @ParameterizedTest(name = "{4}")
    @MethodSource("invalidMetrics")
    @DisplayName("数据库拒绝非法漏斗指标和花费")
    void databaseRejectsInvalidMetrics(
            long impressions,
            long clicks,
            long conversions,
            BigDecimal spend,
            String description) {
        AdvertiserResponse advertiser = createAdvertiser();
        Long typeId = advertisingTypeMapper.findByCodeIgnoreCase("VIDEO").orElseThrow().getId();
        AdvertisingDeliveryRecord record = validRecord(advertiser.id(), typeId);
        record.setImpressions(impressions);
        record.setClicks(clicks);
        record.setConversions(conversions);
        record.setSpend(spend);

        assertThrows(DataIntegrityViolationException.class,
                () -> deliveryRecordMapper.insert(record), description);
    }

    @Test
    @DisplayName("存在投放历史时数据库禁止删除广告主")
    void deliveryHistoryRestrictsDeletingAdvertiser() {
        AdvertiserResponse advertiser = createAdvertiser();
        Long typeId = advertisingTypeMapper.findByCodeIgnoreCase("SOCIAL").orElseThrow().getId();
        deliveryRecordMapper.insert(validRecord(advertiser.id(), typeId));

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("DELETE FROM advertisers WHERE id = ?", advertiser.id()));
    }

    @Test
    @DisplayName("存在投放历史时数据库禁止删除广告类型")
    void deliveryHistoryRestrictsDeletingAdvertisingType() {
        AdvertiserResponse advertiser = createAdvertiser();
        Long typeId = advertisingTypeMapper.findByCodeIgnoreCase("SOCIAL").orElseThrow().getId();
        deliveryRecordMapper.insert(validRecord(advertiser.id(), typeId));

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("DELETE FROM advertising_types WHERE id = ?", typeId));
    }

    private static Stream<Arguments> invalidMetrics() {
        return Stream.of(
                Arguments.of(-1L, 0L, 0L, new BigDecimal("0.00"), "展示量不能为负"),
                Arguments.of(100L, -1L, 0L, new BigDecimal("0.00"), "点击量不能为负"),
                Arguments.of(100L, 101L, 0L, new BigDecimal("0.00"), "点击量不能超过展示量"),
                Arguments.of(100L, 10L, -1L, new BigDecimal("0.00"), "转化量不能为负"),
                Arguments.of(100L, 10L, 11L, new BigDecimal("0.00"), "转化量不能超过点击量"),
                Arguments.of(100L, 10L, 1L, new BigDecimal("-0.01"), "花费不能为负"));
    }

    private AdvertiserResponse createAdvertiser() {
        return advertiserService.create(new CreateAdvertiserRequest(
                "advertising-persistence-" + UUID.randomUUID(),
                null,
                null,
                null,
                null,
                null,
                null,
                null));
    }

    private AdvertisingDeliveryRecord validRecord(Long advertiserId, Long advertisingTypeId) {
        OffsetDateTime now = OffsetDateTime.now();
        AdvertisingDeliveryRecord record = new AdvertisingDeliveryRecord();
        record.setExternalRecordNo("DELIVERY-" + UUID.randomUUID());
        record.setAdvertiserId(advertiserId);
        record.setAdvertisingTypeId(advertisingTypeId);
        record.setRecordDate(LocalDate.of(2026, 8, 25));
        record.setImpressions(10_000L);
        record.setClicks(500L);
        record.setConversions(30L);
        record.setSpend(new BigDecimal("300.00"));
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        return record;
    }
}
