package com.internship.crm.delivery.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.internship.crm.delivery.entity.AdvertisingDeliveryRecord;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

/** ORM data access entry point for advertising delivery records. */
@Mapper
public interface AdvertisingDeliveryRecordMapper extends BaseMapper<AdvertisingDeliveryRecord> {

    @SuppressWarnings("null") // The ORM's serializable getter references lack nullability metadata.
    default Optional<AdvertisingDeliveryRecord> findByExternalRecordNo(String externalRecordNo) {
        LambdaQueryWrapper<AdvertisingDeliveryRecord> query =
                new LambdaQueryWrapper<AdvertisingDeliveryRecord>()
                        .eq(AdvertisingDeliveryRecord::getExternalRecordNo, externalRecordNo)
                        .last("LIMIT 1");
        return Optional.ofNullable(selectOne(query));
    }
}
