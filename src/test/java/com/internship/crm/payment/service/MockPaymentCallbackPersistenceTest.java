package com.internship.crm.payment.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.crm.advertiser.dto.request.CreateAdvertiserRequest;
import com.internship.crm.advertiser.dto.response.AdvertiserResponse;
import com.internship.crm.advertiser.service.AdvertiserService;
import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.payment.dto.request.CreateRechargeOrderRequest;
import com.internship.crm.payment.dto.request.MockPaymentCallbackRequest;
import com.internship.crm.payment.dto.response.MockPaymentCallbackResponse;
import com.internship.crm.payment.dto.response.RechargeOrderResponse;
import com.internship.crm.payment.entity.MockPaymentOutcome;
import com.internship.crm.payment.entity.PaymentCallbackStatus;
import com.internship.crm.payment.entity.RechargeOrderStatus;
import com.internship.crm.payment.exception.PaymentErrorCode;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
    "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
    "app.payment.callback-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
    "app.payment.callback-tolerance-seconds=300"
})
@ActiveProfiles("test")
@DisplayName("模拟支付回调 PostgreSQL 验签与审计事务")
@ExtendWith(ReadableTestResultExtension.class)
class MockPaymentCallbackPersistenceTest {

    @Autowired
    private AdvertiserService advertiserService;

    @Autowired
    private RechargeOrderService rechargeOrderService;

    @Autowired
    private MockPaymentCallbackService callbackService;

    @Autowired
    private PaymentCallbackSignatureService signatureService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> createdAdvertiserIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long advertiserId : createdAdvertiserIds.reversed()) {
            jdbcTemplate.update("""
                    DELETE FROM recharge_payment_callbacks
                    WHERE recharge_order_id IN (
                        SELECT recharge_order.id
                        FROM recharge_orders recharge_order
                        JOIN advertiser_accounts account
                          ON account.id = recharge_order.advertiser_account_id
                        WHERE account.advertiser_id = ?
                    )
                    """, advertiserId);
            jdbcTemplate.update("""
                    DELETE FROM advertiser_account_transactions
                    WHERE advertiser_account_id IN (
                        SELECT id FROM advertiser_accounts WHERE advertiser_id = ?
                    )
                    """, advertiserId);
            jdbcTemplate.update("""
                    DELETE FROM recharge_orders
                    WHERE advertiser_account_id IN (
                        SELECT id FROM advertiser_accounts WHERE advertiser_id = ?
                    )
                    """, advertiserId);
            jdbcTemplate.update(
                    "DELETE FROM advertiser_accounts WHERE advertiser_id = ?",
                    advertiserId);
            jdbcTemplate.update("DELETE FROM advertisers WHERE id = ?", advertiserId);
        }
    }

    @Test
    @DisplayName("合法成功回调原子完成订单、余额、流水和 PROCESSED 审计")
    void trustedCallbackAtomicallyCompletesRecharge() throws Exception {
        AdvertiserResponse advertiser = createAdvertiser();
        RechargeOrderResponse order = createOrder(advertiser.id());
        byte[] payload = payload("evt-" + UUID.randomUUID(), order, advertiser.id(), "txn-1");

        MockPaymentCallbackResponse response = receive(payload);

        assertAll(
                () -> assertEquals(PaymentCallbackStatus.PROCESSED, response.callbackStatus()),
                () -> assertFalse(response.duplicate()),
                () -> assertEquals(1L, callbackCount(response.eventId())),
                () -> assertEquals(64, storedPayloadHash(response.eventId()).length()),
                () -> assertEquals(RechargeOrderStatus.SUCCESS,
                        rechargeOrderService.findByOrderNo(order.orderNo()).status()),
                () -> assertEquals(new BigDecimal("250.00"), balance(advertiser.id())),
                () -> assertEquals(1L, transactionCount(advertiser.id())));
    }

    @Test
    @DisplayName("相同 eventId 与相同原始载荷重复投递返回幂等确认")
    void identicalRetryIsIdempotent() throws Exception {
        AdvertiserResponse advertiser = createAdvertiser();
        RechargeOrderResponse order = createOrder(advertiser.id());
        String eventId = "evt-" + UUID.randomUUID();
        byte[] payload = payload(eventId, order, advertiser.id(), "txn-2");

        MockPaymentCallbackResponse first = receive(payload);
        MockPaymentCallbackResponse retry = receive(payload);

        assertAll(
                () -> assertFalse(first.duplicate()),
                () -> assertTrue(retry.duplicate()),
                () -> assertEquals(first.receivedAt(), retry.receivedAt()),
                () -> assertEquals(1L, callbackCount(eventId)));
    }

    @Test
    @DisplayName("相同 eventId 携带不同原始载荷返回事件冲突")
    void reusedEventIdWithDifferentPayloadConflicts() throws Exception {
        AdvertiserResponse advertiser = createAdvertiser();
        RechargeOrderResponse order = createOrder(advertiser.id());
        String eventId = "evt-" + UUID.randomUUID();
        receive(payload(eventId, order, advertiser.id(), "txn-original"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> receive(payload(eventId, order, advertiser.id(), "txn-changed")));

        assertAll(
                () -> assertSame(PaymentErrorCode.CALLBACK_EVENT_CONFLICT, exception.errorCode()),
                () -> assertEquals(1L, callbackCount(eventId)));
    }

    @Test
    @DisplayName("验签成功但广告主不匹配时 REJECTED 审计独立提交")
    void trustedBusinessMismatchPersistsRejectedAudit() throws Exception {
        AdvertiserResponse advertiser = createAdvertiser();
        RechargeOrderResponse order = createOrder(advertiser.id());
        String eventId = "evt-" + UUID.randomUUID();
        byte[] payload = payload(eventId, order, advertiser.id() + 9999, "txn-mismatch");

        BusinessException exception = assertThrows(BusinessException.class, () -> receive(payload));

        assertAll(
                () -> assertSame(PaymentErrorCode.CALLBACK_ADVERTISER_MISMATCH, exception.errorCode()),
                () -> assertEquals("REJECTED", callbackStatus(eventId)),
                () -> assertEquals(
                        PaymentErrorCode.CALLBACK_ADVERTISER_MISMATCH.code(),
                        failureReason(eventId)),
                () -> assertEquals(RechargeOrderStatus.PENDING,
                        rechargeOrderService.findByOrderNo(order.orderNo()).status()));
    }

    @Test
    @DisplayName("无效签名不会解析载荷或占用 eventId")
    void invalidSignatureDoesNotCreateAudit() throws Exception {
        AdvertiserResponse advertiser = createAdvertiser();
        RechargeOrderResponse order = createOrder(advertiser.id());
        String eventId = "evt-" + UUID.randomUUID();
        byte[] payload = payload(eventId, order, advertiser.id(), "txn-bad-signature");
        String timestamp = Long.toString(Instant.now().getEpochSecond());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> callbackService.receive(timestamp, "sha256=" + "0".repeat(64), payload));

        assertAll(
                () -> assertSame(PaymentErrorCode.CALLBACK_SIGNATURE_INVALID, exception.errorCode()),
                () -> assertEquals(0L, callbackCount(eventId)));
    }

    @Test
    @DisplayName("合法失败回调原子写入 FAILED 和 PROCESSED 且不产生充值")
    void failedCallbackCompletesWithoutCredit() throws Exception {
        AdvertiserResponse advertiser = createAdvertiser();
        RechargeOrderResponse order = createOrder(advertiser.id());
        String eventId = "evt-" + UUID.randomUUID();
        byte[] payload = payload(
                eventId,
                order,
                advertiser.id(),
                MockPaymentOutcome.FAILED,
                null);

        MockPaymentCallbackResponse response = receive(payload);

        assertAll(
                () -> assertEquals(PaymentCallbackStatus.PROCESSED, response.callbackStatus()),
                () -> assertEquals(RechargeOrderStatus.FAILED,
                        rechargeOrderService.findByOrderNo(order.orderNo()).status()),
                () -> assertEquals(new BigDecimal("0.00"), balance(advertiser.id())),
                () -> assertEquals(0L, transactionCount(advertiser.id())));
    }

    @Test
    @DisplayName("流水冲突会回滚订单、余额和本次回调审计")
    void ledgerConflictRollsBackWholeRecharge() throws Exception {
        AdvertiserResponse advertiser = createAdvertiser();
        RechargeOrderResponse order = createOrder(advertiser.id());
        String eventId = "evt-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO advertiser_account_transactions (
                    advertiser_account_id, business_no, transaction_type,
                    amount, balance_after, created_at
                ) VALUES (?, ?, 'RECHARGE', 1.00, 0.00, CURRENT_TIMESTAMP)
                """, order.advertiserAccountId(), order.orderNo());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> receive(payload(eventId, order, advertiser.id(), "txn-conflict")));

        assertAll(
                () -> assertSame(PaymentErrorCode.RECHARGE_PROCESSING_CONFLICT, exception.errorCode()),
                () -> assertEquals(0L, callbackCount(eventId)),
                () -> assertEquals(RechargeOrderStatus.PENDING,
                        rechargeOrderService.findByOrderNo(order.orderNo()).status()),
                () -> assertEquals(new BigDecimal("0.00"), balance(advertiser.id())),
                () -> assertEquals(1L, transactionCount(advertiser.id())));
    }

    @Test
    @DisplayName("两个并发相同事件仅创建一条审计且另一请求收到幂等确认")
    void concurrentIdenticalCallbacksCreateOneAudit() throws Exception {
        AdvertiserResponse advertiser = createAdvertiser();
        RechargeOrderResponse order = createOrder(advertiser.id());
        String eventId = "evt-" + UUID.randomUUID();
        byte[] payload = payload(eventId, order, advertiser.id(), "txn-concurrent");
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String signature = signatureService.sign(timestamp, payload);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MockPaymentCallbackResponse> first = executor.submit(
                    () -> receiveAfterStart(start, timestamp, signature, payload));
            Future<MockPaymentCallbackResponse> second = executor.submit(
                    () -> receiveAfterStart(start, timestamp, signature, payload));
            start.countDown();

            List<MockPaymentCallbackResponse> responses = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS));
            assertAll(
                    () -> assertEquals(1L, responses.stream()
                            .filter(MockPaymentCallbackResponse::duplicate)
                            .count()),
                    () -> assertEquals(1L, callbackCount(eventId)),
                    () -> assertEquals(new BigDecimal("250.00"), balance(advertiser.id())),
                    () -> assertEquals(1L, transactionCount(advertiser.id())));
        } finally {
            executor.shutdownNow();
        }
    }

    private MockPaymentCallbackResponse receive(byte[] payload) {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        return callbackService.receive(timestamp, signatureService.sign(timestamp, payload), payload);
    }

    private MockPaymentCallbackResponse receiveAfterStart(
            CountDownLatch start,
            String timestamp,
            String signature,
            byte[] payload) throws InterruptedException {
        start.await(5, TimeUnit.SECONDS);
        return callbackService.receive(timestamp, signature, payload);
    }

    private byte[] payload(
            String eventId,
            RechargeOrderResponse order,
            Long advertiserId,
            String providerTransactionNo) throws Exception {
        return payload(
                eventId,
                order,
                advertiserId,
                MockPaymentOutcome.SUCCESS,
                providerTransactionNo);
    }

    private byte[] payload(
            String eventId,
            RechargeOrderResponse order,
            Long advertiserId,
            MockPaymentOutcome outcome,
            String providerTransactionNo) throws Exception {
        return objectMapper.writeValueAsBytes(new MockPaymentCallbackRequest(
                eventId,
                order.orderNo(),
                advertiserId,
                order.amount(),
                outcome,
                providerTransactionNo));
    }

    private AdvertiserResponse createAdvertiser() {
        AdvertiserResponse advertiser = advertiserService.create(new CreateAdvertiserRequest(
                "callback-payment-" + UUID.randomUUID(),
                null,
                null,
                null,
                null,
                null,
                null,
                null));
        createdAdvertiserIds.add(advertiser.id());
        return advertiser;
    }

    private RechargeOrderResponse createOrder(Long advertiserId) {
        return rechargeOrderService.create(
                new CreateRechargeOrderRequest(advertiserId, new BigDecimal("250.00")));
    }

    private long callbackCount(String eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recharge_payment_callbacks WHERE provider_event_id = ?",
                Long.class,
                eventId);
    }

    private String storedPayloadHash(String eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT payload_hash FROM recharge_payment_callbacks WHERE provider_event_id = ?",
                String.class,
                eventId);
    }

    private String callbackStatus(String eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT callback_status FROM recharge_payment_callbacks WHERE provider_event_id = ?",
                String.class,
                eventId);
    }

    private String failureReason(String eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT failure_reason FROM recharge_payment_callbacks WHERE provider_event_id = ?",
                String.class,
                eventId);
    }

    private BigDecimal balance(Long advertiserId) {
        return jdbcTemplate.queryForObject(
                "SELECT balance FROM advertiser_accounts WHERE advertiser_id = ?",
                BigDecimal.class,
                advertiserId);
    }

    private long transactionCount(Long advertiserId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM advertiser_account_transactions account_transaction
                JOIN advertiser_accounts account
                  ON account.id = account_transaction.advertiser_account_id
                WHERE account.advertiser_id = ?
                """, Long.class, advertiserId);
    }
}
