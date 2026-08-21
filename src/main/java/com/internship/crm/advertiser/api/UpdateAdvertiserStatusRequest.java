package com.internship.crm.advertiser.api;

import com.internship.crm.advertiser.domain.AdvertiserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "修改广告主状态请求")
public record UpdateAdvertiserStatusRequest(
        @NotNull(message = "广告主状态不能为空")
        AdvertiserStatus status) {
}
