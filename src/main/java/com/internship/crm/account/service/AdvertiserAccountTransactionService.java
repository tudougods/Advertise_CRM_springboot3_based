package com.internship.crm.account.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.internship.crm.account.dto.response.AdvertiserAccountTransactionResponse;
import com.internship.crm.account.entity.AccountTransactionType;
import com.internship.crm.account.entity.AdvertiserAccount;
import com.internship.crm.account.entity.AdvertiserAccountTransaction;
import com.internship.crm.account.exception.AccountErrorCode;
import com.internship.crm.account.mapper.AdvertiserAccountMapper;
import com.internship.crm.account.mapper.AdvertiserAccountTransactionMapper;
import com.internship.crm.advertiser.mapper.AdvertiserMapper;
import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.common.response.PageResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdvertiserAccountTransactionService {

    private static final Duration MAX_TIME_RANGE = Duration.ofDays(366);

    private final AdvertiserAccountMapper accountMapper;
    private final AdvertiserAccountTransactionMapper transactionMapper;
    private final AdvertiserMapper advertiserMapper;

    public AdvertiserAccountTransactionService(
            AdvertiserAccountMapper accountMapper,
            AdvertiserAccountTransactionMapper transactionMapper,
            AdvertiserMapper advertiserMapper) {
        this.accountMapper = accountMapper;
        this.transactionMapper = transactionMapper;
        this.advertiserMapper = advertiserMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdvertiserAccountTransactionResponse> findAll(
            Long advertiserId,
            AccountTransactionType transactionType,
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            long page,
            long size) {
        validateTimeRange(startTime, endTime);
        AdvertiserAccount account = accountMapper.findByAdvertiserId(advertiserId)
                .orElseThrow(() -> missingAccount(advertiserId));
        Page<AdvertiserAccountTransaction> result = transactionMapper.selectPageByAccountId(
                new Page<>(page, size),
                account.getId(),
                transactionType,
                startTime,
                endTime);
        List<AdvertiserAccountTransactionResponse> items = result.getRecords().stream()
                .map(AdvertiserAccountTransactionResponse::from)
                .toList();
        return PageResponse.of(items, result.getCurrent(), result.getSize(), result.getTotal());
    }

    private void validateTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        if ((startTime == null) != (endTime == null)) {
            throw new BusinessException(AccountErrorCode.INCOMPLETE_TRANSACTION_TIME_RANGE);
        }
        if (startTime == null) {
            return;
        }
        if (startTime.isAfter(endTime)) {
            throw new BusinessException(AccountErrorCode.INVALID_TRANSACTION_TIME_RANGE);
        }
        if (Duration.between(startTime, endTime).compareTo(MAX_TIME_RANGE) > 0) {
            throw new BusinessException(AccountErrorCode.TRANSACTION_TIME_RANGE_TOO_LARGE);
        }
    }

    private BusinessException missingAccount(Long advertiserId) {
        if (advertiserMapper.selectById(advertiserId) == null) {
            return new BusinessException(AccountErrorCode.ADVERTISER_NOT_FOUND);
        }
        return new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND);
    }
}
