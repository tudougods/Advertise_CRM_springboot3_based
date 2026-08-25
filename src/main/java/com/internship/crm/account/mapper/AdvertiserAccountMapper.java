package com.internship.crm.account.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.internship.crm.account.entity.AdvertiserAccount;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

/** ORM data access entry point for advertiser account balances. */
@Mapper
public interface AdvertiserAccountMapper extends BaseMapper<AdvertiserAccount> {

    @SuppressWarnings("null") // The ORM's serializable getter references lack nullability metadata.
    default Optional<AdvertiserAccount> findByAdvertiserId(Long advertiserId) {
        LambdaQueryWrapper<AdvertiserAccount> query = new LambdaQueryWrapper<AdvertiserAccount>()
                .eq(AdvertiserAccount::getAdvertiserId, advertiserId)
                .last("LIMIT 1");
        return Optional.ofNullable(selectOne(query));
    }
}
