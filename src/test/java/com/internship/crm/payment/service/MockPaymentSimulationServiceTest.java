package com.internship.crm.payment.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.payment.dto.request.SimulateRechargePaymentRequest;
import com.internship.crm.payment.dto.response.RechargeOrderResponse;
import com.internship.crm.payment.entity.MockPaymentOutcome;
import com.internship.crm.payment.entity.RechargeOrder;
import com.internship.crm.payment.entity.RechargeOrderStatus;
import com.internship.crm.payment.exception.PaymentErrorCode;
import com.internship.crm.payment.mapper.RechargeOrderMapper;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("本地模拟支付 Service")
@ExtendWith({MockitoExtension.class, ReadableTestResultExtension.class})
class MockPaymentSimulationServiceTest {

    private static final String ORDER_NO = "RCH-0123456789ABCDEF0123456789ABCDEF";
    private static final String PROVIDER_TRANSACTION_NO =
            "MOCK-TXN-0123456789ABCDEF0123456789ABCDEF";

    @Mock
    private RechargeOrderMapper rechargeOrderMapper;

    @Mock
    private RechargePaymentProcessor paymentProcessor;

    @Mock
    private MockPaymentReferenceGenerator referenceGenerator;

    private MockPaymentSimulationService simulationService;

    @BeforeEach
    void setUp() {
        simulationService = new MockPaymentSimulationService(
                rechargeOrderMapper,
                paymentProcessor,
                referenceGenerator,
                Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("成功模拟锁定订单并写入 SUCCESS 状态")
    void successfulSimulationLocksAndUpdatesOrder() {
        RechargeOrder order = pendingOrder();
        when(rechargeOrderMapper.selectByOrderNoForUpdate(ORDER_NO)).thenReturn(order);
        when(referenceGenerator.nextProviderTransactionNo())
                .thenReturn(PROVIDER_TRANSACTION_NO);
        stubProcessing(order, MockPaymentOutcome.SUCCESS, PROVIDER_TRANSACTION_NO);

        RechargeOrderResponse response = simulationService.simulate(
                "  " + ORDER_NO + "  ",
                request(MockPaymentOutcome.SUCCESS));

        assertAll(
                () -> assertEquals(RechargeOrderStatus.SUCCESS, response.status()),
                () -> assertEquals(PROVIDER_TRANSACTION_NO, response.providerTransactionNo()),
                () -> assertEquals(
                        OffsetDateTime.parse("2026-08-27T00:00:00Z"),
                        response.paidAt()),
                () -> assertEquals(7L, response.advertiserId()));
        verify(paymentProcessor).process(
                eq(order),
                eq(MockPaymentOutcome.SUCCESS),
                eq(PROVIDER_TRANSACTION_NO),
                any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("失败模拟写入 FAILED 且不生成平台交易号")
    void failedSimulationDoesNotGenerateProviderReference() {
        RechargeOrder order = pendingOrder();
        when(rechargeOrderMapper.selectByOrderNoForUpdate(ORDER_NO)).thenReturn(order);
        stubProcessing(order, MockPaymentOutcome.FAILED, null);

        RechargeOrderResponse response = simulationService.simulate(
                ORDER_NO,
                request(MockPaymentOutcome.FAILED));

        assertAll(
                () -> assertEquals(RechargeOrderStatus.FAILED, response.status()),
                () -> assertNull(response.providerTransactionNo()),
                () -> assertNull(response.paidAt()));
        verifyNoInteractions(referenceGenerator);
        verify(paymentProcessor).process(
                eq(order),
                eq(MockPaymentOutcome.FAILED),
                eq(null),
                any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("订单不存在时返回明确错误且不生成平台交易号")
    void missingOrderIsRejected() {
        when(rechargeOrderMapper.selectByOrderNoForUpdate(ORDER_NO)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> simulationService.simulate(
                        ORDER_NO,
                        request(MockPaymentOutcome.SUCCESS)));

        assertSame(PaymentErrorCode.ORDER_NOT_FOUND, exception.errorCode());
        verifyNoInteractions(referenceGenerator, paymentProcessor);
    }

    @Test
    @DisplayName("终态订单拒绝再次模拟且不会覆盖原状态")
    void terminalOrderIsRejected() {
        RechargeOrder order = pendingOrder();
        order.setStatus(RechargeOrderStatus.FAILED);
        when(rechargeOrderMapper.selectByOrderNoForUpdate(ORDER_NO)).thenReturn(order);
        when(referenceGenerator.nextProviderTransactionNo())
                .thenReturn(PROVIDER_TRANSACTION_NO);
        when(paymentProcessor.process(
                        eq(order),
                        eq(MockPaymentOutcome.SUCCESS),
                        eq(PROVIDER_TRANSACTION_NO),
                        any(OffsetDateTime.class)))
                .thenThrow(new BusinessException(PaymentErrorCode.INVALID_STATUS_TRANSITION));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> simulationService.simulate(
                        ORDER_NO,
                        request(MockPaymentOutcome.SUCCESS)));

        assertAll(
                () -> assertSame(
                        PaymentErrorCode.INVALID_STATUS_TRANSITION,
                        exception.errorCode()),
                () -> assertEquals(RechargeOrderStatus.FAILED, order.getStatus()));
    }

    @Test
    @DisplayName("非法订单号在访问数据库前被拒绝")
    void invalidOrderNumberIsRejectedBeforeDatabaseAccess() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> simulationService.simulate(
                        " ",
                        request(MockPaymentOutcome.FAILED)));

        assertSame(PaymentErrorCode.INVALID_ORDER_NO, exception.errorCode());
        verifyNoInteractions(rechargeOrderMapper, paymentProcessor, referenceGenerator);
    }

    @Test
    @DisplayName("含非法字符的订单号在访问数据库前被拒绝")
    void unsafeOrderNumberIsRejectedBeforeDatabaseAccess() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> simulationService.simulate(
                        "RCH-INVALID/ORDER",
                        request(MockPaymentOutcome.FAILED)));

        assertSame(PaymentErrorCode.INVALID_ORDER_NO, exception.errorCode());
        verifyNoInteractions(rechargeOrderMapper, paymentProcessor, referenceGenerator);
    }

    @Test
    @DisplayName("状态更新未影响一行时返回明确冲突且不查询账户")
    void missingUpdateIsRejectedAsConflict() {
        RechargeOrder order = pendingOrder();
        when(rechargeOrderMapper.selectByOrderNoForUpdate(ORDER_NO)).thenReturn(order);
        when(referenceGenerator.nextProviderTransactionNo())
                .thenReturn(PROVIDER_TRANSACTION_NO);
        when(paymentProcessor.process(
                        eq(order),
                        eq(MockPaymentOutcome.SUCCESS),
                        eq(PROVIDER_TRANSACTION_NO),
                        any(OffsetDateTime.class)))
                .thenThrow(new BusinessException(PaymentErrorCode.ORDER_UPDATE_CONFLICT));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> simulationService.simulate(
                        ORDER_NO,
                        request(MockPaymentOutcome.SUCCESS)));

        assertSame(PaymentErrorCode.ORDER_UPDATE_CONFLICT, exception.errorCode());
    }

    private SimulateRechargePaymentRequest request(MockPaymentOutcome outcome) {
        return new SimulateRechargePaymentRequest(outcome);
    }

    private void stubProcessing(
            RechargeOrder order,
            MockPaymentOutcome outcome,
            String providerTransactionNo) {
        when(paymentProcessor.process(
                        eq(order),
                        eq(outcome),
                        eq(providerTransactionNo),
                        any(OffsetDateTime.class)))
                .thenAnswer(invocation -> {
                    new RechargeOrderStateMachine().transition(
                            order,
                            RechargeOrderStatus.valueOf(outcome.name()),
                            providerTransactionNo,
                            invocation.getArgument(3));
                    return 7L;
                });
    }

    private RechargeOrder pendingOrder() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-26T23:00:00Z");
        RechargeOrder order = new RechargeOrder();
        order.setId(11L);
        order.setOrderNo(ORDER_NO);
        order.setAdvertiserAccountId(8L);
        order.setAmount(new BigDecimal("250.00"));
        order.setStatus(RechargeOrderStatus.PENDING);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        return order;
    }
}
