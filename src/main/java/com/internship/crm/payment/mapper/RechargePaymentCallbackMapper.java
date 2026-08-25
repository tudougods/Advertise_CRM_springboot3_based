package com.internship.crm.payment.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.internship.crm.payment.entity.RechargePaymentCallback;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

/** ORM data access entry point for recharge payment callback audit records. */
@Mapper
public interface RechargePaymentCallbackMapper extends BaseMapper<RechargePaymentCallback> {

    @SuppressWarnings("null") // The ORM's serializable getter references lack nullability metadata.
    default Optional<RechargePaymentCallback> findByProviderEventId(String providerEventId) {
        LambdaQueryWrapper<RechargePaymentCallback> query =
                new LambdaQueryWrapper<RechargePaymentCallback>()
                        .eq(RechargePaymentCallback::getProviderEventId, providerEventId)
                        .last("LIMIT 1");
        return Optional.ofNullable(selectOne(query));
    }
}
