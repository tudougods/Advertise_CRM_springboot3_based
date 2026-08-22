package com.internship.crm.advertiser.dto.request;

import com.internship.crm.advertiser.entity.AdvertiserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Schema(description = "创建广告主分类请求")
public record CreateAdvertiserCategoryRequest(
        @NotBlank(message = "分类名称不能为空")
        @Size(max = 100, message = "分类名称不能超过 100 个字符")
        @Schema(example = "电商")
        String name,

        @Size(max = 500, message = "分类说明不能超过 500 个字符")
        String description,

        AdvertiserStatus status,

        @PositiveOrZero(message = "展示顺序不能为负数")
        Integer sortOrder) {
}
