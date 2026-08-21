package com.internship.crm.advertiser.api;

import com.internship.crm.advertiser.domain.AdvertiserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Schema(description = "局部修改广告主分类请求；未提供的字段保持不变")
public record UpdateAdvertiserCategoryRequest(
        @Size(min = 1, max = 100, message = "分类名称长度必须为 1 到 100 个字符")
        @Pattern(regexp = ".*\\S.*", message = "分类名称不能为空白")
        String name,

        @Size(max = 500, message = "分类说明不能超过 500 个字符")
        String description,

        AdvertiserStatus status,

        @PositiveOrZero(message = "展示顺序不能为负数")
        Integer sortOrder) {
}
