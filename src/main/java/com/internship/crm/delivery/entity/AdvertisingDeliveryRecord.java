package com.internship.crm.delivery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Persistence model mapped to the PostgreSQL {@code advertising_delivery_records} table. */
@TableName("advertising_delivery_records")
public class AdvertisingDeliveryRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("external_record_no")
    private String externalRecordNo;

    @TableField("advertiser_id")
    private Long advertiserId;

    @TableField("advertising_type_id")
    private Long advertisingTypeId;

    @TableField("record_date")
    private LocalDate recordDate;

    private Long impressions;

    private Long clicks;

    private Long conversions;

    private BigDecimal spend;

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

    public String getExternalRecordNo() {
        return externalRecordNo;
    }

    public void setExternalRecordNo(String externalRecordNo) {
        this.externalRecordNo = externalRecordNo;
    }

    public Long getAdvertiserId() {
        return advertiserId;
    }

    public void setAdvertiserId(Long advertiserId) {
        this.advertiserId = advertiserId;
    }

    public Long getAdvertisingTypeId() {
        return advertisingTypeId;
    }

    public void setAdvertisingTypeId(Long advertisingTypeId) {
        this.advertisingTypeId = advertisingTypeId;
    }

    public LocalDate getRecordDate() {
        return recordDate;
    }

    public void setRecordDate(LocalDate recordDate) {
        this.recordDate = recordDate;
    }

    public Long getImpressions() {
        return impressions;
    }

    public void setImpressions(Long impressions) {
        this.impressions = impressions;
    }

    public Long getClicks() {
        return clicks;
    }

    public void setClicks(Long clicks) {
        this.clicks = clicks;
    }

    public Long getConversions() {
        return conversions;
    }

    public void setConversions(Long conversions) {
        this.conversions = conversions;
    }

    public BigDecimal getSpend() {
        return spend;
    }

    public void setSpend(BigDecimal spend) {
        this.spend = spend;
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
