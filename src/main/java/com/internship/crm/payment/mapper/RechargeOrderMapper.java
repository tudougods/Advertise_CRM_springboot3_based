package com.internship.crm.payment.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.internship.crm.payment.entity.RechargeOrder;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** ORM data access entry point for advertiser recharge orders. */
@Mapper
public interface RechargeOrderMapper extends BaseMapper<RechargeOrder> {

    @SuppressWarnings("null") // The ORM's serializable getter references lack nullability metadata.
    default Optional<RechargeOrder> findByOrderNo(String orderNo) {
        LambdaQueryWrapper<RechargeOrder> query = new LambdaQueryWrapper<RechargeOrder>()
                .eq(RechargeOrder::getOrderNo, orderNo)
                .last("LIMIT 1");
        return Optional.ofNullable(selectOne(query));
    }

    @Select("""
            SELECT *
            FROM recharge_orders
            WHERE order_no = #{orderNo}
            FOR UPDATE
            """)
    RechargeOrder selectByOrderNoForUpdate(@Param("orderNo") String orderNo);

    @SuppressWarnings("null") // The ORM's serializable getter references lack nullability metadata.
    default boolean existsByAdvertiserAccountId(Long advertiserAccountId) {
        return selectCount(new LambdaQueryWrapper<RechargeOrder>()
                .eq(RechargeOrder::getAdvertiserAccountId, advertiserAccountId)) > 0;
    }
}
