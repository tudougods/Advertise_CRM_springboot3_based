package com.internship.crm.payment.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Persistence model mapped to the PostgreSQL {@code recharge_orders} table. */
@TableName("recharge_orders")
public class RechargeOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("order_no")
    private String orderNo;

    @TableField("advertiser_account_id")
    private Long advertiserAccountId;

    private BigDecimal amount;

    private RechargeOrderStatus status;

    @TableField(value = "provider_transaction_no", updateStrategy = FieldStrategy.ALWAYS)
    private String providerTransactionNo;

    @TableField(value = "paid_at", updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime paidAt;

    @TableField("created_at")
    private OffsetDateTime createdAt;

    @TableField("updated_at")
    private OffsetDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getAdvertiserAccountId() {
        return advertiserAccountId;
    }

    public void setAdvertiserAccountId(Long advertiserAccountId) {
        this.advertiserAccountId = advertiserAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public RechargeOrderStatus getStatus() {
        return status;
    }

    public void setStatus(RechargeOrderStatus status) {
        this.status = status;
    }

    public String getProviderTransactionNo() {
        return providerTransactionNo;
    }

    public void setProviderTransactionNo(String providerTransactionNo) {
        this.providerTransactionNo = providerTransactionNo;
    }

    public OffsetDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(OffsetDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
