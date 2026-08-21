package com.internship.crm.advertiser.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "局部修改广告主请求；未提供的字段保持不变")
public record UpdateAdvertiserRequest(
        @Size(min = 1, max = 200, message = "广告主名称长度必须为 1 到 200 个字符")
        @Pattern(regexp = ".*\\S.*", message = "广告主名称不能为空白")
        String name,

        @Size(max = 64, message = "注册编号不能超过 64 个字符")
        String registrationNo,

        Long categoryId,

        Long ownerUserId,

        @Size(max = 255, message = "网站地址不能超过 255 个字符")
        String website,

        @Size(max = 500, message = "企业地址不能超过 500 个字符")
        String address,

        @Size(max = 5000, message = "企业简介不能超过 5000 个字符")
        String description) {
}
