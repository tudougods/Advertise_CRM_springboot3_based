package com.internship.crm.delivery.dto.response;

import com.internship.crm.advertiser.entity.Advertiser;
import com.internship.crm.delivery.entity.AdvertisingDeliveryRecord;
import com.internship.crm.delivery.entity.AdvertisingType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Schema(description = "广告投放记录")
public record AdvertisingDeliveryRecordResponse(
        Long id,
        String externalRecordNo,
        Long advertiserId,
        String advertiserName,
        Long advertisingTypeId,
        String advertisingTypeCode,
        String advertisingTypeName,
        LocalDate recordDate,
        Long impressions,
        Long clicks,
        Long conversions,
        BigDecimal spend,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static AdvertisingDeliveryRecordResponse from(
            AdvertisingDeliveryRecord record,
            Advertiser advertiser,
            AdvertisingType advertisingType) {
        return new AdvertisingDeliveryRecordResponse(
                record.getId(),
                record.getExternalRecordNo(),
                advertiser.getId(),
                advertiser.getName(),
                advertisingType.getId(),
                advertisingType.getCode(),
                advertisingType.getName(),
                record.getRecordDate(),
                record.getImpressions(),
                record.getClicks(),
                record.getConversions(),
                record.getSpend(),
                record.getCreatedAt(),
                record.getUpdatedAt());
    }
}
