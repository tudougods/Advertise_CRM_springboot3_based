package com.internship.crm.payment.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

/** Persistence model mapped to {@code recharge_payment_callbacks}. */
@TableName("recharge_payment_callbacks")
public class RechargePaymentCallback {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("provider_event_id")
    private String providerEventId;

    @TableField("recharge_order_id")
    private Long rechargeOrderId;

    @TableField("callback_status")
    private PaymentCallbackStatus callbackStatus;

    @TableField("payload_hash")
    private String payloadHash;

    @TableField(value = "failure_reason", updateStrategy = FieldStrategy.ALWAYS)
    private String failureReason;

    @TableField("received_at")
    private OffsetDateTime receivedAt;

    @TableField(value = "processed_at", updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime processedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public void setProviderEventId(String providerEventId) {
        this.providerEventId = providerEventId;
    }

    public Long getRechargeOrderId() {
        return rechargeOrderId;
    }

    public void setRechargeOrderId(Long rechargeOrderId) {
        this.rechargeOrderId = rechargeOrderId;
    }

    public PaymentCallbackStatus getCallbackStatus() {
        return callbackStatus;
    }

    public void setCallbackStatus(PaymentCallbackStatus callbackStatus) {
        this.callbackStatus = callbackStatus;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public void setPayloadHash(String payloadHash) {
        this.payloadHash = payloadHash;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(OffsetDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(OffsetDateTime processedAt) {
        this.processedAt = processedAt;
    }
}
