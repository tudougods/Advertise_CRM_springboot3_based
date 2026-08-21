package com.internship.crm.advertiser.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "局部修改广告主请求；未提供的字段保持不变")
public record UpdateAdvertiserRequest(
        @Size(min = 1, max = 200, message = "广告主名称长度必须为 1 到 200 个字符")
        @Pattern(regexp = ".*\\S.*", message = "广告主名称不能为空白")
        String name,

        @Size(max = 64, message = "注册编号不能超过 64 个字符")
        String registrationNo,

        @Positive(message = "广告主分类 ID 必须为正数")
        Long categoryId,

        @Schema(description = "设为 true 时解除已有分类；不能与 categoryId 同时提供")
        Boolean clearCategory,

        @Positive(message = "负责人 ID 必须为正数")
        Long ownerUserId,

        @Schema(description = "设为 true 时解除已有负责人；不能与 ownerUserId 同时提供")
        Boolean clearOwner,

        @Size(max = 255, message = "网站地址不能超过 255 个字符")
        String website,

        @Size(max = 500, message = "企业地址不能超过 500 个字符")
        String address,

        @Size(max = 5000, message = "企业简介不能超过 5000 个字符")
        String description) {

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "categoryId 与 clearCategory=true 不能同时提供")
    public boolean isCategoryChangeValid() {
        return categoryId == null || !Boolean.TRUE.equals(clearCategory);
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "ownerUserId 与 clearOwner=true 不能同时提供")
    public boolean isOwnerChangeValid() {
        return ownerUserId == null || !Boolean.TRUE.equals(clearOwner);
    }
}
