package com.internship.crm.account.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.internship.crm.account.dto.request.CreateAccountConsumptionRequest;
import com.internship.crm.account.dto.response.AdvertiserAccountTransactionResponse;
import com.internship.crm.account.exception.AccountErrorCode;
import com.internship.crm.advertiser.dto.request.CreateAdvertiserRequest;
import com.internship.crm.advertiser.dto.response.AdvertiserResponse;
import com.internship.crm.advertiser.service.AdvertiserService;
import com.internship.crm.common.exception.BusinessException;
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

@SpringBootTest(properties =
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@DisplayName("广告主账户原子消费 PostgreSQL 事务")
@ExtendWith(ReadableTestResultExtension.class)
class AdvertiserAccountConsumptionPersistenceTest {

    @Autowired
    private AdvertiserService advertiserService;

    @Autowired
    private AdvertiserAccountConsumptionService consumptionService;

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
            jdbcTemplate.update(
                    "DELETE FROM advertising_delivery_records WHERE advertiser_id = ?",
                    advertiserId);
            jdbcTemplate.update(
                    "DELETE FROM advertiser_accounts WHERE advertiser_id = ?",
                    advertiserId);
            jdbcTemplate.update("DELETE FROM advertisers WHERE id = ?", advertiserId);
        }
    }

    @Test
    @DisplayName("消费提交后账户余额、流水金额和流水后余额一致")
    void successfulConsumptionCommitsBalanceAndTransaction() {
        AdvertiserResponse advertiser = createAdvertiserWithBalance("100.00");
        String businessNo = businessNo("SUCCESS");

        AdvertiserAccountTransactionResponse response = consumptionService.consume(
                advertiser.id(), request(businessNo, "30.00", null), null);

        assertAll(
                () -> assertEquals(new BigDecimal("70.00"), balance(advertiser.id())),
                () -> assertEquals(new BigDecimal("30.00"), response.amount()),
                () -> assertEquals(new BigDecimal("70.00"), response.balanceAfter()),
                () -> assertEquals(1L, transactionCount(businessNo)));
    }

    @Test
    @DisplayName("余额不足时原子 SQL 不扣款也不生成流水")
    void insufficientBalanceDoesNotChangePersistentState() {
        AdvertiserResponse advertiser = createAdvertiserWithBalance("20.00");
        String businessNo = businessNo("INSUFFICIENT");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> consumptionService.consume(
                        advertiser.id(), request(businessNo, "30.00", null), null));

        assertAll(
                () -> assertSame(AccountErrorCode.INSUFFICIENT_BALANCE, exception.errorCode()),
                () -> assertEquals(new BigDecimal("20.00"), balance(advertiser.id())),
                () -> assertEquals(0L, transactionCount(businessNo)));
    }

    @Test
    @DisplayName("扣款后发现投放记录不存在时整个事务回滚")
    void missingDeliveryRecordRollsBackDebit() {
        AdvertiserResponse advertiser = createAdvertiserWithBalance("100.00");
        String businessNo = businessNo("ROLLBACK");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> consumptionService.consume(
                        advertiser.id(), request(businessNo, "30.00", Long.MAX_VALUE), null));

        assertAll(
                () -> assertSame(AccountErrorCode.DELIVERY_RECORD_NOT_FOUND, exception.errorCode()),
                () -> assertEquals(new BigDecimal("100.00"), balance(advertiser.id())),
                () -> assertEquals(0L, transactionCount(businessNo)));
    }

    @Test
    @DisplayName("重复业务号不会重复扣款或追加第二条流水")
    void duplicateBusinessNumberIsIdempotentlyRejected() {
        AdvertiserResponse advertiser = createAdvertiserWithBalance("100.00");
        String businessNo = businessNo("DUPLICATE");
        consumptionService.consume(
                advertiser.id(), request(businessNo, "30.00", null), null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> consumptionService.consume(
                        advertiser.id(), request(businessNo, "30.00", null), null));

        assertAll(
                () -> assertSame(
                        AccountErrorCode.BUSINESS_NO_ALREADY_EXISTS, exception.errorCode()),
                () -> assertEquals(new BigDecimal("70.00"), balance(advertiser.id())),
                () -> assertEquals(1L, transactionCount(businessNo)));
    }

    @Test
    @DisplayName("两个并发消费竞争同一余额时仅一个成功且余额不会为负")
    void concurrentConsumptionsCannotOverdrawAccount() throws Exception {
        AdvertiserResponse advertiser = createAdvertiserWithBalance("100.00");
        String firstBusinessNo = businessNo("CONCURRENT-A");
        String secondBusinessNo = businessNo("CONCURRENT-B");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = executor.submit(() -> consumeAfterStart(
                    start, advertiser.id(), firstBusinessNo));
            Future<Object> second = executor.submit(() -> consumeAfterStart(
                    start, advertiser.id(), secondBusinessNo));
            start.countDown();

            List<Object> results = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS));
            long successCount = results.stream()
                    .filter(AdvertiserAccountTransactionResponse.class::isInstance)
                    .count();
            long insufficientCount = results.stream()
                    .filter(result -> result == AccountErrorCode.INSUFFICIENT_BALANCE)
                    .count();

            assertAll(
                    () -> assertEquals(1L, successCount),
                    () -> assertEquals(1L, insufficientCount),
                    () -> assertEquals(new BigDecimal("20.00"), balance(advertiser.id())),
                    () -> assertEquals(
                            1L,
                            transactionCount(firstBusinessNo)
                                    + transactionCount(secondBusinessNo)));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("两个相同业务号并发消费时仅扣款一次并明确返回业务号冲突")
    void concurrentDuplicateBusinessNumberDebitsOnlyOnce() throws Exception {
        AdvertiserResponse advertiser = createAdvertiserWithBalance("30.00");
        String duplicateBusinessNo = businessNo("CONCURRENT-DUPLICATE");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = executor.submit(() -> consumeAfterStart(
                    start, advertiser.id(), duplicateBusinessNo, "30.00"));
            Future<Object> second = executor.submit(() -> consumeAfterStart(
                    start, advertiser.id(), duplicateBusinessNo, "30.00"));
            start.countDown();

            List<Object> results = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS));
            long successCount = results.stream()
                    .filter(AdvertiserAccountTransactionResponse.class::isInstance)
                    .count();
            long duplicateCount = results.stream()
                    .filter(result -> result == AccountErrorCode.BUSINESS_NO_ALREADY_EXISTS)
                    .count();

            assertAll(
                    () -> assertEquals(1L, successCount),
                    () -> assertEquals(1L, duplicateCount),
                    () -> assertEquals(new BigDecimal("0.00"), balance(advertiser.id())),
                    () -> assertEquals(1L, transactionCount(duplicateBusinessNo)));
        } finally {
            executor.shutdownNow();
        }
    }

    private Object consumeAfterStart(
            CountDownLatch start,
            Long advertiserId,
            String businessNo) throws InterruptedException {
        return consumeAfterStart(start, advertiserId, businessNo, "80.00");
    }

    private Object consumeAfterStart(
            CountDownLatch start,
            Long advertiserId,
            String businessNo,
            String amount) throws InterruptedException {
        start.await(5, TimeUnit.SECONDS);
        try {
            return consumptionService.consume(
                    advertiserId, request(businessNo, amount, null), null);
        } catch (BusinessException exception) {
            assertInstanceOf(AccountErrorCode.class, exception.errorCode());
            return exception.errorCode();
        }
    }

    private AdvertiserResponse createAdvertiserWithBalance(String initialBalance) {
        AdvertiserResponse advertiser = advertiserService.create(new CreateAdvertiserRequest(
                "account-consumption-" + UUID.randomUUID(),
                null,
                null,
                null,
                null,
                null,
                null,
                null));
        createdAdvertiserIds.add(advertiser.id());
        jdbcTemplate.update(
                "UPDATE advertiser_accounts SET balance = ? WHERE advertiser_id = ?",
                new BigDecimal(initialBalance),
                advertiser.id());
        return advertiser;
    }

    private CreateAccountConsumptionRequest request(
            String businessNo,
            String amount,
            Long deliveryRecordId) {
        return new CreateAccountConsumptionRequest(
                businessNo, new BigDecimal(amount), deliveryRecordId, "集成测试消费");
    }

    private String businessNo(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private BigDecimal balance(Long advertiserId) {
        return jdbcTemplate.queryForObject(
                "SELECT balance FROM advertiser_accounts WHERE advertiser_id = ?",
                BigDecimal.class,
                advertiserId);
    }

    private long transactionCount(String businessNo) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM advertiser_account_transactions WHERE business_no = ?",
                Long.class,
                businessNo);
    }
}
