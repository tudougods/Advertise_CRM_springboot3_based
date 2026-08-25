package com.internship.crm.account.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.internship.crm.account.entity.AdvertiserAccountTransaction;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

/** ORM data access entry point for immutable advertiser account transactions. */
@Mapper
public interface AdvertiserAccountTransactionMapper extends BaseMapper<AdvertiserAccountTransaction> {

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
