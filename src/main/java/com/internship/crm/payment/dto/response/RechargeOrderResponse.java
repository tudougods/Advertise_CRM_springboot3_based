package com.internship.crm.payment.dto.response;

import com.internship.crm.payment.entity.RechargeOrder;
import com.internship.crm.payment.entity.RechargeOrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Objects;

@Schema(description = "模拟充值订单")
public record RechargeOrderResponse(
        Long id,
        String orderNo,
        Long advertiserId,
        Long advertiserAccountId,
        BigDecimal amount,
        RechargeOrderStatus status,
        String providerTransactionNo,
        OffsetDateTime paidAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static RechargeOrderResponse from(RechargeOrder order, Long advertiserId) {
        Objects.requireNonNull(order, "order must not be null");
        return new RechargeOrderResponse(
                order.getId(),
                order.getOrderNo(),
                Objects.requireNonNull(advertiserId, "advertiserId must not be null"),
                order.getAdvertiserAccountId(),
                normalizeMoney(order.getAmount()),
                order.getStatus(),
                order.getProviderTransactionNo(),
                order.getPaidAt(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    private static BigDecimal normalizeMoney(BigDecimal value) {
        return Objects.requireNonNull(value, "order amount must not be null")
                .setScale(2, RoundingMode.UNNECESSARY);
    }
}
