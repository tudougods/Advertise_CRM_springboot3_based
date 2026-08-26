package com.internship.crm.account.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.internship.crm.account.entity.AccountTransactionType;
import com.internship.crm.account.entity.AdvertiserAccountTransaction;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** ORM data access entry point for immutable advertiser account transactions. */
@Mapper
public interface AdvertiserAccountTransactionMapper extends BaseMapper<AdvertiserAccountTransaction> {

    @Insert("""
            INSERT INTO advertiser_account_transactions (
                advertiser_account_id,
                business_no,
                transaction_type,
                amount,
                balance_after,
                advertising_delivery_record_id,
                recharge_order_id,
                remark,
                created_by,
                created_at
            ) VALUES (
                #{advertiserAccountId},
                #{businessNo},
                #{transactionType},
                #{amount},
                #{balanceAfter},
                #{advertisingDeliveryRecordId},
                #{rechargeOrderId},
                #{remark},
                #{createdBy},
                #{createdAt}
            )
            ON CONFLICT (business_no) DO NOTHING
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertIfBusinessNoAbsent(AdvertiserAccountTransaction transaction);

    @Select("""
            <script>
            SELECT
                id,
                advertiser_account_id,
                business_no,
                transaction_type,
                amount,
                balance_after,
                advertising_delivery_record_id,
                recharge_order_id,
                remark,
                created_by,
                created_at
            FROM advertiser_account_transactions
            WHERE advertiser_account_id = #{accountId}
            <if test="transactionType != null">
                AND transaction_type = #{transactionType}
            </if>
            <if test="startTime != null">
                AND created_at &gt;= #{startTime}
                AND created_at &lt;= #{endTime}
            </if>
            ORDER BY created_at DESC, id DESC
            </script>
            """)
    Page<AdvertiserAccountTransaction> selectPageByAccountId(
            Page<AdvertiserAccountTransaction> page,
            @Param("accountId") Long accountId,
            @Param("transactionType") AccountTransactionType transactionType,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime);

    @SuppressWarnings("null") // The ORM's serializable getter references lack nullability metadata.
    default Optional<AdvertiserAccountTransaction> findByBusinessNo(String businessNo) {
        LambdaQueryWrapper<AdvertiserAccountTransaction> query =
                new LambdaQueryWrapper<AdvertiserAccountTransaction>()
                        .eq(AdvertiserAccountTransaction::getBusinessNo, businessNo)
                        .last("LIMIT 1");
        return Optional.ofNullable(selectOne(query));
    }

    @SuppressWarnings("null") // The ORM's serializable getter references lack nullability metadata.
    default boolean existsByAdvertiserAccountId(Long advertiserAccountId) {
        return selectCount(new LambdaQueryWrapper<AdvertiserAccountTransaction>()
                .eq(AdvertiserAccountTransaction::getAdvertiserAccountId, advertiserAccountId)) > 0;
    }
}
