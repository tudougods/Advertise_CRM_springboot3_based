package com.internship.crm.account.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.internship.crm.account.entity.AdvertiserAccount;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** ORM data access entry point for advertiser account balances. */
@Mapper
public interface AdvertiserAccountMapper extends BaseMapper<AdvertiserAccount> {

    @Select("""
            UPDATE advertiser_accounts
            SET balance = balance + #{amount},
                updated_at = #{updatedAt}
            WHERE id = #{accountId}
            RETURNING balance
            """)
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    BigDecimal credit(
            @Param("accountId") Long accountId,
            @Param("amount") BigDecimal amount,
            @Param("updatedAt") OffsetDateTime updatedAt);

    @Select("""
            UPDATE advertiser_accounts
            SET balance = balance - #{amount},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{accountId}
              AND balance >= #{amount}
            RETURNING balance
            """)
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    BigDecimal debitIfBalanceSufficient(
            @Param("accountId") Long accountId,
            @Param("amount") BigDecimal amount);

    @SuppressWarnings("null") // The ORM's serializable getter references lack nullability metadata.
    default Optional<AdvertiserAccount> findByAdvertiserId(Long advertiserId) {
        LambdaQueryWrapper<AdvertiserAccount> query = new LambdaQueryWrapper<AdvertiserAccount>()
                .eq(AdvertiserAccount::getAdvertiserId, advertiserId)
                .last("LIMIT 1");
        return Optional.ofNullable(selectOne(query));
    }
}
