package com.internship.crm.account.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.internship.crm.account.entity.AdvertiserAccountTransaction;
import java.util.Optional;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

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
