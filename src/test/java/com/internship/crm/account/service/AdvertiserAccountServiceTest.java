package com.internship.crm.account.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.internship.crm.account.dto.response.AdvertiserAccountResponse;
import com.internship.crm.account.entity.AdvertiserAccount;
import com.internship.crm.account.exception.AccountErrorCode;
import com.internship.crm.account.mapper.AdvertiserAccountMapper;
import com.internship.crm.advertiser.entity.Advertiser;
import com.internship.crm.advertiser.mapper.AdvertiserMapper;
import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("广告主账户查询 Service 业务规则")
@ExtendWith({MockitoExtension.class, ReadableTestResultExtension.class})
class AdvertiserAccountServiceTest {

    @Mock
    private AdvertiserAccountMapper advertiserAccountMapper;

    @Mock
    private AdvertiserMapper advertiserMapper;

    private AdvertiserAccountService advertiserAccountService;

    @BeforeEach
    void setUp() {
        advertiserAccountService =
                new AdvertiserAccountService(advertiserAccountMapper, advertiserMapper);
    }

    @Test
    @DisplayName("存在账户时返回规范化为两位小数的余额且不额外查询广告主")
    void existingAccountReturnsBalanceWithoutAdvertiserLookup() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-26T00:00:00Z");
        AdvertiserAccount account = account(8L, 7L, new BigDecimal("123.4"), now);
        when(advertiserAccountMapper.findByAdvertiserId(7L)).thenReturn(Optional.of(account));

        AdvertiserAccountResponse response = advertiserAccountService.findByAdvertiserId(7L);

        assertAll(
                () -> assertEquals(8L, response.accountId()),
                () -> assertEquals(7L, response.advertiserId()),
                () -> assertEquals(new BigDecimal("123.40"), response.balance()),
                () -> assertEquals(now, response.createdAt()),
                () -> assertEquals(now, response.updatedAt()));
        verify(advertiserMapper, never()).selectById(7L);
    }

    @Test
    @DisplayName("账户和广告主均不存在时返回广告主不存在")
    void missingAdvertiserReturnsNotFound() {
        when(advertiserAccountMapper.findByAdvertiserId(404L)).thenReturn(Optional.empty());
        when(advertiserMapper.selectById(404L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> advertiserAccountService.findByAdvertiserId(404L));

        assertSame(AccountErrorCode.ADVERTISER_NOT_FOUND, exception.errorCode());
    }

    @Test
    @DisplayName("广告主存在但账户缺失时返回账户不存在且不隐式创建")
    void missingAccountReturnsNotFoundWithoutCreatingAccount() {
        when(advertiserAccountMapper.findByAdvertiserId(7L)).thenReturn(Optional.empty());
        when(advertiserMapper.selectById(7L)).thenReturn(new Advertiser());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> advertiserAccountService.findByAdvertiserId(7L));

        assertSame(AccountErrorCode.ACCOUNT_NOT_FOUND, exception.errorCode());
        verify(advertiserAccountMapper, never())
                .insert(org.mockito.ArgumentMatchers.any(AdvertiserAccount.class));
    }

    private AdvertiserAccount account(
            Long accountId,
            Long advertiserId,
            BigDecimal balance,
            OffsetDateTime timestamp) {
        AdvertiserAccount account = new AdvertiserAccount();
        account.setId(accountId);
        account.setAdvertiserId(advertiserId);
        account.setBalance(balance);
        account.setCreatedAt(timestamp);
        account.setUpdatedAt(timestamp);
        return account;
    }
}
