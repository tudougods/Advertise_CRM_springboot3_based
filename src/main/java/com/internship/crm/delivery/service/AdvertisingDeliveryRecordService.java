package com.internship.crm.delivery.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.internship.crm.advertiser.entity.Advertiser;
import com.internship.crm.advertiser.entity.AdvertiserStatus;
import com.internship.crm.advertiser.mapper.AdvertiserMapper;
import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.common.response.PageResponse;
import com.internship.crm.delivery.dto.request.CreateAdvertisingDeliveryRecordRequest;
import com.internship.crm.delivery.dto.request.UpdateAdvertisingDeliveryRecordRequest;
import com.internship.crm.delivery.dto.response.AdvertisingDeliveryRecordResponse;
import com.internship.crm.delivery.entity.AdvertisingDeliveryRecord;
import com.internship.crm.delivery.entity.AdvertisingType;
import com.internship.crm.delivery.entity.AdvertisingTypeStatus;
import com.internship.crm.delivery.exception.DeliveryErrorCode;
import com.internship.crm.delivery.mapper.AdvertisingDeliveryRecordMapper;
import com.internship.crm.delivery.mapper.AdvertisingTypeMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdvertisingDeliveryRecordService {

    private final AdvertisingDeliveryRecordMapper deliveryRecordMapper;
    private final AdvertisingTypeMapper advertisingTypeMapper;
    private final AdvertiserMapper advertiserMapper;
    private final Clock clock;

    public AdvertisingDeliveryRecordService(
            AdvertisingDeliveryRecordMapper deliveryRecordMapper,
            AdvertisingTypeMapper advertisingTypeMapper,
            AdvertiserMapper advertiserMapper,
            Clock clock) {
        this.deliveryRecordMapper = deliveryRecordMapper;
        this.advertisingTypeMapper = advertisingTypeMapper;
        this.advertiserMapper = advertiserMapper;
        this.clock = clock;
    }

    @Transactional
    public AdvertisingDeliveryRecordResponse create(CreateAdvertisingDeliveryRecordRequest request) {
        BigDecimal normalizedSpend = validateAndNormalizeSpend(request.spend());
        validateMetrics(request.impressions(), request.clicks(), request.conversions());

        Advertiser advertiser = requireActiveAdvertiser(request.advertiserId());
        AdvertisingType advertisingType = requireActiveAdvertisingType(request.advertisingTypeCode().trim());
        OffsetDateTime now = OffsetDateTime.now(clock);

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

    @Transactional
    public AdvertisingDeliveryRecordResponse update(
            Long id, UpdateAdvertisingDeliveryRecordRequest request) {
        ensureUpdateHasFields(request);
        AdvertisingDeliveryRecord record = deliveryRecordMapper.selectByIdForUpdate(id);
        if (record == null) {
            throw new BusinessException(DeliveryErrorCode.DELIVERY_RECORD_NOT_FOUND);
        }

        boolean changingAdvertiser = request.advertiserId() != null
                && !Objects.equals(record.getAdvertiserId(), request.advertiserId());
        if (changingAdvertiser) {
            if (deliveryRecordMapper.hasAccountTransactionReference(id)) {
                throw new BusinessException(DeliveryErrorCode.DELIVERY_RECORD_ADVERTISER_LOCKED);
            }
            Advertiser advertiser = requireActiveAdvertiser(request.advertiserId());
            record.setAdvertiserId(advertiser.getId());
        }
        if (request.advertisingTypeCode() != null) {
            AdvertisingType advertisingType =
                    requireActiveAdvertisingType(request.advertisingTypeCode().trim());
            record.setAdvertisingTypeId(advertisingType.getId());
        }
        if (request.recordDate() != null) {
            record.setRecordDate(request.recordDate());
        }
        if (request.impressions() != null) {
            record.setImpressions(request.impressions());
        }
        if (request.clicks() != null) {
            record.setClicks(request.clicks());
        }
        if (request.conversions() != null) {
            record.setConversions(request.conversions());
        }
        if (request.spend() != null) {
            record.setSpend(validateAndNormalizeSpend(request.spend()));
        }

        validateMetrics(record.getImpressions(), record.getClicks(), record.getConversions());
        record.setUpdatedAt(OffsetDateTime.now(clock));
        try {
            if (deliveryRecordMapper.updateById(record) == 0) {
                throw new BusinessException(DeliveryErrorCode.DELIVERY_RECORD_NOT_FOUND);
            }
        } catch (DataIntegrityViolationException exception) {
            if (changingAdvertiser) {
                throw new BusinessException(
                        DeliveryErrorCode.DELIVERY_RECORD_ADVERTISER_LOCKED, exception);
            }
            throw exception;
        }
        return findById(id);
    }

    @Transactional
    public void delete(Long id) {
        try {
            if (deliveryRecordMapper.deleteIfUnreferenced(id) == 1) {
                return;
            }
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(DeliveryErrorCode.DELIVERY_RECORD_IN_USE, exception);
        }

        if (deliveryRecordMapper.selectById(id) == null) {
            throw new BusinessException(DeliveryErrorCode.DELIVERY_RECORD_NOT_FOUND);
        }
        throw new BusinessException(DeliveryErrorCode.DELIVERY_RECORD_IN_USE);
    }

    @Transactional(readOnly = true)
    public AdvertisingDeliveryRecordResponse findById(Long id) {
        AdvertisingDeliveryRecordResponse response = deliveryRecordMapper.selectDetailById(id);
        if (response == null) {
            throw new BusinessException(DeliveryErrorCode.DELIVERY_RECORD_NOT_FOUND);
        }
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdvertisingDeliveryRecordResponse> findAll(
            LocalDate startDate,
            LocalDate endDate,
            Long advertiserId,
            String advertisingTypeCode,
            long page,
            long size) {
        DateRange dateRange = normalizeDateRange(startDate, endDate);
        Long advertisingTypeId = resolveAdvertisingTypeId(advertisingTypeCode);
        if (advertisingTypeCode != null && advertisingTypeId == null) {
            return PageResponse.of(List.of(), page, size, 0);
        }

        Page<AdvertisingDeliveryRecordResponse> result = deliveryRecordMapper.selectPageWithDetails(
                new Page<>(page, size),
                dateRange.startDate(),
                dateRange.endDate(),
                advertiserId,
                advertisingTypeId);
        return PageResponse.of(
                result.getRecords(), result.getCurrent(), result.getSize(), result.getTotal());
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

    private Long resolveAdvertisingTypeId(String code) {
        if (code == null) {
            return null;
        }
        String normalizedCode = code.trim();
        if (normalizedCode.isEmpty()) {
            throw new BusinessException(DeliveryErrorCode.BLANK_ADVERTISING_TYPE_CODE);
        }
        return advertisingTypeMapper.findByCodeIgnoreCase(normalizedCode)
                .map(AdvertisingType::getId)
                .orElse(null);
    }

    private DateRange normalizeDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) {
            LocalDate defaultEndDate = LocalDate.now(clock);
            return new DateRange(defaultEndDate.minusDays(29), defaultEndDate);
        }
        if (startDate == null || endDate == null) {
            throw new BusinessException(DeliveryErrorCode.INCOMPLETE_DATE_RANGE);
        }
        if (startDate.isAfter(endDate)) {
            throw new BusinessException(DeliveryErrorCode.INVALID_DATE_RANGE);
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) > 365) {
            throw new BusinessException(DeliveryErrorCode.DATE_RANGE_TOO_LARGE);
        }
        return new DateRange(startDate, endDate);
    }

    private void validateMetrics(Long impressions, Long clicks, Long conversions) {
        if (impressions == null
                || clicks == null
                || conversions == null
                || impressions < 0
                || clicks < 0
                || conversions < 0
                || clicks > impressions
                || conversions > clicks) {
            throw new BusinessException(DeliveryErrorCode.INVALID_METRICS);
        }
    }

    private BigDecimal validateAndNormalizeSpend(BigDecimal spend) {
        if (spend == null || spend.signum() < 0) {
            throw new BusinessException(DeliveryErrorCode.INVALID_METRICS);
        }
        try {
            BigDecimal normalized = spend.setScale(2, RoundingMode.UNNECESSARY);
            if (normalized.precision() - normalized.scale() > 17) {
                throw new BusinessException(DeliveryErrorCode.INVALID_METRICS);
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new BusinessException(DeliveryErrorCode.INVALID_METRICS, exception);
        }
    }

    private void ensureUpdateHasFields(UpdateAdvertisingDeliveryRecordRequest request) {
        if (request.advertiserId() == null
                && request.advertisingTypeCode() == null
                && request.recordDate() == null
                && request.impressions() == null
                && request.clicks() == null
                && request.conversions() == null
                && request.spend() == null) {
            throw new BusinessException(DeliveryErrorCode.NO_FIELDS_TO_UPDATE);
        }
    }

    private record DateRange(LocalDate startDate, LocalDate endDate) {
    }
}
