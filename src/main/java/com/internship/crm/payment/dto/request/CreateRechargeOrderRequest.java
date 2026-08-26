package com.internship.crm.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

@Schema(description = "创建模拟充值订单请求")
public record CreateRechargeOrderRequest(
        @NotNull(message = "广告主 ID 不能为空")
        @Positive(message = "广告主 ID 必须为正数")
        @Schema(example = "1")
        Long advertiserId,

        @NotNull(message = "充值金额不能为空")
        @DecimalMin(value = "0.01", message = "充值金额必须大于零")
        @Digits(integer = 17, fraction = 2, message = "充值金额最多为 17 位整数和 2 位小数")
        @Schema(example = "1000.00")
        BigDecimal amount) {
}
