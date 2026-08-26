package com.internship.crm.payment.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.internship.crm.advertiser.dto.request.CreateAdvertiserRequest;
import com.internship.crm.advertiser.dto.response.AdvertiserResponse;
import com.internship.crm.advertiser.service.AdvertiserService;
import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.payment.dto.request.CreateRechargeOrderRequest;
import com.internship.crm.payment.dto.request.SimulateRechargePaymentRequest;
import com.internship.crm.payment.dto.response.RechargeOrderResponse;
import com.internship.crm.payment.entity.MockPaymentOutcome;
import com.internship.crm.payment.entity.RechargeOrderStatus;
import com.internship.crm.payment.exception.PaymentErrorCode;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import java.math.BigDecimal;
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

@SpringBootTest(properties =
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@ActiveProfiles("test")
@DisplayName("本地模拟支付 PostgreSQL 状态事务")
@ExtendWith(ReadableTestResultExtension.class)
class MockPaymentSimulationPersistenceTest {

    @Autowired
    private AdvertiserService advertiserService;

    @Autowired
    private RechargeOrderService rechargeOrderService;

    @Autowired
    private MockPaymentSimulationService simulationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> createdAdvertiserIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long advertiserId : createdAdvertiserIds.reversed()) {
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
    @DisplayName("成功模拟在同一事务更新订单、余额和唯一充值流水")
    void successfulSimulationAtomicallyCreditsAccount() {
        AdvertiserResponse advertiser = createAdvertiser();
        RechargeOrderResponse order = createOrder(advertiser.id());

        RechargeOrderResponse result = simulationService.simulate(
                order.orderNo(),
                new SimulateRechargePaymentRequest(MockPaymentOutcome.SUCCESS));

        assertAll(
                () -> assertEquals(RechargeOrderStatus.SUCCESS, result.status()),
                () -> assertNotNull(result.providerTransactionNo()),
                () -> assertNotNull(result.paidAt()),
                () -> assertEquals(new BigDecimal("250.00"), balance(advertiser.id())),
                () -> assertEquals(1L, transactionCount(advertiser.id())));
    }

    @Test
    @DisplayName("两个并发模拟请求竞争同一订单时仅一个终态提交")
    void concurrentSimulationsCommitOnlyOneTerminalState() throws Exception {
        AdvertiserResponse advertiser = createAdvertiser();
        RechargeOrderResponse order = createOrder(advertiser.id());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> success = executor.submit(() -> simulateAfterStart(
                    start, order.orderNo(), MockPaymentOutcome.SUCCESS));
            Future<Object> failure = executor.submit(() -> simulateAfterStart(
                    start, order.orderNo(), MockPaymentOutcome.FAILED));
            start.countDown();

            List<Object> results = List.of(
                    success.get(10, TimeUnit.SECONDS),
                    failure.get(10, TimeUnit.SECONDS));
            long successCount = results.stream()
                    .filter(RechargeOrderResponse.class::isInstance)
                    .count();
            long conflictCount = results.stream()
                    .filter(result -> result == PaymentErrorCode.INVALID_STATUS_TRANSITION)
                    .count();
            RechargeOrderResponse persisted = rechargeOrderService.findByOrderNo(order.orderNo());

            assertAll(
                    () -> assertEquals(1L, successCount),
                    () -> assertEquals(1L, conflictCount),
                    () -> assertTrue(
                            persisted.status() == RechargeOrderStatus.SUCCESS
                                    || persisted.status() == RechargeOrderStatus.FAILED),
                    () -> assertEquals(
                            persisted.status() == RechargeOrderStatus.SUCCESS
                                    ? new BigDecimal("250.00")
                                    : new BigDecimal("0.00"),
                            balance(advertiser.id())),
                    () -> assertEquals(
                            persisted.status() == RechargeOrderStatus.SUCCESS ? 1L : 0L,
                            transactionCount(advertiser.id())));
        } finally {
            executor.shutdownNow();
        }
    }

    private Object simulateAfterStart(
            CountDownLatch start,
            String orderNo,
            MockPaymentOutcome outcome) throws InterruptedException {
        start.await(5, TimeUnit.SECONDS);
        try {
            return simulationService.simulate(
                    orderNo,
                    new SimulateRechargePaymentRequest(outcome));
        } catch (BusinessException exception) {
            assertInstanceOf(PaymentErrorCode.class, exception.errorCode());
            return exception.errorCode();
        }
    }

    private AdvertiserResponse createAdvertiser() {
        AdvertiserResponse advertiser = advertiserService.create(new CreateAdvertiserRequest(
                "mock-payment-" + UUID.randomUUID(),
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
