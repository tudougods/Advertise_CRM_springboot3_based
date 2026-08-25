package com.internship.crm.delivery.service;

import com.internship.crm.advertiser.entity.Advertiser;
import com.internship.crm.advertiser.entity.AdvertiserStatus;
import com.internship.crm.advertiser.mapper.AdvertiserMapper;
import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.delivery.dto.request.CreateAdvertisingDeliveryRecordRequest;
import com.internship.crm.delivery.dto.response.AdvertisingDeliveryRecordResponse;
import com.internship.crm.delivery.entity.AdvertisingDeliveryRecord;
import com.internship.crm.delivery.entity.AdvertisingType;
import com.internship.crm.delivery.entity.AdvertisingTypeStatus;
import com.internship.crm.delivery.exception.DeliveryErrorCode;
import com.internship.crm.delivery.mapper.AdvertisingDeliveryRecordMapper;
import com.internship.crm.delivery.mapper.AdvertisingTypeMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdvertisingDeliveryRecordService {

    private final AdvertisingDeliveryRecordMapper deliveryRecordMapper;
    private final AdvertisingTypeMapper advertisingTypeMapper;
    private final AdvertiserMapper advertiserMapper;

    public AdvertisingDeliveryRecordService(
            AdvertisingDeliveryRecordMapper deliveryRecordMapper,
            AdvertisingTypeMapper advertisingTypeMapper,
            AdvertiserMapper advertiserMapper) {
        this.deliveryRecordMapper = deliveryRecordMapper;
        this.advertisingTypeMapper = advertisingTypeMapper;
        this.advertiserMapper = advertiserMapper;
    }

    @Transactional
    public AdvertisingDeliveryRecordResponse create(CreateAdvertisingDeliveryRecordRequest request) {
        BigDecimal normalizedSpend = validateAndNormalizeSpend(request);
        validateMetrics(request);

        Advertiser advertiser = requireActiveAdvertiser(request.advertiserId());
        AdvertisingType advertisingType = requireActiveAdvertisingType(request.advertisingTypeCode().trim());
        OffsetDateTime now = OffsetDateTime.now();

        AdvertisingDeliveryRecord record = new AdvertisingDeliveryRecord();
        record.setExternalRecordNo(request.externalRecordNo().trim());
        record.setAdvertiserId(advertiser.getId());
        record.setAdvertisingTypeId(advertisingType.getId());
        record.setRecordDate(request.recordDate());
        record.setImpressions(request.impressions());
        record.setClicks(request.clicks());
        record.setConversions(request.conversions());
        record.setSpend(normalizedSpend);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);

        if (deliveryRecordMapper.insertIfExternalRecordNoAbsent(record) == 0) {
            throw new BusinessException(DeliveryErrorCode.EXTERNAL_RECORD_NO_ALREADY_EXISTS);
        }
        return AdvertisingDeliveryRecordResponse.from(record, advertiser, advertisingType);
    }

    private Advertiser requireActiveAdvertiser(Long advertiserId) {
        Advertiser advertiser = advertiserMapper.selectById(advertiserId);
        if (advertiser == null) {
            throw new BusinessException(DeliveryErrorCode.ADVERTISER_NOT_FOUND);
        }
        if (advertiser.getStatus() != AdvertiserStatus.ACTIVE) {
            throw new BusinessException(DeliveryErrorCode.ADVERTISER_DISABLED);
        }
        return advertiser;
    }

    private AdvertisingType requireActiveAdvertisingType(String code) {
        AdvertisingType advertisingType = advertisingTypeMapper.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.ADVERTISING_TYPE_NOT_FOUND));
        if (advertisingType.getStatus() != AdvertisingTypeStatus.ACTIVE) {
            throw new BusinessException(DeliveryErrorCode.ADVERTISING_TYPE_DISABLED);
        }
        return advertisingType;
    }

    private void validateMetrics(CreateAdvertisingDeliveryRecordRequest request) {
        if (request.impressions() == null
                || request.clicks() == null
                || request.conversions() == null
                || request.impressions() < 0
                || request.clicks() < 0
                || request.conversions() < 0
                || request.clicks() > request.impressions()
                || request.conversions() > request.clicks()) {
            throw new BusinessException(DeliveryErrorCode.INVALID_METRICS);
        }
    }

    private BigDecimal validateAndNormalizeSpend(CreateAdvertisingDeliveryRecordRequest request) {
        if (request.spend() == null || request.spend().signum() < 0) {
            throw new BusinessException(DeliveryErrorCode.INVALID_METRICS);
        }
        try {
            BigDecimal normalized = request.spend().setScale(2, RoundingMode.UNNECESSARY);
            if (normalized.precision() - normalized.scale() > 17) {
                throw new BusinessException(DeliveryErrorCode.INVALID_METRICS);
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new BusinessException(DeliveryErrorCode.INVALID_METRICS, exception);
        }
    }
}
