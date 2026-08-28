package com.internship.crm.account.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(description = "广告主账户消费请求")
public record CreateAccountConsumptionRequest(
        @NotBlank(message = "消费业务号不能为空")
        @Size(max = 64, message = "消费业务号不能超过 64 个字符")
        @Schema(example = "CONSUMPTION-20260826-001")
        String businessNo,

        @NotNull(message = "消费金额不能为空")
        @DecimalMin(value = "0.01", message = "消费金额必须大于零")
        @Digits(integer = 17, fraction = 2, message = "消费金额最多为 17 位整数和 2 位小数")
        @Schema(example = "300.00")
        BigDecimal amount,

        @Positive(message = "投放记录 ID 必须为正数")
        @Schema(description = "可选关联的投放记录 ID", example = "1")
        Long deliveryRecordId,

        @Size(max = 500, message = "消费备注不能超过 500 个字符")
        @Schema(example = "结算 2026-08-26 搜索广告花费")
        String remark) {
}
