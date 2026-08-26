package com.internship.crm.delivery.mapper;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.internship.crm.advertiser.dto.request.CreateAdvertiserRequest;
import com.internship.crm.advertiser.dto.response.AdvertiserResponse;
import com.internship.crm.advertiser.service.AdvertiserService;
import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.common.response.PageResponse;
import com.internship.crm.delivery.dto.request.CreateAdvertisingDeliveryRecordRequest;
import com.internship.crm.delivery.dto.request.UpdateAdvertisingDeliveryRecordRequest;
import com.internship.crm.delivery.dto.response.AdvertisingDeliveryRecordResponse;
import com.internship.crm.delivery.entity.AdvertisingDeliveryRecord;
import com.internship.crm.delivery.entity.AdvertisingType;
import com.internship.crm.delivery.entity.AdvertisingTypeStatus;
import com.internship.crm.delivery.exception.DeliveryErrorCode;
import com.internship.crm.delivery.service.AdvertisingDeliveryRecordService;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
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

    @Autowired
    private AdvertisingDeliveryRecordService deliveryRecordService;

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
    @DisplayName("V6 创建投放记录与账户流水的广告主一致性保护")
    void v6CreatesDeliveryAccountConsistencyProtection() {
        MigrationInfo migration = Arrays.stream(flyway.info().applied())
                .filter(info -> info.getVersion() != null)
                .filter(info -> "6".equals(info.getVersion().getVersion()))
                .findFirst()
                .orElseThrow();
        Long triggerCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT trigger_name)
                FROM information_schema.triggers
                WHERE trigger_name IN (
                    'trg_account_transaction_delivery_advertiser',
                    'trg_referenced_delivery_advertiser_change'
                )
                """, Long.class);

        assertAll(
                () -> assertEquals(MigrationState.SUCCESS, migration.getState()),
                () -> assertEquals(2L, triggerCount));
    }

    @Test
    @DisplayName("V7 为账户流水与投放记录一致性校验增加行锁")
    void v7SerializesDeliveryAccountConsistencyCheck() {
        MigrationInfo migration = Arrays.stream(flyway.info().applied())
                .filter(info -> info.getVersion() != null)
                .filter(info -> "7".equals(info.getVersion().getVersion()))
                .findFirst()
                .orElseThrow();
        String functionDefinition = jdbcTemplate.queryForObject("""
                SELECT pg_get_functiondef(
                    'validate_account_transaction_delivery_advertiser()'::regprocedure
                )
                """, String.class);

        assertAll(
                () -> assertEquals(MigrationState.SUCCESS, migration.getState()),
                () -> assertNotNull(functionDefinition),
                () -> assertTrue(functionDefinition.contains("FOR UPDATE")));
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
    @DisplayName("Service 使用类型编码原子录入并返回完整投放记录")
    void serviceCreatesDeliveryRecordAtomically() {
        AdvertiserResponse advertiser = createAdvertiser();
        String externalRecordNo = "DELIVERY-SERVICE-" + UUID.randomUUID();

        AdvertisingDeliveryRecordResponse response = deliveryRecordService.create(
                new CreateAdvertisingDeliveryRecordRequest(
                        "  " + externalRecordNo + "  ",
                        advertiser.id(),
                        " search ",
                        LocalDate.of(2026, 8, 26),
                        2_000L,
                        100L,
                        8L,
                        new BigDecimal("80")));

        AdvertisingDeliveryRecord reloaded = deliveryRecordMapper.selectById(response.id());
        assertAll(
                () -> assertNotNull(response.id()),
                () -> assertEquals(externalRecordNo, response.externalRecordNo()),
                () -> assertEquals(advertiser.name(), response.advertiserName()),
                () -> assertEquals("SEARCH", response.advertisingTypeCode()),
                () -> assertEquals(new BigDecimal("80.00"), response.spend()),
                () -> assertNotNull(reloaded),
                () -> assertEquals(response.id(), reloaded.getId()));
    }

    @Test
    @DisplayName("Service 通过原子插入拒绝重复外部投放记录号")
    void serviceRejectsDuplicateExternalRecordNumber() {
        AdvertiserResponse advertiser = createAdvertiser();
        String externalRecordNo = "DELIVERY-DUPLICATE-" + UUID.randomUUID();
        CreateAdvertisingDeliveryRecordRequest request = new CreateAdvertisingDeliveryRecordRequest(
                externalRecordNo,
                advertiser.id(),
                "DISPLAY",
                LocalDate.of(2026, 8, 26),
                2_000L,
                100L,
                8L,
                new BigDecimal("80.00"));
        deliveryRecordService.create(request);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> deliveryRecordService.create(request));
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM advertising_delivery_records WHERE external_record_no = ?",
                Long.class,
                externalRecordNo);

        assertAll(
                () -> assertEquals(DeliveryErrorCode.EXTERNAL_RECORD_NO_ALREADY_EXISTS,
                        exception.errorCode()),
                () -> assertEquals(1L, count));
    }

    @Test
    @DisplayName("Service 使用 JOIN、组合筛选和稳定排序查询投放记录")
    void serviceQueriesDeliveryDetailsAndFilteredPage() {
        AdvertiserResponse advertiser = createAdvertiser();
        AdvertisingDeliveryRecordResponse oldest = deliveryRecordService.create(
                queryRecordRequest(advertiser.id(), "SEARCH", LocalDate.of(2026, 8, 20)));
        AdvertisingDeliveryRecordResponse middle = deliveryRecordService.create(
                queryRecordRequest(advertiser.id(), "DISPLAY", LocalDate.of(2026, 8, 21)));
        AdvertisingDeliveryRecordResponse newest = deliveryRecordService.create(
                queryRecordRequest(advertiser.id(), "SEARCH", LocalDate.of(2026, 8, 22)));

        PageResponse<AdvertisingDeliveryRecordResponse> page = deliveryRecordService.findAll(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                advertiser.id(),
                null,
                1,
                2);
        PageResponse<AdvertisingDeliveryRecordResponse> searchOnly = deliveryRecordService.findAll(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                advertiser.id(),
                "search",
                1,
                20);
        AdvertisingDeliveryRecordResponse detail = deliveryRecordService.findById(middle.id());

        assertAll(
                () -> assertEquals(List.of(newest.id(), middle.id()),
                        page.items().stream().map(AdvertisingDeliveryRecordResponse::id).toList()),
                () -> assertEquals(3, page.total()),
                () -> assertEquals(2, page.totalPages()),
                () -> assertEquals(List.of(newest.id(), oldest.id()),
                        searchOnly.items().stream()
                                .map(AdvertisingDeliveryRecordResponse::id)
                                .toList()),
                () -> assertEquals("DISPLAY", detail.advertisingTypeCode()),
                () -> assertEquals(advertiser.name(), detail.advertiserName()));
    }

    @Test
    @DisplayName("Service 局部修正持久化新关联和指标且保持外部记录号不变")
    void servicePersistsPartialDeliveryRecordUpdate() {
        AdvertiserResponse originalAdvertiser = createAdvertiser();
        AdvertiserResponse replacementAdvertiser = createAdvertiser();
        AdvertisingDeliveryRecordResponse created = deliveryRecordService.create(
                queryRecordRequest(
                        originalAdvertiser.id(), "SEARCH", LocalDate.of(2026, 8, 20)));

        AdvertisingDeliveryRecordResponse updated = deliveryRecordService.update(
                created.id(),
                new UpdateAdvertisingDeliveryRecordRequest(
                        replacementAdvertiser.id(),
                        " video ",
                        LocalDate.of(2026, 8, 21),
                        20_000L,
                        800L,
                        40L,
                        new BigDecimal("500")));
        AdvertisingDeliveryRecord reloaded = deliveryRecordMapper.selectById(created.id());

        assertAll(
                () -> assertEquals(created.externalRecordNo(), updated.externalRecordNo()),
                () -> assertEquals(replacementAdvertiser.id(), updated.advertiserId()),
                () -> assertEquals(replacementAdvertiser.name(), updated.advertiserName()),
                () -> assertEquals("VIDEO", updated.advertisingTypeCode()),
                () -> assertEquals(LocalDate.of(2026, 8, 21), updated.recordDate()),
                () -> assertEquals(20_000L, updated.impressions()),
                () -> assertEquals(new BigDecimal("500.00"), updated.spend()),
                () -> assertEquals(created.externalRecordNo(), reloaded.getExternalRecordNo()),
                () -> assertEquals(updated.advertisingTypeId(), reloaded.getAdvertisingTypeId()));
    }

    @Test
    @DisplayName("Service 可以物理删除未关联资金流水的投放记录")
    void serviceDeletesUnreferencedDeliveryRecord() {
        AdvertiserResponse advertiser = createAdvertiser();
        AdvertisingDeliveryRecordResponse created = deliveryRecordService.create(
                queryRecordRequest(advertiser.id(), "SEARCH", LocalDate.of(2026, 8, 20)));

        deliveryRecordService.delete(created.id());

        assertNull(deliveryRecordMapper.selectById(created.id()));
    }

    @Test
    @DisplayName("Service 拒绝删除已关联资金流水的投放记录并保留历史")
    void serviceRejectsDeletingReferencedDeliveryRecord() {
        AdvertiserResponse advertiser = createAdvertiser();
        AdvertisingDeliveryRecordResponse created = deliveryRecordService.create(
                queryRecordRequest(advertiser.id(), "SEARCH", LocalDate.of(2026, 8, 20)));
        Long accountId = jdbcTemplate.queryForObject(
                "SELECT id FROM advertiser_accounts WHERE advertiser_id = ?",
                Long.class,
                advertiser.id());
        String businessNo = "CONSUME-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO advertiser_account_transactions (
                    advertiser_account_id,
                    business_no,
                    transaction_type,
                    amount,
                    balance_after,
                    advertising_delivery_record_id,
                    created_at
                ) VALUES (?, ?, 'CONSUMPTION', 1.00, 0.00, ?, CURRENT_TIMESTAMP)
                """, accountId, businessNo, created.id());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> deliveryRecordService.delete(created.id()));
        Long transactionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM advertiser_account_transactions WHERE business_no = ?",
                Long.class,
                businessNo);

        assertAll(
                () -> assertEquals(DeliveryErrorCode.DELIVERY_RECORD_IN_USE, exception.errorCode()),
                () -> assertNotNull(deliveryRecordMapper.selectById(created.id())),
                () -> assertEquals(1L, transactionCount));
    }

    @Test
    @DisplayName("Service 拒绝把已关联资金流水的投放记录换绑给其他广告主")
    void serviceRejectsChangingReferencedRecordAdvertiser() {
        AdvertiserResponse originalAdvertiser = createAdvertiser();
        AdvertiserResponse replacementAdvertiser = createAdvertiser();
        AdvertisingDeliveryRecordResponse created = deliveryRecordService.create(
                queryRecordRequest(originalAdvertiser.id(), "SEARCH", LocalDate.of(2026, 8, 20)));
        insertConsumptionTransaction(originalAdvertiser.id(), created.id());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> deliveryRecordService.update(
                        created.id(),
                        new UpdateAdvertisingDeliveryRecordRequest(
                                replacementAdvertiser.id(), null, null, null, null, null, null)));
        AdvertisingDeliveryRecord reloaded = deliveryRecordMapper.selectById(created.id());

        assertAll(
                () -> assertEquals(
                        DeliveryErrorCode.DELIVERY_RECORD_ADVERTISER_LOCKED,
                        exception.errorCode()),
                () -> assertEquals(originalAdvertiser.id(), reloaded.getAdvertiserId()));
    }

    @Test
    @DisplayName("数据库拒绝账户与投放记录属于不同广告主的资金流水")
    void databaseRejectsMismatchedTransactionAdvertiser() {
        AdvertiserResponse deliveryAdvertiser = createAdvertiser();
        AdvertiserResponse accountAdvertiser = createAdvertiser();
        AdvertisingDeliveryRecordResponse created = deliveryRecordService.create(
                queryRecordRequest(deliveryAdvertiser.id(), "DISPLAY", LocalDate.of(2026, 8, 20)));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertConsumptionTransaction(accountAdvertiser.id(), created.id()));
    }

    @Test
    @DisplayName("数据库拒绝直接修改已关联资金流水投放记录的广告主")
    void databaseRejectsDirectReferencedRecordAdvertiserChange() {
        AdvertiserResponse originalAdvertiser = createAdvertiser();
        AdvertiserResponse replacementAdvertiser = createAdvertiser();
        AdvertisingDeliveryRecordResponse created = deliveryRecordService.create(
                queryRecordRequest(originalAdvertiser.id(), "VIDEO", LocalDate.of(2026, 8, 20)));
        insertConsumptionTransaction(originalAdvertiser.id(), created.id());

        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbcTemplate.update(
                        "UPDATE advertising_delivery_records SET advertiser_id = ? WHERE id = ?",
                        replacementAdvertiser.id(),
                        created.id()));
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
        jdbcTemplate.update(
                "DELETE FROM advertiser_accounts WHERE advertiser_id = ?", advertiser.id());
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

    private void insertConsumptionTransaction(Long advertiserId, Long deliveryRecordId) {
        Long accountId = jdbcTemplate.queryForObject(
                "SELECT id FROM advertiser_accounts WHERE advertiser_id = ?",
                Long.class,
                advertiserId);
        jdbcTemplate.update("""
                INSERT INTO advertiser_account_transactions (
                    advertiser_account_id,
                    business_no,
                    transaction_type,
                    amount,
                    balance_after,
                    advertising_delivery_record_id,
                    created_at
                ) VALUES (?, ?, 'CONSUMPTION', 1.00, 0.00, ?, CURRENT_TIMESTAMP)
                """, accountId, "CONSUME-" + UUID.randomUUID(), deliveryRecordId);
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

    private CreateAdvertisingDeliveryRecordRequest queryRecordRequest(
            Long advertiserId, String advertisingTypeCode, LocalDate recordDate) {
        return new CreateAdvertisingDeliveryRecordRequest(
                "DELIVERY-QUERY-" + UUID.randomUUID(),
                advertiserId,
                advertisingTypeCode,
                recordDate,
                10_000L,
                500L,
                30L,
                new BigDecimal("300.00"));
    }
}
