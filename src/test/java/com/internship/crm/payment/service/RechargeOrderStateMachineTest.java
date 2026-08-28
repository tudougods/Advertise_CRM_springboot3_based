package com.internship.crm.payment.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.payment.entity.RechargeOrder;
import com.internship.crm.payment.entity.RechargeOrderStatus;
import com.internship.crm.payment.exception.PaymentErrorCode;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("充值订单状态机")
@ExtendWith(ReadableTestResultExtension.class)
class RechargeOrderStateMachineTest {

    private static final OffsetDateTime TRANSITION_TIME =
            OffsetDateTime.parse("2026-08-27T00:00:00.123456789Z");

    private final RechargeOrderStateMachine stateMachine = new RechargeOrderStateMachine();

    @Test
    @DisplayName("PENDING 可以迁移到 SUCCESS 并记录平台交易号和支付时间")
    void pendingCanTransitionToSuccess() {
        RechargeOrder order = order(RechargeOrderStatus.PENDING);

        stateMachine.transition(
                order,
                RechargeOrderStatus.SUCCESS,
                "  MOCK-TXN-001  ",
                TRANSITION_TIME);

        OffsetDateTime expectedTime = TRANSITION_TIME.truncatedTo(ChronoUnit.MICROS);
        assertAll(
                () -> assertEquals(RechargeOrderStatus.SUCCESS, order.getStatus()),
                () -> assertEquals("MOCK-TXN-001", order.getProviderTransactionNo()),
                () -> assertEquals(expectedTime, order.getPaidAt()),
                () -> assertEquals(expectedTime, order.getUpdatedAt()));
    }

    @Test
    @DisplayName("PENDING 可以迁移到 FAILED 且不保留支付成功字段")
    void pendingCanTransitionToFailed() {
        RechargeOrder order = order(RechargeOrderStatus.PENDING);

        stateMachine.transition(
                order,
                RechargeOrderStatus.FAILED,
                "IGNORED",
                TRANSITION_TIME);

        assertAll(
                () -> assertEquals(RechargeOrderStatus.FAILED, order.getStatus()),
                () -> assertNull(order.getProviderTransactionNo()),
                () -> assertNull(order.getPaidAt()),
                () -> assertEquals(
                        TRANSITION_TIME.truncatedTo(ChronoUnit.MICROS),
                        order.getUpdatedAt()));
    }

    @Test
    @DisplayName("PENDING 可以迁移到 CLOSED")
    void pendingCanTransitionToClosed() {
        RechargeOrder order = order(RechargeOrderStatus.PENDING);

        stateMachine.transition(
                order,
                RechargeOrderStatus.CLOSED,
                null,
                TRANSITION_TIME);

        assertEquals(RechargeOrderStatus.CLOSED, order.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = RechargeOrderStatus.class, names = {"SUCCESS", "FAILED", "CLOSED"})
    @DisplayName("终态订单不能再次迁移")
    void terminalOrderCannotTransition(RechargeOrderStatus currentStatus) {
        RechargeOrder order = order(currentStatus);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> stateMachine.transition(
                        order,
                        RechargeOrderStatus.SUCCESS,
                        "MOCK-TXN-002",
                        TRANSITION_TIME));

        assertSame(PaymentErrorCode.INVALID_STATUS_TRANSITION, exception.errorCode());
        assertEquals(currentStatus, order.getStatus());
    }

    @Test
    @DisplayName("PENDING 不能迁移回 PENDING")
    void pendingCannotTransitionToPending() {
        RechargeOrder order = order(RechargeOrderStatus.PENDING);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> stateMachine.transition(
                        order,
                        RechargeOrderStatus.PENDING,
                        null,
                        TRANSITION_TIME));

        assertSame(PaymentErrorCode.INVALID_STATUS_TRANSITION, exception.errorCode());
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("invalidProviderTransactionNumbers")
    @DisplayName("SUCCESS 必须具有合法的平台交易号")
    void successRequiresValidProviderTransactionNumber(
            String providerTransactionNo,
            String description) {
        RechargeOrder order = order(RechargeOrderStatus.PENDING);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> stateMachine.transition(
                        order,
                        RechargeOrderStatus.SUCCESS,
                        providerTransactionNo,
                        TRANSITION_TIME),
                description);

        assertAll(
                () -> assertSame(
                        PaymentErrorCode.INVALID_PROVIDER_TRANSACTION_NO,
                        exception.errorCode()),
                () -> assertEquals(RechargeOrderStatus.PENDING, order.getStatus()),
                () -> assertNull(order.getPaidAt()));
    }

    private static Stream<Arguments> invalidProviderTransactionNumbers() {
        return Stream.of(
                Arguments.of(null, "平台交易号不能为空"),
                Arguments.of("   ", "平台交易号不能为空白"),
                Arguments.of("X".repeat(101), "平台交易号不能超过数据库字段长度"));
    }

    private RechargeOrder order(RechargeOrderStatus status) {
        RechargeOrder order = new RechargeOrder();
        order.setId(11L);
        order.setStatus(status);
        return order;
    }
}
