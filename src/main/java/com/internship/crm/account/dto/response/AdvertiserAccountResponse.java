package com.internship.crm.account.dto.response;

import com.internship.crm.account.entity.AdvertiserAccount;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Objects;

@Schema(description = "广告主账户余额")
public record AdvertiserAccountResponse(
        Long accountId,
        Long advertiserId,
        BigDecimal balance,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static AdvertiserAccountResponse from(AdvertiserAccount account) {
        Objects.requireNonNull(account, "account must not be null");
        return new AdvertiserAccountResponse(
                account.getId(),
                account.getAdvertiserId(),
                normalizeMoney(account.getBalance()),
                account.getCreatedAt(),
                account.getUpdatedAt());
    }

    private static BigDecimal normalizeMoney(BigDecimal value) {
        return Objects.requireNonNull(value, "account balance must not be null")
                .setScale(2, RoundingMode.UNNECESSARY);
    }
}
