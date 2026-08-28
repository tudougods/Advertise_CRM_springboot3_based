package com.internship.crm.delivery.dto.response;

import com.internship.crm.delivery.entity.AdvertisingType;
import com.internship.crm.delivery.entity.AdvertisingTypeStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "广告类型字典项")
public record AdvertisingTypeResponse(
        Long id,
        String code,
        String name,
        AdvertisingTypeStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static AdvertisingTypeResponse from(AdvertisingType advertisingType) {
        return new AdvertisingTypeResponse(
                advertisingType.getId(),
                advertisingType.getCode(),
                advertisingType.getName(),
                advertisingType.getStatus(),
                advertisingType.getCreatedAt(),
                advertisingType.getUpdatedAt());
    }
}
