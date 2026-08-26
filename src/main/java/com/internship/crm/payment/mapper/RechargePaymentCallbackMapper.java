package com.internship.crm.payment.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.internship.crm.payment.entity.RechargePaymentCallback;
import java.util.Optional;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

/** ORM data access entry point for recharge payment callback audit records. */
@Mapper
public interface RechargePaymentCallbackMapper extends BaseMapper<RechargePaymentCallback> {

    @Insert("""
            INSERT INTO recharge_payment_callbacks (
                provider_event_id,
                recharge_order_id,
                callback_status,
                payload_hash,
                failure_reason,
                received_at,
                processed_at
            ) VALUES (
                #{providerEventId},
                #{rechargeOrderId},
                #{callbackStatus},
                #{payloadHash},
                #{failureReason},
                #{receivedAt},
                #{processedAt}
            )
            ON CONFLICT (provider_event_id) DO NOTHING
            """)
    @Options(
            flushCache = Options.FlushCachePolicy.TRUE,
            useCache = false,
            useGeneratedKeys = true,
            keyProperty = "id",
            keyColumn = "id")
    int insertIfProviderEventIdAbsent(RechargePaymentCallback callback);

    @SuppressWarnings("null") // The ORM's serializable getter references lack nullability metadata.
    default Optional<RechargePaymentCallback> findByProviderEventId(String providerEventId) {
        LambdaQueryWrapper<RechargePaymentCallback> query =
                new LambdaQueryWrapper<RechargePaymentCallback>()
                        .eq(RechargePaymentCallback::getProviderEventId, providerEventId)
                        .last("LIMIT 1");
        return Optional.ofNullable(selectOne(query));
    }
}
