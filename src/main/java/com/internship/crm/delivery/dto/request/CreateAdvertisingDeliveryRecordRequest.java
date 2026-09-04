package com.internship.crm.delivery.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "录入广告投放数据请求")
public record CreateAdvertisingDeliveryRecordRequest(
        @NotBlank(message = "外部投放记录号不能为空")
        @Size(max = 64, message = "外部投放记录号不能超过 64 个字符")
        @Schema(example = "DELIVERY-20260826-001")
        String externalRecordNo,

        @NotNull(message = "广告主 ID 不能为空")
        @Positive(message = "广告主 ID 必须为正数")
        @Schema(example = "1")
        Long advertiserId,

        @NotBlank(message = "广告类型编码不能为空")
        @Size(max = 30, message = "广告类型编码不能超过 30 个字符")
        @Schema(example = "SEARCH")
        String advertisingTypeCode,

        @NotNull(message = "投放日期不能为空")
        @Schema(example = "2026-08-26")
        LocalDate recordDate,

        @NotNull(message = "展示量不能为空")
        @PositiveOrZero(message = "展示量不能为负数")
        @Schema(example = "10000")
        Long impressions,

        @NotNull(message = "点击量不能为空")
        @PositiveOrZero(message = "点击量不能为负数")
        @Schema(example = "500")
        Long clicks,

        @NotNull(message = "转化量不能为空")
        @PositiveOrZero(message = "转化量不能为负数")
        @Schema(example = "30")
        Long conversions,

        @NotNull(message = "投放花费不能为空")
        @DecimalMin(value = "0.00", message = "投放花费不能为负数")
        @Digits(integer = 17, fraction = 2, message = "投放花费最多为 17 位整数和 2 位小数")
        @Schema(example = "300.00")
        BigDecimal spend) {

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "点击量不能超过展示量")
    public boolean isClicksWithinImpressions() {
        return impressions == null || clicks == null || clicks <= impressions;
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "转化量不能超过点击量")
    public boolean isConversionsWithinClicks() {
        return clicks == null || conversions == null || conversions <= clicks;
    }
}
