package com.internship.crm.delivery.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "局部修正广告投放数据请求；外部投放记录号创建后不可修改")
public record UpdateAdvertisingDeliveryRecordRequest(
        @Positive(message = "广告主 ID 必须为正数")
        Long advertiserId,

        @Size(max = 30, message = "广告类型编码不能超过 30 个字符")
        @Pattern(regexp = ".*\\S.*", message = "广告类型编码不能为空白")
        String advertisingTypeCode,

        LocalDate recordDate,

        @PositiveOrZero(message = "展示量不能为负数")
        Long impressions,

        @PositiveOrZero(message = "点击量不能为负数")
        Long clicks,

        @PositiveOrZero(message = "转化量不能为负数")
        Long conversions,

        @DecimalMin(value = "0.00", message = "投放花费不能为负数")
        @Digits(integer = 17, fraction = 2, message = "投放花费最多为 17 位整数和 2 位小数")
        BigDecimal spend) {
}
