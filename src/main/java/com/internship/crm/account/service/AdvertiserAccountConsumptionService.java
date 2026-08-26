package com.internship.crm.account.service;

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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdvertiserAccountConsumptionService {

    private static final Logger log =
            LoggerFactory.getLogger(AdvertiserAccountConsumptionService.class);

    private final AdvertiserAccountMapper accountMapper;
    private final AdvertiserAccountTransactionMapper transactionMapper;
    private final AdvertiserMapper advertiserMapper;
    private final AdvertisingDeliveryRecordMapper deliveryRecordMapper;
    private final Clock clock;

    public AdvertiserAccountConsumptionService(
            AdvertiserAccountMapper accountMapper,
            AdvertiserAccountTransactionMapper transactionMapper,
            AdvertiserMapper advertiserMapper,
            AdvertisingDeliveryRecordMapper deliveryRecordMapper,
            Clock clock) {
        this.accountMapper = accountMapper;
        this.transactionMapper = transactionMapper;
        this.advertiserMapper = advertiserMapper;
        this.deliveryRecordMapper = deliveryRecordMapper;
        this.clock = clock;
    }

    @Transactional
    public AdvertiserAccountTransactionResponse consume(
            Long advertiserId,
            CreateAccountConsumptionRequest request,
            Long createdBy) {
        String businessNo = normalizeBusinessNo(request.businessNo());
        BigDecimal amount = normalizeAmount(request.amount());
        if (transactionMapper.findByBusinessNo(businessNo).isPresent()) {
            throw new BusinessException(AccountErrorCode.BUSINESS_NO_ALREADY_EXISTS);
        }

        AdvertiserAccount account = requireAccount(advertiserId);
        BigDecimal balanceAfter = accountMapper.debitIfBalanceSufficient(account.getId(), amount);
        if (balanceAfter == null) {
            if (transactionMapper.findByBusinessNo(businessNo).isPresent()) {
                throw new BusinessException(AccountErrorCode.BUSINESS_NO_ALREADY_EXISTS);
            }
            if (accountMapper.selectById(account.getId()) == null) {
                throw new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND);
            }
            throw new BusinessException(AccountErrorCode.INSUFFICIENT_BALANCE);
        }

        validateDeliveryRecord(advertiserId, request.deliveryRecordId());
        AdvertiserAccountTransaction transaction = consumptionTransaction(
                account,
                businessNo,
                amount,
                balanceAfter,
                request.deliveryRecordId(),
                normalizeRemark(request.remark()),
                createdBy);
        if (transactionMapper.insertIfBusinessNoAbsent(transaction) == 0) {
            throw new BusinessException(AccountErrorCode.BUSINESS_NO_ALREADY_EXISTS);
        }

        log.info(
                "Advertiser account consumption completed: advertiserId={} accountId={} businessNo={}",
                advertiserId,
                account.getId(),
                safeForLog(businessNo));
        return AdvertiserAccountTransactionResponse.from(transaction);
    }

    private AdvertiserAccount requireAccount(Long advertiserId) {
        return accountMapper.findByAdvertiserId(advertiserId)
                .orElseThrow(() -> missingAccount(advertiserId));
    }

    private BusinessException missingAccount(Long advertiserId) {
        if (advertiserMapper.selectById(advertiserId) == null) {
            return new BusinessException(AccountErrorCode.ADVERTISER_NOT_FOUND);
        }
        return new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND);
    }

    private void validateDeliveryRecord(Long advertiserId, Long deliveryRecordId) {
        if (deliveryRecordId == null) {
            return;
        }
        AdvertisingDeliveryRecord record = deliveryRecordMapper.selectByIdForUpdate(deliveryRecordId);
        if (record == null) {
            throw new BusinessException(AccountErrorCode.DELIVERY_RECORD_NOT_FOUND);
        }
        if (!Objects.equals(advertiserId, record.getAdvertiserId())) {
            throw new BusinessException(AccountErrorCode.DELIVERY_RECORD_ADVERTISER_MISMATCH);
        }
    }

    private AdvertiserAccountTransaction consumptionTransaction(
            AdvertiserAccount account,
            String businessNo,
            BigDecimal amount,
            BigDecimal balanceAfter,
            Long deliveryRecordId,
            String remark,
            Long createdBy) {
        AdvertiserAccountTransaction transaction = new AdvertiserAccountTransaction();
        transaction.setAdvertiserAccountId(account.getId());
        transaction.setBusinessNo(businessNo);
        transaction.setTransactionType(AccountTransactionType.CONSUMPTION);
        transaction.setAmount(amount);
        transaction.setBalanceAfter(balanceAfter.setScale(2, RoundingMode.UNNECESSARY));
        transaction.setAdvertisingDeliveryRecordId(deliveryRecordId);
        transaction.setRemark(remark);
        transaction.setCreatedBy(createdBy);
        transaction.setCreatedAt(OffsetDateTime.now(clock));
        return transaction;
    }

    private String normalizeBusinessNo(String businessNo) {
        if (businessNo == null) {
            throw new BusinessException(AccountErrorCode.INVALID_BUSINESS_NO);
        }
        String normalized = businessNo.trim();
        if (normalized.isEmpty() || normalized.length() > 64) {
            throw new BusinessException(AccountErrorCode.INVALID_BUSINESS_NO);
        }
        return normalized;
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(AccountErrorCode.INVALID_AMOUNT);
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BusinessException(AccountErrorCode.INVALID_AMOUNT, exception);
        }
    }

    private String normalizeRemark(String remark) {
        if (remark == null) {
            return null;
        }
        String normalized = remark.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String safeForLog(String value) {
        return value.replaceAll("[\\p{Cntrl}]", "_");
    }
}
