package com.internship.crm.account.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.internship.crm.account.dto.request.CreateAccountConsumptionRequest;
import com.internship.crm.account.dto.response.AdvertiserAccountTransactionResponse;
import com.internship.crm.account.entity.AccountTransactionType;
import com.internship.crm.account.entity.AdvertiserAccount;
import com.internship.crm.account.entity.AdvertiserAccountTransaction;
import com.internship.crm.account.exception.AccountErrorCode;
import com.internship.crm.account.mapper.AdvertiserAccountMapper;
import com.internship.crm.account.mapper.AdvertiserAccountTransactionMapper;
import com.internship.crm.advertiser.mapper.AdvertiserMapper;
import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.delivery.entity.AdvertisingDeliveryRecord;
import com.internship.crm.delivery.mapper.AdvertisingDeliveryRecordMapper;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("广告主账户原子消费 Service 业务规则")
@ExtendWith({MockitoExtension.class, ReadableTestResultExtension.class})
class AdvertiserAccountConsumptionServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private AdvertiserAccountMapper accountMapper;

    @Mock
    private AdvertiserAccountTransactionMapper transactionMapper;

    @Mock
    private AdvertiserMapper advertiserMapper;

    @Mock
    private AdvertisingDeliveryRecordMapper deliveryRecordMapper;

    private AdvertiserAccountConsumptionService consumptionService;

    @BeforeEach
    void setUp() {
        consumptionService = new AdvertiserAccountConsumptionService(
                accountMapper,
                transactionMapper,
                advertiserMapper,
                deliveryRecordMapper,
                FIXED_CLOCK);
    }

    @Test
    @DisplayName("消费会原子扣减余额并追加规范化的不可变流水")
    void consumptionDebitsBalanceAndAppendsTransaction() {
        AdvertiserAccount account = account(8L, 7L, "100.00");
        AdvertisingDeliveryRecord deliveryRecord = deliveryRecord(11L, 7L);
        when(transactionMapper.findByBusinessNo("CONSUMPTION-001"))
                .thenReturn(Optional.empty());
        when(accountMapper.findByAdvertiserId(7L)).thenReturn(Optional.of(account));
        when(accountMapper.debitIfBalanceSufficient(8L, new BigDecimal("30.00")))
                .thenReturn(new BigDecimal("70.00"));
        when(deliveryRecordMapper.selectByIdForUpdate(11L)).thenReturn(deliveryRecord);
        when(transactionMapper.insertIfBusinessNoAbsent(any()))
                .thenAnswer(invocation -> {
                    AdvertiserAccountTransaction transaction = invocation.getArgument(0);
                    transaction.setId(21L);
                    return 1;
                });

        AdvertiserAccountTransactionResponse response = consumptionService.consume(
                7L,
                request("  CONSUMPTION-001  ", "30", 11L, "  搜索广告结算  "),
                3L);

        ArgumentCaptor<AdvertiserAccountTransaction> captor =
                ArgumentCaptor.forClass(AdvertiserAccountTransaction.class);
        verify(transactionMapper).insertIfBusinessNoAbsent(captor.capture());
        AdvertiserAccountTransaction inserted = captor.getValue();
        assertAll(
                () -> assertEquals("CONSUMPTION-001", inserted.getBusinessNo()),
                () -> assertSame(AccountTransactionType.CONSUMPTION, inserted.getTransactionType()),
                () -> assertEquals(new BigDecimal("30.00"), inserted.getAmount()),
                () -> assertEquals(new BigDecimal("70.00"), inserted.getBalanceAfter()),
                () -> assertEquals("搜索广告结算", inserted.getRemark()),
                () -> assertEquals(3L, inserted.getCreatedBy()),
                () -> assertEquals(21L, response.id()),
                () -> assertEquals(new BigDecimal("70.00"), response.balanceAfter()));
    }

    @Test
    @DisplayName("已存在的业务号在扣款前返回冲突")
    void duplicateBusinessNumberStopsBeforeDebit() {
        when(transactionMapper.findByBusinessNo("CONSUMPTION-001"))
                .thenReturn(Optional.of(new AdvertiserAccountTransaction()));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> consumptionService.consume(
                        7L, request("CONSUMPTION-001", "30.00", null, null), 3L));

        assertSame(AccountErrorCode.BUSINESS_NO_ALREADY_EXISTS, exception.errorCode());
        verify(accountMapper, never()).debitIfBalanceSufficient(any(), any());
    }

    @Test
    @DisplayName("余额不足时不写入消费流水")
    void insufficientBalanceDoesNotAppendTransaction() {
        AdvertiserAccount account = account(8L, 7L, "20.00");
        when(transactionMapper.findByBusinessNo("CONSUMPTION-002"))
                .thenReturn(Optional.empty());
        when(accountMapper.findByAdvertiserId(7L)).thenReturn(Optional.of(account));
        when(accountMapper.debitIfBalanceSufficient(8L, new BigDecimal("30.00")))
                .thenReturn(null);
        when(accountMapper.selectById(8L)).thenReturn(account);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> consumptionService.consume(
                        7L, request("CONSUMPTION-002", "30.00", null, null), 3L));

        assertSame(AccountErrorCode.INSUFFICIENT_BALANCE, exception.errorCode());
        verify(transactionMapper, never()).insertIfBusinessNoAbsent(any());
        verify(deliveryRecordMapper, never()).selectByIdForUpdate(any());
    }

    @Test
    @DisplayName("并发插入发现相同业务号时返回冲突并交由事务回滚扣款")
    void concurrentDuplicateBusinessNumberReturnsConflict() {
        AdvertiserAccount account = account(8L, 7L, "100.00");
        when(transactionMapper.findByBusinessNo("CONSUMPTION-003"))
                .thenReturn(Optional.empty());
        when(accountMapper.findByAdvertiserId(7L)).thenReturn(Optional.of(account));
        when(accountMapper.debitIfBalanceSufficient(8L, new BigDecimal("30.00")))
                .thenReturn(new BigDecimal("70.00"));
        when(transactionMapper.insertIfBusinessNoAbsent(any())).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> consumptionService.consume(
                        7L, request("CONSUMPTION-003", "30.00", null, null), 3L));

        assertSame(AccountErrorCode.BUSINESS_NO_ALREADY_EXISTS, exception.errorCode());
    }

    @Test
    @DisplayName("并发重复请求耗尽余额时优先返回业务号冲突而非余额不足")
    void concurrentDuplicateAfterFailedDebitReturnsBusinessNumberConflict() {
        AdvertiserAccount account = account(8L, 7L, "30.00");
        AdvertiserAccountTransaction committedTransaction = new AdvertiserAccountTransaction();
        when(transactionMapper.findByBusinessNo("CONSUMPTION-003"))
                .thenReturn(Optional.empty(), Optional.of(committedTransaction));
        when(accountMapper.findByAdvertiserId(7L)).thenReturn(Optional.of(account));
        when(accountMapper.debitIfBalanceSufficient(8L, new BigDecimal("30.00")))
                .thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> consumptionService.consume(
                        7L, request("CONSUMPTION-003", "30.00", null, null), 3L));

        assertSame(AccountErrorCode.BUSINESS_NO_ALREADY_EXISTS, exception.errorCode());
        verify(accountMapper, never()).selectById(8L);
    }

    @Test
    @DisplayName("关联其他广告主的投放记录时拒绝生成流水")
    void deliveryRecordMustBelongToAdvertiser() {
        AdvertiserAccount account = account(8L, 7L, "100.00");
        when(transactionMapper.findByBusinessNo("CONSUMPTION-004"))
                .thenReturn(Optional.empty());
        when(accountMapper.findByAdvertiserId(7L)).thenReturn(Optional.of(account));
        when(accountMapper.debitIfBalanceSufficient(8L, new BigDecimal("30.00")))
                .thenReturn(new BigDecimal("70.00"));
        when(deliveryRecordMapper.selectByIdForUpdate(11L))
                .thenReturn(deliveryRecord(11L, 99L));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> consumptionService.consume(
                        7L, request("CONSUMPTION-004", "30.00", 11L, null), 3L));

        assertSame(AccountErrorCode.DELIVERY_RECORD_ADVERTISER_MISMATCH, exception.errorCode());
        verify(transactionMapper, never()).insertIfBusinessNoAbsent(any());
    }

    @Test
    @DisplayName("超过两位小数的金额在访问数据库前被拒绝")
    void amountWithExcessiveScaleIsRejected() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> consumptionService.consume(
                        7L, request("CONSUMPTION-005", "1.001", null, null), 3L));

        assertSame(AccountErrorCode.INVALID_AMOUNT, exception.errorCode());
        verify(transactionMapper, never()).findByBusinessNo(any());
    }

    private CreateAccountConsumptionRequest request(
            String businessNo,
            String amount,
            Long deliveryRecordId,
            String remark) {
        return new CreateAccountConsumptionRequest(
                businessNo, new BigDecimal(amount), deliveryRecordId, remark);
    }

    private AdvertiserAccount account(Long id, Long advertiserId, String balance) {
        AdvertiserAccount account = new AdvertiserAccount();
        account.setId(id);
        account.setAdvertiserId(advertiserId);
        account.setBalance(new BigDecimal(balance));
        return account;
    }

    private AdvertisingDeliveryRecord deliveryRecord(Long id, Long advertiserId) {
        AdvertisingDeliveryRecord record = new AdvertisingDeliveryRecord();
        record.setId(id);
        record.setAdvertiserId(advertiserId);
        return record;
    }
}
