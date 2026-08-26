package com.internship.crm.payment.dto.request;

import com.internship.crm.payment.entity.MockPaymentOutcome;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "本地模拟支付请求")
public record SimulateRechargePaymentRequest(
        @NotNull(message = "模拟支付结果不能为空")
        @Schema(description = "仅支持 SUCCESS 或 FAILED", example = "SUCCESS")
        MockPaymentOutcome outcome) {
}
