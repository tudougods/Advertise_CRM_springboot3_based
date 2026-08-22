package com.internship.crm.advertiser.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.internship.crm.advertiser.entity.Advertiser;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

/** ORM data access entry point for advertisers. */
@Mapper
public interface AdvertiserMapper extends BaseMapper<Advertiser> {

    default Optional<Advertiser> findByNameIgnoreCase(String name) {
        LambdaQueryWrapper<Advertiser> query = new LambdaQueryWrapper<Advertiser>()
                .apply("LOWER(name) = LOWER({0})", name)
                .last("LIMIT 1");
        return Optional.ofNullable(selectOne(query));
    }

    @SuppressWarnings("null") // The ORM's serializable getter references lack nullability metadata.
    default Optional<Advertiser> findByRegistrationNo(String registrationNo) {
        LambdaQueryWrapper<Advertiser> query = new LambdaQueryWrapper<Advertiser>()
                .eq(Advertiser::getRegistrationNo, registrationNo)
                .last("LIMIT 1");
        return Optional.ofNullable(selectOne(query));
    }
}
