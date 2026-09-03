package com.internship.crm.payment.dto.request;

import com.internship.crm.payment.entity.MockPaymentOutcome;
import com.internship.crm.payment.validation.PaymentReferenceRules;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(description = "本地模拟支付平台回调")
public record MockPaymentCallbackRequest(
        @NotBlank(message = "回调事件编号不能为空")
        @Size(max = PaymentReferenceRules.EXTERNAL_REFERENCE_MAX_LENGTH,
                message = "回调事件编号长度不能超过 100")
        @Pattern(regexp = PaymentReferenceRules.SAFE_REFERENCE_PATTERN,
                message = "回调事件编号格式不合法")
        @Schema(example = "evt_20260827_0001")
        String eventId,

        @NotBlank(message = "充值订单号不能为空")
        @Size(max = PaymentReferenceRules.ORDER_NO_MAX_LENGTH,
                message = "充值订单号长度不能超过 64")
        @Pattern(regexp = PaymentReferenceRules.SAFE_REFERENCE_PATTERN,
                message = "充值订单号格式不合法")
        @Schema(example = "R202608270001")
        String orderNo,

        @NotNull(message = "广告主 ID 不能为空")
        @Positive(message = "广告主 ID 必须为正数")
        @Schema(example = "1")
        Long advertiserId,

        @NotNull(message = "充值金额不能为空")
        @DecimalMin(value = "0.01", message = "充值金额必须大于零")
        @Digits(integer = 17, fraction = 2, message = "充值金额最多为 17 位整数和 2 位小数")
        @Schema(example = "1000.00")
        BigDecimal amount,

        @NotNull(message = "支付结果不能为空")
        MockPaymentOutcome outcome,

        @Size(max = PaymentReferenceRules.EXTERNAL_REFERENCE_MAX_LENGTH,
                message = "支付平台交易号长度不能超过 100")
        @Pattern(regexp = PaymentReferenceRules.SAFE_REFERENCE_PATTERN,
                message = "支付平台交易号格式不合法")
        @Schema(example = "mock_txn_20260827_0001")
        String providerTransactionNo) {
}
