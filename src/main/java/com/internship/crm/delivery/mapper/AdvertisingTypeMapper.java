package com.internship.crm.delivery.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.internship.crm.delivery.entity.AdvertisingType;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

/** ORM data access entry point for advertising type dictionary entries. */
@Mapper
public interface AdvertisingTypeMapper extends BaseMapper<AdvertisingType> {

    default Optional<AdvertisingType> findByCodeIgnoreCase(String code) {
        LambdaQueryWrapper<AdvertisingType> query = new LambdaQueryWrapper<AdvertisingType>()
                .apply("LOWER(code) = LOWER({0})", code)
                .last("LIMIT 1");
        return Optional.ofNullable(selectOne(query));
    }
}
