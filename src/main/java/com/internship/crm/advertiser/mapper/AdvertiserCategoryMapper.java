package com.internship.crm.advertiser.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.internship.crm.advertiser.entity.AdvertiserCategory;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis-Plus data access entry point for advertiser categories. */
@Mapper
public interface AdvertiserCategoryMapper extends BaseMapper<AdvertiserCategory> {

    default Optional<AdvertiserCategory> findByNameIgnoreCase(String name) {
        LambdaQueryWrapper<AdvertiserCategory> query = new LambdaQueryWrapper<AdvertiserCategory>()
                .apply("LOWER(name) = LOWER({0})", name)
                .last("LIMIT 1");
        return Optional.ofNullable(selectOne(query));
    }
}
