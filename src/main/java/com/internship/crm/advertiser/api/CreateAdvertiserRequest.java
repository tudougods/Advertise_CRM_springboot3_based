package com.internship.crm.advertiser.api;

import com.internship.crm.advertiser.domain.AdvertiserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "创建广告主请求")
public record CreateAdvertiserRequest(
        @NotBlank(message = "广告主名称不能为空")
        @Size(max = 200, message = "广告主名称不能超过 200 个字符")
        @Schema(example = "示例科技有限公司")
        String name,

        @Size(max = 64, message = "注册编号不能超过 64 个字符")
        String registrationNo,

        Long categoryId,

        Long ownerUserId,

        AdvertiserStatus status,

        @Size(max = 255, message = "网站地址不能超过 255 个字符")
        String website,

        @Size(max = 500, message = "企业地址不能超过 500 个字符")
        String address,

        @Size(max = 5000, message = "企业简介不能超过 5000 个字符")
        String description) {
}
