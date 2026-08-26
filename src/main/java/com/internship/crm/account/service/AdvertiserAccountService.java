package com.internship.crm.account.service;

import com.internship.crm.account.dto.response.AdvertiserAccountResponse;
import com.internship.crm.account.exception.AccountErrorCode;
import com.internship.crm.account.mapper.AdvertiserAccountMapper;
import com.internship.crm.advertiser.mapper.AdvertiserMapper;
import com.internship.crm.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdvertiserAccountService {

    private final AdvertiserAccountMapper advertiserAccountMapper;
    private final AdvertiserMapper advertiserMapper;

    public AdvertiserAccountService(
            AdvertiserAccountMapper advertiserAccountMapper,
            AdvertiserMapper advertiserMapper) {
        this.advertiserAccountMapper = advertiserAccountMapper;
        this.advertiserMapper = advertiserMapper;
    }

    @Transactional(readOnly = true)
    public AdvertiserAccountResponse findByAdvertiserId(Long advertiserId) {
        return advertiserAccountMapper.findByAdvertiserId(advertiserId)
                .map(AdvertiserAccountResponse::from)
                .orElseThrow(() -> missingAccount(advertiserId));
    }

    private BusinessException missingAccount(Long advertiserId) {
        if (advertiserMapper.selectById(advertiserId) == null) {
            return new BusinessException(AccountErrorCode.ADVERTISER_NOT_FOUND);
        }
        return new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND);
    }
}
