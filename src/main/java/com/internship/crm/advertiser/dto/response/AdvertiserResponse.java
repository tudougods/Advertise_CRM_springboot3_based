package com.internship.crm.advertiser.dto.response;

import com.internship.crm.advertiser.entity.Advertiser;
import com.internship.crm.advertiser.entity.AdvertiserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "广告主信息")
public record AdvertiserResponse(
        Long id,
        String name,
        String registrationNo,
        Long categoryId,
        Long ownerUserId,
        AdvertiserStatus status,
        String website,
        String address,
        String description,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static AdvertiserResponse from(Advertiser advertiser) {
        return new AdvertiserResponse(
                advertiser.getId(),
                advertiser.getName(),
                advertiser.getRegistrationNo(),
                advertiser.getCategoryId(),
                advertiser.getOwnerUserId(),
                advertiser.getStatus(),
                advertiser.getWebsite(),
                advertiser.getAddress(),
                advertiser.getDescription(),
                advertiser.getCreatedAt(),
                advertiser.getUpdatedAt());
    }
}
