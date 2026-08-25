package com.internship.crm.delivery.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.internship.crm.delivery.entity.AdvertisingDeliveryRecord;
import java.util.Optional;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

/** ORM data access entry point for advertising delivery records. */
@Mapper
public interface AdvertisingDeliveryRecordMapper extends BaseMapper<AdvertisingDeliveryRecord> {

    @Insert("""
            INSERT INTO advertising_delivery_records (
                external_record_no,
                advertiser_id,
                advertising_type_id,
                record_date,
                impressions,
                clicks,
                conversions,
                spend,
                created_at,
                updated_at
            ) VALUES (
                #{externalRecordNo},
                #{advertiserId},
                #{advertisingTypeId},
                #{recordDate},
                #{impressions},
                #{clicks},
                #{conversions},
                #{spend},
                #{createdAt},
                #{updatedAt}
            )
            ON CONFLICT (external_record_no) DO NOTHING
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertIfExternalRecordNoAbsent(AdvertisingDeliveryRecord record);

    @SuppressWarnings("null") // The ORM's serializable getter references lack nullability metadata.
    default Optional<AdvertisingDeliveryRecord> findByExternalRecordNo(String externalRecordNo) {
        LambdaQueryWrapper<AdvertisingDeliveryRecord> query =
                new LambdaQueryWrapper<AdvertisingDeliveryRecord>()
                        .eq(AdvertisingDeliveryRecord::getExternalRecordNo, externalRecordNo)
                        .last("LIMIT 1");
        return Optional.ofNullable(selectOne(query));
    }

    @SuppressWarnings("null") // The ORM's serializable getter references lack nullability metadata.
    default boolean existsByAdvertiserId(Long advertiserId) {
        return selectCount(new LambdaQueryWrapper<AdvertisingDeliveryRecord>()
                .eq(AdvertisingDeliveryRecord::getAdvertiserId, advertiserId)) > 0;
    }
}
