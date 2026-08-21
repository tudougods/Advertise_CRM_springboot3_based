package com.internship.crm.advertiser.api;

import com.internship.crm.advertiser.domain.AdvertiserCategory;
import com.internship.crm.advertiser.domain.AdvertiserStatus;
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
