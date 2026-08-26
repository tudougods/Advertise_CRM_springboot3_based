package com.internship.crm.account.dto.response;

import com.internship.crm.account.entity.AccountTransactionType;
import com.internship.crm.account.entity.AdvertiserAccountTransaction;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Objects;

@Schema(description = "广告主账户资金流水")
public record AdvertiserAccountTransactionResponse(
        Long id,
        Long accountId,
        String businessNo,
        AccountTransactionType transactionType,
        BigDecimal amount,
        BigDecimal balanceAfter,
        Long deliveryRecordId,
        Long rechargeOrderId,
        String remark,
        Long createdBy,
        OffsetDateTime createdAt) {

    public static AdvertiserAccountTransactionResponse from(
            AdvertiserAccountTransaction transaction) {
        Objects.requireNonNull(transaction, "transaction must not be null");
        return new AdvertiserAccountTransactionResponse(
                transaction.getId(),
                transaction.getAdvertiserAccountId(),
                transaction.getBusinessNo(),
                transaction.getTransactionType(),
                normalizeMoney(transaction.getAmount()),
                normalizeMoney(transaction.getBalanceAfter()),
                transaction.getAdvertisingDeliveryRecordId(),
                transaction.getRechargeOrderId(),
                transaction.getRemark(),
                transaction.getCreatedBy(),
                transaction.getCreatedAt());
    }

    private static BigDecimal normalizeMoney(BigDecimal value) {
        return Objects.requireNonNull(value, "transaction money must not be null")
                .setScale(2, RoundingMode.UNNECESSARY);
    }
}
