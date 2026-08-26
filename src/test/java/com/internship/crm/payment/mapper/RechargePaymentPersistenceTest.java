package com.internship.crm.payment.mapper;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.internship.crm.account.entity.AccountTransactionType;
import com.internship.crm.account.entity.AdvertiserAccount;
import com.internship.crm.account.entity.AdvertiserAccountTransaction;
import com.internship.crm.account.mapper.AdvertiserAccountMapper;
import com.internship.crm.account.mapper.AdvertiserAccountTransactionMapper;
import com.internship.crm.advertiser.dto.request.CreateAdvertiserRequest;
import com.internship.crm.advertiser.dto.response.AdvertiserResponse;
import com.internship.crm.advertiser.exception.AdvertiserErrorCode;
import com.internship.crm.advertiser.service.AdvertiserService;
import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.payment.entity.PaymentCallbackStatus;
import com.internship.crm.payment.entity.RechargeOrder;
import com.internship.crm.payment.entity.RechargeOrderStatus;
import com.internship.crm.payment.entity.RechargePaymentCallback;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import java.math.BigDecimal;
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
@DisplayName("充值订单与支付回调数据库结构")
@ExtendWith(ReadableTestResultExtension.class)
class RechargePaymentPersistenceTest {

    private static final String VALID_PAYLOAD_HASH = "a".repeat(64);

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AdvertiserService advertiserService;

    @Autowired
    private AdvertiserAccountMapper accountMapper;

    @Autowired
    private AdvertiserAccountTransactionMapper transactionMapper;

    @Autowired
    private RechargeOrderMapper rechargeOrderMapper;

    @Autowired
    private RechargePaymentCallbackMapper callbackMapper;

    @Test
    @DisplayName("V5 创建充值订单、回调表和资金流水订单关联")
    void v5CreatesRechargeTablesAndTransactionRelation() {
        MigrationInfo migration = Arrays.stream(flyway.info().applied())
                .filter(info -> info.getVersion() != null)
                .filter(info -> "5".equals(info.getVersion().getVersion()))
                .findFirst()
                .orElse(null);
        Long tableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('recharge_orders', 'recharge_payment_callbacks')
                """, Long.class);
        Long rechargeOrderColumnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'advertiser_account_transactions'
                  AND column_name = 'recharge_order_id'
                """, Long.class);

        assertNotNull(migration, "数据库中应当存在 Flyway V5 的迁移记录");
        assertAll(
                () -> assertEquals(MigrationState.SUCCESS, migration.getState()),
                () -> assertEquals(2L, tableCount),
                () -> assertEquals(1L, rechargeOrderColumnCount));
    }

    @Test
    @DisplayName("Mapper 可以持久化订单状态和已处理回调")
    void mappersPersistRechargeOrderAndProcessedCallback() {
        AdvertiserAccount account = createAccount();
        RechargeOrder order = pendingOrder(account.getId());
        rechargeOrderMapper.insert(order);
        OffsetDateTime paidAt = OffsetDateTime.now();
        order.setStatus(RechargeOrderStatus.SUCCESS);
        order.setProviderTransactionNo("PROVIDER-TXN-" + UUID.randomUUID());
        order.setPaidAt(paidAt);
        order.setUpdatedAt(paidAt);
        rechargeOrderMapper.updateById(order);
        RechargePaymentCallback callback = processedCallback(order.getId());
        callbackMapper.insert(callback);

        RechargeOrder reloadedOrder = rechargeOrderMapper.findByOrderNo(order.getOrderNo()).orElseThrow();
        RechargePaymentCallback reloadedCallback = callbackMapper
                .findByProviderEventId(callback.getProviderEventId())
                .orElseThrow();

        assertAll(
                () -> assertEquals(RechargeOrderStatus.SUCCESS, reloadedOrder.getStatus()),
                () -> assertEquals(0, new BigDecimal("250.00").compareTo(reloadedOrder.getAmount())),
                () -> assertEquals(order.getProviderTransactionNo(), reloadedOrder.getProviderTransactionNo()),
                () -> assertNotNull(reloadedOrder.getPaidAt()),
                () -> assertEquals(PaymentCallbackStatus.PROCESSED, reloadedCallback.getCallbackStatus()),
                () -> assertEquals(order.getId(), reloadedCallback.getRechargeOrderId()),
                () -> assertEquals(VALID_PAYLOAD_HASH, reloadedCallback.getPayloadHash()),
                () -> assertNotNull(reloadedCallback.getProcessedAt()));
    }

    @Test
    @DisplayName("充值订单号不能重复")
    void rechargeOrderNumberIsUnique() {
        AdvertiserAccount account = createAccount();
        RechargeOrder first = pendingOrder(account.getId());
        rechargeOrderMapper.insert(first);
        RechargeOrder duplicate = pendingOrder(account.getId());
        duplicate.setOrderNo(first.getOrderNo());

        assertThrows(DataIntegrityViolationException.class, () -> rechargeOrderMapper.insert(duplicate));
    }

    @Test
    @DisplayName("支付平台交易号不能关联多个成功订单")
    void providerTransactionNumberIsUnique() {
        AdvertiserAccount account = createAccount();
        String providerTransactionNo = "PROVIDER-TXN-" + UUID.randomUUID();
        RechargeOrder first = successfulOrder(account.getId(), providerTransactionNo);
        rechargeOrderMapper.insert(first);
        RechargeOrder duplicate = successfulOrder(account.getId(), providerTransactionNo);

        assertThrows(DataIntegrityViolationException.class, () -> rechargeOrderMapper.insert(duplicate));
    }

    @ParameterizedTest(name = "{5}")
    @MethodSource("invalidOrderData")
    @DisplayName("数据库拒绝非法充值订单")
    void databaseRejectsInvalidRechargeOrders(
            String orderNo,
            BigDecimal amount,
            String status,
            String providerTransactionNo,
            OffsetDateTime paidAt,
            String description) {
        AdvertiserAccount account = createAccount();

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO recharge_orders (
                    order_no,
                    advertiser_account_id,
                    amount,
                    status,
                    provider_transaction_no,
                    paid_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, orderNo, account.getId(), amount, status, providerTransactionNo, paidAt), description);
    }

    @Test
    @DisplayName("支付回调事件号不能重复")
    void paymentCallbackEventIdIsUnique() {
        AdvertiserAccount account = createAccount();
        RechargeOrder order = pendingOrder(account.getId());
        rechargeOrderMapper.insert(order);
        RechargePaymentCallback first = receivedCallback(order.getId());
        callbackMapper.insert(first);
        RechargePaymentCallback duplicate = receivedCallback(order.getId());
        duplicate.setProviderEventId(first.getProviderEventId());

        assertThrows(DataIntegrityViolationException.class, () -> callbackMapper.insert(duplicate));
    }

    @ParameterizedTest(name = "{4}")
    @MethodSource("invalidCallbackData")
    @DisplayName("数据库拒绝非法支付回调")
    void databaseRejectsInvalidCallbacks(
            String providerEventId,
            String callbackStatus,
            String payloadHash,
            OffsetDateTime processedAt,
            String description) {
        AdvertiserAccount account = createAccount();
        RechargeOrder order = pendingOrder(account.getId());
        rechargeOrderMapper.insert(order);

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO recharge_payment_callbacks (
                    provider_event_id,
                    recharge_order_id,
                    callback_status,
                    payload_hash,
                    processed_at
                ) VALUES (?, ?, ?, ?, ?)
                """, providerEventId, order.getId(), callbackStatus, payloadHash, processedAt), description);
    }

    @Test
    @DisplayName("同一充值订单最多生成一条充值流水")
    void rechargeOrderCanProduceOnlyOneAccountTransaction() {
        AdvertiserAccount account = createAccount();
        RechargeOrder order = successfulOrder(account.getId(), "PROVIDER-TXN-" + UUID.randomUUID());
        rechargeOrderMapper.insert(order);
        AdvertiserAccountTransaction first = rechargeTransaction(account.getId(), order.getId());
        transactionMapper.insert(first);
        assertEquals(
                order.getId(),
                transactionMapper.selectById(first.getId()).getRechargeOrderId(),
                "Java 实体应正确映射充值订单关联");
        AdvertiserAccountTransaction duplicate = rechargeTransaction(account.getId(), order.getId());

        assertThrows(DataIntegrityViolationException.class, () -> transactionMapper.insert(duplicate));
    }

    @Test
    @DisplayName("V8 拒绝把充值订单记入其他广告主账户的资金流水")
    void rechargeOrderAndAccountTransactionMustBelongToSameAccount() {
        MigrationInfo migration = Arrays.stream(flyway.info().applied())
                .filter(info -> info.getVersion() != null)
                .filter(info -> "8".equals(info.getVersion().getVersion()))
                .findFirst()
                .orElse(null);
        AdvertiserAccount orderAccount = createAccount();
        AdvertiserAccount otherAccount = createAccount();
        RechargeOrder order = successfulOrder(
                orderAccount.getId(), "PROVIDER-TXN-" + UUID.randomUUID());
        rechargeOrderMapper.insert(order);
        AdvertiserAccountTransaction crossAccountTransaction =
                rechargeTransaction(otherAccount.getId(), order.getId());

        assertNotNull(migration, "数据库中应当存在 Flyway V8 的迁移记录");
        assertEquals(MigrationState.SUCCESS, migration.getState());
        assertThrows(
                DataIntegrityViolationException.class,
                () -> transactionMapper.insert(crossAccountTransaction));
    }

    @Test
    @DisplayName("回调历史会阻止充值订单被物理删除")
    void callbackHistoryRestrictsDeletingRechargeOrder() {
        AdvertiserAccount account = createAccount();
        RechargeOrder order = pendingOrder(account.getId());
        rechargeOrderMapper.insert(order);
        callbackMapper.insert(receivedCallback(order.getId()));

        assertThrows(DataIntegrityViolationException.class, () -> rechargeOrderMapper.deleteById(order.getId()));
    }

    @Test
    @DisplayName("充值流水会阻止充值订单被物理删除")
    void accountTransactionRestrictsDeletingRechargeOrder() {
        AdvertiserAccount account = createAccount();
        RechargeOrder order = successfulOrder(account.getId(), "PROVIDER-TXN-" + UUID.randomUUID());
        rechargeOrderMapper.insert(order);
        transactionMapper.insert(rechargeTransaction(account.getId(), order.getId()));

        assertThrows(DataIntegrityViolationException.class, () -> rechargeOrderMapper.deleteById(order.getId()));
    }

    @Test
    @DisplayName("存在充值订单时广告主删除返回明确业务错误并保留账户")
    void advertiserWithRechargeOrderCannotBeDeleted() {
        AdvertiserResponse advertiser = createAdvertiser();
        AdvertiserAccount account = accountMapper.findByAdvertiserId(advertiser.id()).orElseThrow();
        RechargeOrder order = pendingOrder(account.getId());
        rechargeOrderMapper.insert(order);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> advertiserService.delete(advertiser.id()));

        assertAll(
                () -> assertSame(AdvertiserErrorCode.ADVERTISER_HAS_BUSINESS_DATA, exception.errorCode()),
                () -> assertTrue(accountMapper.findByAdvertiserId(advertiser.id()).isPresent()),
                () -> assertNotNull(rechargeOrderMapper.selectById(order.getId())));
    }

    private static Stream<Arguments> invalidOrderData() {
        OffsetDateTime now = OffsetDateTime.now();
        return Stream.of(
                Arguments.of(" ", new BigDecimal("1.00"), "PENDING", null, null, "订单号不能为空白"),
                Arguments.of("ZERO-AMOUNT", new BigDecimal("0.00"), "PENDING", null, null,
                        "充值金额必须大于零"),
                Arguments.of("NEGATIVE-AMOUNT", new BigDecimal("-0.01"), "PENDING", null, null,
                        "充值金额不能为负"),
                Arguments.of("INVALID-STATUS", new BigDecimal("1.00"), "PROCESSING", null, null,
                        "订单状态必须在白名单中"),
                Arguments.of("SUCCESS-WITHOUT-PAID-AT", new BigDecimal("1.00"), "SUCCESS", null, null,
                        "成功订单必须有支付时间"),
                Arguments.of("SUCCESS-WITHOUT-PROVIDER-NO", new BigDecimal("1.00"), "SUCCESS", null, now,
                        "成功订单必须有支付平台交易号"),
                Arguments.of("PENDING-WITH-PAID-AT", new BigDecimal("1.00"), "PENDING", null, now,
                        "未成功订单不能有支付时间"),
                Arguments.of("FAILED-WITH-PROVIDER-NO", new BigDecimal("1.00"), "FAILED", "txn-1", null,
                        "非成功订单不能有支付平台交易号"),
                Arguments.of("BLANK-PROVIDER-NO", new BigDecimal("1.00"), "PENDING", " ", null,
                        "支付平台交易号不能是空白"));
    }

    private static Stream<Arguments> invalidCallbackData() {
        return Stream.of(
                Arguments.of(" ", "RECEIVED", VALID_PAYLOAD_HASH, null,
                        "回调事件号不能为空白"),
                Arguments.of("EVENT-INVALID-STATUS", "IGNORED", VALID_PAYLOAD_HASH, null,
                        "回调状态必须在白名单中"),
                Arguments.of("EVENT-BAD-HASH", "RECEIVED", "short-hash", null,
                        "回调载荷摘要必须是 64 位"),
                Arguments.of("EVENT-NO-PROCESSED-TIME", "PROCESSED", VALID_PAYLOAD_HASH, null,
                        "已处理回调必须有处理时间"));
    }

    private AdvertiserResponse createAdvertiser() {
        return advertiserService.create(new CreateAdvertiserRequest(
                "payment-persistence-" + UUID.randomUUID(),
                null,
                null,
                null,
                null,
                null,
                null,
                null));
    }

    private AdvertiserAccount createAccount() {
        AdvertiserResponse advertiser = createAdvertiser();
        return accountMapper.findByAdvertiserId(advertiser.id()).orElseThrow();
    }

    private RechargeOrder pendingOrder(Long accountId) {
        OffsetDateTime now = OffsetDateTime.now();
        RechargeOrder order = new RechargeOrder();
        order.setOrderNo("RECHARGE-ORDER-" + UUID.randomUUID());
        order.setAdvertiserAccountId(accountId);
        order.setAmount(new BigDecimal("250.00"));
        order.setStatus(RechargeOrderStatus.PENDING);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        return order;
    }

    private RechargeOrder successfulOrder(Long accountId, String providerTransactionNo) {
        RechargeOrder order = pendingOrder(accountId);
        OffsetDateTime paidAt = OffsetDateTime.now();
        order.setStatus(RechargeOrderStatus.SUCCESS);
        order.setProviderTransactionNo(providerTransactionNo);
        order.setPaidAt(paidAt);
        order.setUpdatedAt(paidAt);
        return order;
    }

    private RechargePaymentCallback receivedCallback(Long orderId) {
        RechargePaymentCallback callback = new RechargePaymentCallback();
        callback.setProviderEventId("PROVIDER-EVENT-" + UUID.randomUUID());
        callback.setRechargeOrderId(orderId);
        callback.setCallbackStatus(PaymentCallbackStatus.RECEIVED);
        callback.setPayloadHash(VALID_PAYLOAD_HASH);
        callback.setReceivedAt(OffsetDateTime.now());
        return callback;
    }

    private RechargePaymentCallback processedCallback(Long orderId) {
        RechargePaymentCallback callback = receivedCallback(orderId);
        callback.setCallbackStatus(PaymentCallbackStatus.PROCESSED);
        callback.setProcessedAt(OffsetDateTime.now());
        return callback;
    }

    private AdvertiserAccountTransaction rechargeTransaction(Long accountId, Long orderId) {
        AdvertiserAccountTransaction transaction = new AdvertiserAccountTransaction();
        transaction.setAdvertiserAccountId(accountId);
        transaction.setBusinessNo("RECHARGE-TXN-" + UUID.randomUUID());
        transaction.setTransactionType(AccountTransactionType.RECHARGE);
        transaction.setAmount(new BigDecimal("250.00"));
        transaction.setBalanceAfter(new BigDecimal("250.00"));
        transaction.setRechargeOrderId(orderId);
        transaction.setCreatedAt(OffsetDateTime.now());
        return transaction;
    }
}
