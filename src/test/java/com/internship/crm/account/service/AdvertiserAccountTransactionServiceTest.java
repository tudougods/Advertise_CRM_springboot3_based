package com.internship.crm.account.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.internship.crm.account.dto.response.AdvertiserAccountTransactionResponse;
import com.internship.crm.account.entity.AccountTransactionType;
import com.internship.crm.account.entity.AdvertiserAccount;
import com.internship.crm.account.entity.AdvertiserAccountTransaction;
import com.internship.crm.account.exception.AccountErrorCode;
import com.internship.crm.account.mapper.AdvertiserAccountMapper;
import com.internship.crm.account.mapper.AdvertiserAccountTransactionMapper;
import com.internship.crm.advertiser.entity.Advertiser;
import com.internship.crm.advertiser.mapper.AdvertiserMapper;
import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.common.response.PageResponse;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("广告主账户流水分页 Service 查询规则")
@ExtendWith({MockitoExtension.class, ReadableTestResultExtension.class})
class AdvertiserAccountTransactionServiceTest {

    @Mock
    private AdvertiserAccountMapper accountMapper;

    @Mock
    private AdvertiserAccountTransactionMapper transactionMapper;

    @Mock
    private AdvertiserMapper advertiserMapper;

    private AdvertiserAccountTransactionService transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new AdvertiserAccountTransactionService(
                accountMapper, transactionMapper, advertiserMapper);
    }

    @Test
    @DisplayName("流水类型和时间范围会下推到数据库并返回物理分页结果")
    void filtersAreDelegatedToPhysicalPaginationQuery() {
        OffsetDateTime start = OffsetDateTime.parse("2026-08-01T00:00:00Z");
        OffsetDateTime end = OffsetDateTime.parse("2026-08-31T23:59:59Z");
        AdvertiserAccount account = account(8L, 7L);
        when(accountMapper.findByAdvertiserId(7L)).thenReturn(Optional.of(account));
        Page<AdvertiserAccountTransaction> mapperPage = new Page<>(2, 1, 3);
        mapperPage.setRecords(List.of(transaction(
                21L, 8L, "CONSUMPTION-001", AccountTransactionType.CONSUMPTION, end)));
        when(transactionMapper.selectPageByAccountId(
                any(), eq(8L), eq(AccountTransactionType.CONSUMPTION), eq(start), eq(end)))
                .thenReturn(mapperPage);

        PageResponse<AdvertiserAccountTransactionResponse> response = transactionService.findAll(
                7L, AccountTransactionType.CONSUMPTION, start, end, 2, 1);

        assertAll(
                () -> assertEquals(1, response.items().size()),
                () -> assertEquals("CONSUMPTION-001", response.items().get(0).businessNo()),
                () -> assertEquals(new BigDecimal("30.00"), response.items().get(0).amount()),
                () -> assertEquals(2, response.page()),
                () -> assertEquals(1, response.size()),
                () -> assertEquals(3, response.total()),
                () -> assertEquals(3, response.totalPages()));
        verify(advertiserMapper, never()).selectById(7L);
    }

    @Test
    @DisplayName("广告主存在但账户缺失时返回明确的账户不存在")
    void missingAccountReturnsNotFound() {
        when(accountMapper.findByAdvertiserId(7L)).thenReturn(Optional.empty());
        when(advertiserMapper.selectById(7L)).thenReturn(new Advertiser());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> transactionService.findAll(7L, null, null, null, 1, 20));

        assertSame(AccountErrorCode.ACCOUNT_NOT_FOUND, exception.errorCode());
        verify(transactionMapper, never()).selectPageByAccountId(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("只提供开始时间时拒绝查询")
    void incompleteTimeRangeIsRejected() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> transactionService.findAll(
                        7L,
                        null,
                        OffsetDateTime.parse("2026-08-01T00:00:00Z"),
                        null,
                        1,
                        20));

        assertSame(AccountErrorCode.INCOMPLETE_TRANSACTION_TIME_RANGE, exception.errorCode());
        verify(accountMapper, never()).findByAdvertiserId(any());
    }

    @Test
    @DisplayName("开始时间晚于结束时间时拒绝查询")
    void reversedTimeRangeIsRejected() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> transactionService.findAll(
                        7L,
                        null,
                        OffsetDateTime.parse("2026-08-02T00:00:00Z"),
                        OffsetDateTime.parse("2026-08-01T00:00:00Z"),
                        1,
                        20));

        assertSame(AccountErrorCode.INVALID_TRANSACTION_TIME_RANGE, exception.errorCode());
    }

    @Test
    @DisplayName("超过 366 天的时间范围被拒绝")
    void excessiveTimeRangeIsRejected() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> transactionService.findAll(
                        7L,
                        null,
                        OffsetDateTime.parse("2025-01-01T00:00:00Z"),
                        OffsetDateTime.parse("2026-01-03T00:00:00Z"),
                        1,
                        20));

        assertSame(AccountErrorCode.TRANSACTION_TIME_RANGE_TOO_LARGE, exception.errorCode());
    }

    private AdvertiserAccount account(Long accountId, Long advertiserId) {
        AdvertiserAccount account = new AdvertiserAccount();
        account.setId(accountId);
        account.setAdvertiserId(advertiserId);
        return account;
    }

    private AdvertiserAccountTransaction transaction(
            Long id,
            Long accountId,
            String businessNo,
            AccountTransactionType transactionType,
            OffsetDateTime createdAt) {
        AdvertiserAccountTransaction transaction = new AdvertiserAccountTransaction();
        transaction.setId(id);
        transaction.setAdvertiserAccountId(accountId);
        transaction.setBusinessNo(businessNo);
        transaction.setTransactionType(transactionType);
        transaction.setAmount(new BigDecimal("30.00"));
        transaction.setBalanceAfter(new BigDecimal("70.00"));
        transaction.setCreatedAt(createdAt);
        return transaction;
    }
}
