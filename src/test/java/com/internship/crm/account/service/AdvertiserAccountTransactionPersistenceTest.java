package com.internship.crm.account.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.internship.crm.account.dto.response.AdvertiserAccountTransactionResponse;
import com.internship.crm.account.entity.AccountTransactionType;
import com.internship.crm.account.entity.AdvertiserAccount;
import com.internship.crm.account.entity.AdvertiserAccountTransaction;
import com.internship.crm.account.mapper.AdvertiserAccountMapper;
import com.internship.crm.account.mapper.AdvertiserAccountTransactionMapper;
import com.internship.crm.advertiser.dto.request.CreateAdvertiserRequest;
import com.internship.crm.advertiser.dto.response.AdvertiserResponse;
import com.internship.crm.advertiser.service.AdvertiserService;
import com.internship.crm.common.response.PageResponse;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties =
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@Transactional
@DisplayName("广告主账户流水 PostgreSQL 物理分页")
@ExtendWith(ReadableTestResultExtension.class)
class AdvertiserAccountTransactionPersistenceTest {

    @Autowired
    private AdvertiserService advertiserService;

    @Autowired
    private AdvertiserAccountMapper accountMapper;

    @Autowired
    private AdvertiserAccountTransactionMapper transactionMapper;

    @Autowired
    private AdvertiserAccountTransactionService transactionService;

    @Test
    @DisplayName("数据库按账户、类型和时间筛选并以时间和 ID 倒序稳定分页")
    void databaseFiltersAndPaginatesTransactions() {
        AdvertiserResponse advertiser = createAdvertiser();
        AdvertiserAccount account =
                accountMapper.findByAdvertiserId(advertiser.id()).orElseThrow();
        OffsetDateTime firstTime = OffsetDateTime.parse("2026-08-01T00:00:00Z");
        OffsetDateTime secondTime = OffsetDateTime.parse("2026-08-02T00:00:00Z");
        OffsetDateTime thirdTime = OffsetDateTime.parse("2026-08-03T00:00:00Z");
        AdvertiserAccountTransaction first = insertTransaction(
                account.getId(), AccountTransactionType.CONSUMPTION, firstTime);
        insertTransaction(account.getId(), AccountTransactionType.RECHARGE, secondTime);
        AdvertiserAccountTransaction third = insertTransaction(
                account.getId(), AccountTransactionType.CONSUMPTION, thirdTime);

        PageResponse<AdvertiserAccountTransactionResponse> firstPage = transactionService.findAll(
                advertiser.id(),
                AccountTransactionType.CONSUMPTION,
                firstTime,
                thirdTime,
                1,
                1);
        PageResponse<AdvertiserAccountTransactionResponse> secondPage = transactionService.findAll(
                advertiser.id(),
                AccountTransactionType.CONSUMPTION,
                firstTime,
                thirdTime,
                2,
                1);

        assertAll(
                () -> assertEquals(2L, firstPage.total()),
                () -> assertEquals(2L, firstPage.totalPages()),
                () -> assertEquals(third.getId(), firstPage.items().get(0).id()),
                () -> assertEquals(first.getId(), secondPage.items().get(0).id()),
                () -> assertEquals(
                        AccountTransactionType.CONSUMPTION,
                        firstPage.items().get(0).transactionType()));
    }

    private AdvertiserResponse createAdvertiser() {
        return advertiserService.create(new CreateAdvertiserRequest(
                "account-transaction-page-" + UUID.randomUUID(),
                null,
                null,
                null,
                null,
                null,
                null,
                null));
    }

    private AdvertiserAccountTransaction insertTransaction(
            Long accountId,
            AccountTransactionType transactionType,
            OffsetDateTime createdAt) {
        AdvertiserAccountTransaction transaction = new AdvertiserAccountTransaction();
        transaction.setAdvertiserAccountId(accountId);
        transaction.setBusinessNo("TRANSACTION-PAGE-" + UUID.randomUUID());
        transaction.setTransactionType(transactionType);
        transaction.setAmount(new BigDecimal("10.00"));
        transaction.setBalanceAfter(new BigDecimal("0.00"));
        transaction.setCreatedAt(createdAt);
        transactionMapper.insert(transaction);
        return transaction;
    }
}
