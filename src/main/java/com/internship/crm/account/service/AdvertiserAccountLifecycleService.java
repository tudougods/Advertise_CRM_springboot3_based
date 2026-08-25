package com.internship.crm.account.service;

import com.internship.crm.account.entity.AdvertiserAccount;
import com.internship.crm.account.mapper.AdvertiserAccountMapper;
import com.internship.crm.account.mapper.AdvertiserAccountTransactionMapper;
import com.internship.crm.advertiser.exception.AdvertiserErrorCode;
import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.delivery.mapper.AdvertisingDeliveryRecordMapper;
import com.internship.crm.payment.mapper.RechargeOrderMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Keeps the one-to-one advertiser account lifecycle consistent with advertiser changes. */
@Service
public class AdvertiserAccountLifecycleService {

    private final AdvertiserAccountMapper accountMapper;
    private final AdvertiserAccountTransactionMapper transactionMapper;
    private final AdvertisingDeliveryRecordMapper deliveryRecordMapper;
    private final RechargeOrderMapper rechargeOrderMapper;

    public AdvertiserAccountLifecycleService(
            AdvertiserAccountMapper accountMapper,
            AdvertiserAccountTransactionMapper transactionMapper,
            AdvertisingDeliveryRecordMapper deliveryRecordMapper,
            RechargeOrderMapper rechargeOrderMapper) {
        this.accountMapper = accountMapper;
        this.transactionMapper = transactionMapper;
        this.deliveryRecordMapper = deliveryRecordMapper;
        this.rechargeOrderMapper = rechargeOrderMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void createAccount(Long advertiserId, OffsetDateTime createdAt) {
        AdvertiserAccount account = new AdvertiserAccount();
        account.setAdvertiserId(advertiserId);
        account.setBalance(new BigDecimal("0.00"));
        account.setCreatedAt(createdAt);
        account.setUpdatedAt(createdAt);
        accountMapper.insert(account);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void ensureDeletableAndDeleteAccount(Long advertiserId) {
        if (deliveryRecordMapper.existsByAdvertiserId(advertiserId)) {
            throw new BusinessException(AdvertiserErrorCode.ADVERTISER_HAS_BUSINESS_DATA);
        }

        accountMapper.findByAdvertiserId(advertiserId).ifPresent(account -> {
            if (transactionMapper.existsByAdvertiserAccountId(account.getId())
                    || rechargeOrderMapper.existsByAdvertiserAccountId(account.getId())) {
                throw new BusinessException(AdvertiserErrorCode.ADVERTISER_HAS_BUSINESS_DATA);
            }
            accountMapper.deleteById(account.getId());
        });
    }
}
