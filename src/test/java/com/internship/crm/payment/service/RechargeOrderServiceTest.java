package com.internship.crm.payment.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.internship.crm.account.entity.AdvertiserAccount;
import com.internship.crm.account.mapper.AdvertiserAccountMapper;
import com.internship.crm.advertiser.entity.Advertiser;
import com.internship.crm.advertiser.mapper.AdvertiserMapper;
import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.payment.dto.request.CreateRechargeOrderRequest;
import com.internship.crm.payment.dto.response.RechargeOrderResponse;
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
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("充值订单创建与查询 Service 业务规则")
@ExtendWith({MockitoExtension.class, ReadableTestResultExtension.class})
class RechargeOrderServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-26T12:00:00Z");
    private static final String ORDER_NO = "RCH-0123456789ABCDEF0123456789ABCDEF";

    @Mock
    private RechargeOrderMapper rechargeOrderMapper;

    @Mock
    private AdvertiserAccountMapper accountMapper;

    @Mock
    private AdvertiserMapper advertiserMapper;

    @Mock
    private RechargeOrderNumberGenerator orderNumberGenerator;

    private RechargeOrderService rechargeOrderService;

    @BeforeEach
    void setUp() {
        rechargeOrderService = new RechargeOrderService(
                rechargeOrderMapper,
                accountMapper,
                advertiserMapper,
                orderNumberGenerator,
                Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("创建订单时服务端生成订单号并持久化为 PENDING")
    void createsPendingOrderWithServerGeneratedNumber() {
        AdvertiserAccount account = account(8L, 7L);
        when(accountMapper.findByAdvertiserId(7L)).thenReturn(Optional.of(account));
        when(orderNumberGenerator.nextOrderNo()).thenReturn(ORDER_NO);
        doAnswer(invocation -> {
            RechargeOrder order = invocation.getArgument(0);
            order.setId(11L);
            return 1;
        }).when(rechargeOrderMapper).insert(any(RechargeOrder.class));

        RechargeOrderResponse response = rechargeOrderService.create(
                new CreateRechargeOrderRequest(7L, new BigDecimal("123.4")));

        ArgumentCaptor<RechargeOrder> orderCaptor = ArgumentCaptor.forClass(RechargeOrder.class);
        verify(rechargeOrderMapper).insert(orderCaptor.capture());
        RechargeOrder persisted = orderCaptor.getValue();
        OffsetDateTime expectedTime = OffsetDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC);
        assertAll(
                () -> assertEquals(11L, response.id()),
                () -> assertEquals(ORDER_NO, response.orderNo()),
                () -> assertEquals(7L, response.advertiserId()),
                () -> assertEquals(8L, response.advertiserAccountId()),
                () -> assertEquals(new BigDecimal("123.40"), response.amount()),
                () -> assertEquals(RechargeOrderStatus.PENDING, response.status()),
                () -> assertNull(response.providerTransactionNo()),
                () -> assertNull(response.paidAt()),
                () -> assertEquals(expectedTime, response.createdAt()),
                () -> assertEquals(expectedTime, response.updatedAt()),
                () -> assertSame(persisted.getCreatedAt(), persisted.getUpdatedAt()));
        verify(advertiserMapper, never()).selectById(7L);
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("invalidAmounts")
    @DisplayName("Service 拒绝非法充值金额且不访问数据库")
    void rejectsInvalidAmount(BigDecimal amount, String description) {
        CreateRechargeOrderRequest request = new CreateRechargeOrderRequest(7L, amount);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> rechargeOrderService.create(request),
                description);

        assertSame(PaymentErrorCode.INVALID_AMOUNT, exception.errorCode());
        verifyNoInteractions(rechargeOrderMapper, accountMapper, advertiserMapper, orderNumberGenerator);
    }

    @Test
    @DisplayName("广告主不存在时返回明确错误且不生成订单号")
    void missingAdvertiserIsRejected() {
        when(accountMapper.findByAdvertiserId(404L)).thenReturn(Optional.empty());
        when(advertiserMapper.selectById(404L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> rechargeOrderService.create(request(404L)));

        assertSame(PaymentErrorCode.ADVERTISER_NOT_FOUND, exception.errorCode());
        verifyNoInteractions(orderNumberGenerator, rechargeOrderMapper);
    }

    @Test
    @DisplayName("广告主存在但账户缺失时返回明确错误且不创建订单")
    void missingAccountIsRejected() {
        when(accountMapper.findByAdvertiserId(7L)).thenReturn(Optional.empty());
        when(advertiserMapper.selectById(7L)).thenReturn(new Advertiser());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> rechargeOrderService.create(request(7L)));

        assertSame(PaymentErrorCode.ACCOUNT_NOT_FOUND, exception.errorCode());
        verifyNoInteractions(orderNumberGenerator, rechargeOrderMapper);
    }

    @Test
    @DisplayName("按订单号查询时返回订单及所属广告主")
    void findsOrderAndAdvertiserByOrderNumber() {
        RechargeOrder order = pendingOrder();
        when(rechargeOrderMapper.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(order));
        when(accountMapper.selectById(8L)).thenReturn(account(8L, 7L));

        RechargeOrderResponse response = rechargeOrderService.findByOrderNo("  " + ORDER_NO + "  ");

        assertAll(
                () -> assertEquals(ORDER_NO, response.orderNo()),
                () -> assertEquals(7L, response.advertiserId()),
                () -> assertEquals(8L, response.advertiserAccountId()),
                () -> assertEquals(new BigDecimal("250.00"), response.amount()),
                () -> assertEquals(RechargeOrderStatus.PENDING, response.status()));
    }

    @Test
    @DisplayName("订单不存在时返回明确的 404 业务错误")
    void missingOrderIsRejected() {
        when(rechargeOrderMapper.findByOrderNo(ORDER_NO)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> rechargeOrderService.findByOrderNo(ORDER_NO));

        assertSame(PaymentErrorCode.ORDER_NOT_FOUND, exception.errorCode());
        verify(accountMapper, never()).selectById(any());
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("invalidOrderNumbers")
    @DisplayName("Service 拒绝非法订单号且不查询数据库")
    void rejectsInvalidOrderNumber(String orderNo, String description) {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> rechargeOrderService.findByOrderNo(orderNo),
                description);

        assertSame(PaymentErrorCode.INVALID_ORDER_NO, exception.errorCode());
        verifyNoInteractions(rechargeOrderMapper, accountMapper);
    }

    private static Stream<Arguments> invalidAmounts() {
        return Stream.of(
                Arguments.of(null, "金额不能为空"),
                Arguments.of(BigDecimal.ZERO, "金额不能为零"),
                Arguments.of(new BigDecimal("-0.01"), "金额不能为负"),
                Arguments.of(new BigDecimal("1.001"), "金额最多两位小数"),
                Arguments.of(new BigDecimal("100000000000000000.00"), "整数部分最多 17 位"));
    }

    private static Stream<Arguments> invalidOrderNumbers() {
        return Stream.of(
                Arguments.of(null, "订单号不能为空"),
                Arguments.of("   ", "订单号不能为空白"),
                Arguments.of("X".repeat(65), "订单号不能超过数据库字段长度"));
    }

    private CreateRechargeOrderRequest request(Long advertiserId) {
        return new CreateRechargeOrderRequest(advertiserId, new BigDecimal("250.00"));
    }

    private AdvertiserAccount account(Long accountId, Long advertiserId) {
        AdvertiserAccount account = new AdvertiserAccount();
        account.setId(accountId);
        account.setAdvertiserId(advertiserId);
        return account;
    }

    private RechargeOrder pendingOrder() {
        OffsetDateTime now = OffsetDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC);
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
