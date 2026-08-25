package com.internship.crm.account.mapper;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.internship.crm.account.entity.AccountTransactionType;
import com.internship.crm.account.entity.AdvertiserAccount;
import com.internship.crm.account.entity.AdvertiserAccountTransaction;
import com.internship.crm.advertiser.dto.request.CreateAdvertiserRequest;
import com.internship.crm.advertiser.dto.response.AdvertiserResponse;
import com.internship.crm.advertiser.exception.AdvertiserErrorCode;
import com.internship.crm.advertiser.mapper.AdvertiserMapper;
import com.internship.crm.advertiser.service.AdvertiserService;
import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.delivery.entity.AdvertisingDeliveryRecord;
import com.internship.crm.delivery.mapper.AdvertisingDeliveryRecordMapper;
import com.internship.crm.delivery.mapper.AdvertisingTypeMapper;
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
@DisplayName("广告主账户数据库结构与生命周期")
@ExtendWith(ReadableTestResultExtension.class)
class AdvertiserAccountPersistenceTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AdvertiserService advertiserService;

    @Autowired
    private AdvertiserMapper advertiserMapper;

    @Autowired
    private AdvertiserAccountMapper accountMapper;

    @Autowired
    private AdvertiserAccountTransactionMapper transactionMapper;

    @Autowired
    private AdvertisingTypeMapper advertisingTypeMapper;

    @Autowired
    private AdvertisingDeliveryRecordMapper deliveryRecordMapper;

    @Test
    @DisplayName("V4 创建账户和流水表并为全部已有广告主补建账户")
    void v4CreatesAccountTablesAndBackfillsAdvertisers() {
        MigrationInfo migration = Arrays.stream(flyway.info().applied())
                .filter(info -> info.getVersion() != null)
                .filter(info -> "4".equals(info.getVersion().getVersion()))
                .findFirst()
                .orElse(null);
        Long tableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('advertiser_accounts', 'advertiser_account_transactions')
                """, Long.class);
        Long advertisersWithoutAccount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM advertisers a
                LEFT JOIN advertiser_accounts aa ON aa.advertiser_id = a.id
                WHERE aa.id IS NULL
                """, Long.class);

        assertNotNull(migration, "数据库中应当存在 Flyway V4 的迁移记录");
        assertAll(
                () -> assertEquals(MigrationState.SUCCESS, migration.getState()),
                () -> assertEquals(2L, tableCount),
                () -> assertEquals(0L, advertisersWithoutAccount));
    }

    @Test
    @DisplayName("创建广告主时会在同一业务操作中创建零余额账户")
    void creatingAdvertiserCreatesZeroBalanceAccount() {
        AdvertiserResponse advertiser = createAdvertiser();

        AdvertiserAccount account = accountMapper.findByAdvertiserId(advertiser.id()).orElseThrow();

        assertAll(
                () -> assertNotNull(account.getId()),
                () -> assertEquals(advertiser.id(), account.getAdvertiserId()),
                () -> assertEquals(0, new BigDecimal("0.00").compareTo(account.getBalance())),
                () -> assertNotNull(account.getCreatedAt()),
                () -> assertNotNull(account.getUpdatedAt()));
    }

    @Test
    @DisplayName("一个广告主只能拥有一个账户")
    void advertiserCanOnlyHaveOneAccount() {
        AdvertiserResponse advertiser = createAdvertiser();
        OffsetDateTime now = OffsetDateTime.now();
        AdvertiserAccount duplicate = new AdvertiserAccount();
        duplicate.setAdvertiserId(advertiser.id());
        duplicate.setBalance(new BigDecimal("0.00"));
        duplicate.setCreatedAt(now);
        duplicate.setUpdatedAt(now);

        assertThrows(DataIntegrityViolationException.class, () -> accountMapper.insert(duplicate));
    }

    @Test
    @DisplayName("账户余额不能为负")
    void accountBalanceCannotBeNegative() {
        AdvertiserResponse advertiser = createAdvertiser();
        AdvertiserAccount account = accountMapper.findByAdvertiserId(advertiser.id()).orElseThrow();

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "UPDATE advertiser_accounts SET balance = -0.01 WHERE id = ?", account.getId()));
    }

    @Test
    @DisplayName("Mapper 可以完整持久化并按业务号查询资金流水")
    void mapperPersistsAndFindsAccountTransaction() {
        AdvertiserResponse advertiser = createAdvertiser();
        AdvertiserAccount account = accountMapper.findByAdvertiserId(advertiser.id()).orElseThrow();
        account.setBalance(new BigDecimal("100.00"));
        account.setUpdatedAt(OffsetDateTime.now());
        accountMapper.updateById(account);
        AdvertiserAccountTransaction transaction = validTransaction(account.getId());

        transactionMapper.insert(transaction);

        AdvertiserAccountTransaction reloaded = transactionMapper
                .findByBusinessNo(transaction.getBusinessNo())
                .orElseThrow();
        assertAll(
                () -> assertNotNull(reloaded.getId()),
                () -> assertEquals(account.getId(), reloaded.getAdvertiserAccountId()),
                () -> assertEquals(AccountTransactionType.RECHARGE, reloaded.getTransactionType()),
                () -> assertEquals(0, new BigDecimal("100.00").compareTo(reloaded.getAmount())),
                () -> assertEquals(0, new BigDecimal("100.00").compareTo(reloaded.getBalanceAfter())),
                () -> assertEquals("测试充值", reloaded.getRemark()),
                () -> assertTrue(transactionMapper.existsByAdvertiserAccountId(account.getId())));
    }

    @Test
    @DisplayName("资金流水业务号不能重复")
    void transactionBusinessNumberIsUnique() {
        AdvertiserResponse advertiser = createAdvertiser();
        Long accountId = accountMapper.findByAdvertiserId(advertiser.id()).orElseThrow().getId();
        AdvertiserAccountTransaction first = validTransaction(accountId);
        transactionMapper.insert(first);
        AdvertiserAccountTransaction duplicate = validTransaction(accountId);
        duplicate.setBusinessNo(first.getBusinessNo());

        assertThrows(DataIntegrityViolationException.class, () -> transactionMapper.insert(duplicate));
    }

    @ParameterizedTest(name = "{4}")
    @MethodSource("invalidTransactionData")
    @DisplayName("数据库拒绝非法资金流水")
    void databaseRejectsInvalidTransactions(
            String businessNo,
            String transactionType,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String description) {
        AdvertiserResponse advertiser = createAdvertiser();
        Long accountId = accountMapper.findByAdvertiserId(advertiser.id()).orElseThrow().getId();

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO advertiser_account_transactions (
                    advertiser_account_id,
                    business_no,
                    transaction_type,
                    amount,
                    balance_after
                ) VALUES (?, ?, ?, ?, ?)
                """, accountId, businessNo, transactionType, amount, balanceAfter), description);
    }

    @Test
    @DisplayName("无业务历史的广告主和空账户可以一起删除")
    void advertiserWithoutBusinessHistoryCanBeDeletedWithEmptyAccount() {
        AdvertiserResponse advertiser = createAdvertiser();
        Long accountId = accountMapper.findByAdvertiserId(advertiser.id()).orElseThrow().getId();

        advertiserService.delete(advertiser.id());

        assertAll(
                () -> assertNull(advertiserMapper.selectById(advertiser.id())),
                () -> assertNull(accountMapper.selectById(accountId)));
    }

    @Test
    @DisplayName("存在资金流水时广告主删除返回明确业务错误并保留数据")
    void advertiserWithTransactionCannotBeDeleted() {
        AdvertiserResponse advertiser = createAdvertiser();
        AdvertiserAccount account = accountMapper.findByAdvertiserId(advertiser.id()).orElseThrow();
        transactionMapper.insert(validTransaction(account.getId()));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> advertiserService.delete(advertiser.id()));

        assertAll(
                () -> assertSame(AdvertiserErrorCode.ADVERTISER_HAS_BUSINESS_DATA, exception.errorCode()),
                () -> assertNotNull(advertiserMapper.selectById(advertiser.id())),
                () -> assertNotNull(accountMapper.selectById(account.getId())));
    }

    @Test
    @DisplayName("存在投放记录时广告主删除返回明确业务错误并保留数据")
    void advertiserWithDeliveryRecordCannotBeDeleted() {
        AdvertiserResponse advertiser = createAdvertiser();
        Long typeId = advertisingTypeMapper.findByCodeIgnoreCase("SEARCH").orElseThrow().getId();
        deliveryRecordMapper.insert(validDeliveryRecord(advertiser.id(), typeId));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> advertiserService.delete(advertiser.id()));

        assertAll(
                () -> assertSame(AdvertiserErrorCode.ADVERTISER_HAS_BUSINESS_DATA, exception.errorCode()),
                () -> assertNotNull(advertiserMapper.selectById(advertiser.id())),
                () -> assertTrue(accountMapper.findByAdvertiserId(advertiser.id()).isPresent()));
    }

    private static Stream<Arguments> invalidTransactionData() {
        return Stream.of(
                Arguments.of(" ", "RECHARGE", new BigDecimal("1.00"), new BigDecimal("1.00"),
                        "业务号不能为空白"),
                Arguments.of("INVALID-TYPE", "REFUND", new BigDecimal("1.00"), new BigDecimal("1.00"),
                        "流水类型必须是充值或消费"),
                Arguments.of("ZERO-AMOUNT", "RECHARGE", new BigDecimal("0.00"), new BigDecimal("0.00"),
                        "流水金额必须大于零"),
                Arguments.of("NEGATIVE-AMOUNT", "CONSUMPTION", new BigDecimal("-0.01"),
                        new BigDecimal("0.00"), "流水金额不能为负"),
                Arguments.of("NEGATIVE-BALANCE", "CONSUMPTION", new BigDecimal("1.00"),
                        new BigDecimal("-0.01"), "变更后余额不能为负"));
    }

    private AdvertiserResponse createAdvertiser() {
        return advertiserService.create(new CreateAdvertiserRequest(
                "account-persistence-" + UUID.randomUUID(),
                null,
                null,
                null,
                null,
                null,
                null,
                null));
    }

    private AdvertiserAccountTransaction validTransaction(Long accountId) {
        AdvertiserAccountTransaction transaction = new AdvertiserAccountTransaction();
        transaction.setAdvertiserAccountId(accountId);
        transaction.setBusinessNo("RECHARGE-" + UUID.randomUUID());
        transaction.setTransactionType(AccountTransactionType.RECHARGE);
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setBalanceAfter(new BigDecimal("100.00"));
        transaction.setRemark("测试充值");
        transaction.setCreatedAt(OffsetDateTime.now());
        return transaction;
    }

    private AdvertisingDeliveryRecord validDeliveryRecord(Long advertiserId, Long advertisingTypeId) {
        OffsetDateTime now = OffsetDateTime.now();
        AdvertisingDeliveryRecord record = new AdvertisingDeliveryRecord();
        record.setExternalRecordNo("DELIVERY-" + UUID.randomUUID());
        record.setAdvertiserId(advertiserId);
        record.setAdvertisingTypeId(advertisingTypeId);
        record.setRecordDate(LocalDate.of(2026, 8, 26));
        record.setImpressions(100L);
        record.setClicks(10L);
        record.setConversions(1L);
        record.setSpend(new BigDecimal("10.00"));
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        return record;
    }
}
