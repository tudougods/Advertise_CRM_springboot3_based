package com.internship.crm.advertiser.dto.response;

import com.internship.crm.advertiser.entity.AdvertiserCategory;
import com.internship.crm.advertiser.entity.AdvertiserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "广告主分类信息")
public record AdvertiserCategoryResponse(
        Long id,
        String name,
        String description,
        AdvertiserStatus status,
        Integer sortOrder,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static AdvertiserCategoryResponse from(AdvertiserCategory category) {
        return new AdvertiserCategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getStatus(),
                category.getSortOrder(),
                category.getCreatedAt(),
                category.getUpdatedAt());
    }
}
