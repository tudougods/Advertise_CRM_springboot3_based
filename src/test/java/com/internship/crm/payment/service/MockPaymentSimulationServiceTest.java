package com.internship.crm.payment.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.internship.crm.account.entity.AdvertiserAccount;
import com.internship.crm.account.mapper.AdvertiserAccountMapper;
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
    private AdvertiserAccountMapper accountMapper;

    @Mock
    private MockPaymentReferenceGenerator referenceGenerator;

    private MockPaymentSimulationService simulationService;

    @BeforeEach
    void setUp() {
        simulationService = new MockPaymentSimulationService(
                rechargeOrderMapper,
                accountMapper,
                new RechargeOrderStateMachine(),
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
        when(accountMapper.selectById(8L)).thenReturn(account());
        when(rechargeOrderMapper.updateById(order)).thenReturn(1);

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
        verify(rechargeOrderMapper).updateById(order);
    }

    @Test
    @DisplayName("失败模拟写入 FAILED 且不生成平台交易号")
    void failedSimulationDoesNotGenerateProviderReference() {
        RechargeOrder order = pendingOrder();
        when(rechargeOrderMapper.selectByOrderNoForUpdate(ORDER_NO)).thenReturn(order);
        when(accountMapper.selectById(8L)).thenReturn(account());
        when(rechargeOrderMapper.updateById(order)).thenReturn(1);

        RechargeOrderResponse response = simulationService.simulate(
                ORDER_NO,
                request(MockPaymentOutcome.FAILED));

        assertAll(
                () -> assertEquals(RechargeOrderStatus.FAILED, response.status()),
                () -> assertNull(response.providerTransactionNo()),
                () -> assertNull(response.paidAt()));
        verifyNoInteractions(referenceGenerator);
        verify(rechargeOrderMapper).updateById(order);
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
        verifyNoInteractions(referenceGenerator, accountMapper);
        verify(rechargeOrderMapper, never()).updateById(any(RechargeOrder.class));
    }

    @Test
    @DisplayName("终态订单拒绝再次模拟且不会覆盖原状态")
    void terminalOrderIsRejected() {
        RechargeOrder order = pendingOrder();
        order.setStatus(RechargeOrderStatus.FAILED);
        when(rechargeOrderMapper.selectByOrderNoForUpdate(ORDER_NO)).thenReturn(order);
        when(referenceGenerator.nextProviderTransactionNo())
                .thenReturn(PROVIDER_TRANSACTION_NO);

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
        verify(rechargeOrderMapper, never()).updateById(any(RechargeOrder.class));
        verifyNoInteractions(accountMapper);
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
        verifyNoInteractions(rechargeOrderMapper, accountMapper, referenceGenerator);
    }

    @Test
    @DisplayName("状态更新未影响一行时返回明确冲突且不查询账户")
    void missingUpdateIsRejectedAsConflict() {
        RechargeOrder order = pendingOrder();
        when(rechargeOrderMapper.selectByOrderNoForUpdate(ORDER_NO)).thenReturn(order);
        when(referenceGenerator.nextProviderTransactionNo())
                .thenReturn(PROVIDER_TRANSACTION_NO);
        when(rechargeOrderMapper.updateById(order)).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> simulationService.simulate(
                        ORDER_NO,
                        request(MockPaymentOutcome.SUCCESS)));

        assertSame(PaymentErrorCode.ORDER_UPDATE_CONFLICT, exception.errorCode());
        verifyNoInteractions(accountMapper);
    }

    private SimulateRechargePaymentRequest request(MockPaymentOutcome outcome) {
        return new SimulateRechargePaymentRequest(outcome);
    }

    private AdvertiserAccount account() {
        AdvertiserAccount account = new AdvertiserAccount();
        account.setId(8L);
        account.setAdvertiserId(7L);
        return account;
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
