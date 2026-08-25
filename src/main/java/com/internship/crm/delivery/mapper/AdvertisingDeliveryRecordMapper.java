package com.internship.crm.delivery.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.internship.crm.delivery.dto.response.AdvertisingDeliveryRecordResponse;
import com.internship.crm.delivery.entity.AdvertisingDeliveryRecord;
import java.time.LocalDate;
import java.util.Optional;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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

    @Delete("""
            DELETE FROM advertising_delivery_records record
            WHERE record.id = #{id}
              AND NOT EXISTS (
                  SELECT 1
                  FROM advertiser_account_transactions account_transaction
                  WHERE account_transaction.advertising_delivery_record_id = record.id
              )
            """)
    int deleteIfUnreferenced(@Param("id") Long id);

    @Select("""
            SELECT
                record.id,
                record.external_record_no,
                advertiser.id AS advertiser_id,
                advertiser.name AS advertiser_name,
                advertising_type.id AS advertising_type_id,
                advertising_type.code AS advertising_type_code,
                advertising_type.name AS advertising_type_name,
                record.record_date,
                record.impressions,
                record.clicks,
                record.conversions,
                record.spend,
                record.created_at,
                record.updated_at
            FROM advertising_delivery_records record
            JOIN advertisers advertiser ON advertiser.id = record.advertiser_id
            JOIN advertising_types advertising_type ON advertising_type.id = record.advertising_type_id
            WHERE record.id = #{id}
            """)
    AdvertisingDeliveryRecordResponse selectDetailById(@Param("id") Long id);

    @Select("""
            <script>
            SELECT
                record.id,
                record.external_record_no,
                advertiser.id AS advertiser_id,
                advertiser.name AS advertiser_name,
                advertising_type.id AS advertising_type_id,
                advertising_type.code AS advertising_type_code,
                advertising_type.name AS advertising_type_name,
                record.record_date,
                record.impressions,
                record.clicks,
                record.conversions,
                record.spend,
                record.created_at,
                record.updated_at
            FROM advertising_delivery_records record
            JOIN advertisers advertiser ON advertiser.id = record.advertiser_id
            JOIN advertising_types advertising_type ON advertising_type.id = record.advertising_type_id
            <where>
                <if test="startDate != null">
                    record.record_date &gt;= #{startDate}
                </if>
                <if test="endDate != null">
                    AND record.record_date &lt;= #{endDate}
                </if>
                <if test="advertiserId != null">
                    AND record.advertiser_id = #{advertiserId}
                </if>
                <if test="advertisingTypeId != null">
                    AND record.advertising_type_id = #{advertisingTypeId}
                </if>
            </where>
            ORDER BY record.record_date DESC, record.id DESC
            </script>
            """)
    Page<AdvertisingDeliveryRecordResponse> selectPageWithDetails(
            Page<AdvertisingDeliveryRecordResponse> page,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("advertiserId") Long advertiserId,
            @Param("advertisingTypeId") Long advertisingTypeId);

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
