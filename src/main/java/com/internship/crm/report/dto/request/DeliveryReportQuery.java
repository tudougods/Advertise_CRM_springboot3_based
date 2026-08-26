package com.internship.crm.report.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

@Schema(description = "投放报表的通用筛选条件")
public record DeliveryReportQuery(
        @Schema(description = "统计开始日期；需与结束日期同时提供", example = "2026-08-01")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate startDate,
        @Schema(description = "统计结束日期；需与开始日期同时提供", example = "2026-08-30")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate endDate,
        @Schema(description = "广告主 ID", example = "1")
        @Positive(message = "广告主 ID 必须为正数")
        Long advertiserId,
        @Schema(description = "广告类型编码", example = "SEARCH")
        @Size(max = 30, message = "广告类型编码不能超过 30 个字符")
        @Pattern(regexp = ".*\\S.*", message = "广告类型编码不能为空白")
        String advertisingTypeCode) {
}
