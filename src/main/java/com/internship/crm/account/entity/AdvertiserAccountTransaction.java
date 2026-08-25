package com.internship.crm.account.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Persistence model mapped to {@code advertiser_account_transactions}. */
@TableName("advertiser_account_transactions")
public class AdvertiserAccountTransaction {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("advertiser_account_id")
    private Long advertiserAccountId;

    @TableField("business_no")
    private String businessNo;

    @TableField("transaction_type")
    private AccountTransactionType transactionType;

    private BigDecimal amount;

    @TableField("balance_after")
    private BigDecimal balanceAfter;

    @TableField(value = "advertising_delivery_record_id", updateStrategy = FieldStrategy.ALWAYS)
    private Long advertisingDeliveryRecordId;

    @TableField(value = "recharge_order_id", updateStrategy = FieldStrategy.ALWAYS)
    private Long rechargeOrderId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;

    @TableField(value = "created_by", updateStrategy = FieldStrategy.ALWAYS)
    private Long createdBy;

    @TableField("created_at")
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAdvertiserAccountId() {
        return advertiserAccountId;
    }

    public void setAdvertiserAccountId(Long advertiserAccountId) {
        this.advertiserAccountId = advertiserAccountId;
    }

    public String getBusinessNo() {
        return businessNo;
    }

    public void setBusinessNo(String businessNo) {
        this.businessNo = businessNo;
    }

    public AccountTransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(AccountTransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public void setBalanceAfter(BigDecimal balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    public Long getAdvertisingDeliveryRecordId() {
        return advertisingDeliveryRecordId;
    }

    public void setAdvertisingDeliveryRecordId(Long advertisingDeliveryRecordId) {
        this.advertisingDeliveryRecordId = advertisingDeliveryRecordId;
    }

    public Long getRechargeOrderId() {
        return rechargeOrderId;
    }

    public void setRechargeOrderId(Long rechargeOrderId) {
        this.rechargeOrderId = rechargeOrderId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
