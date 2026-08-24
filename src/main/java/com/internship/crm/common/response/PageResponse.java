package com.internship.crm.common.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;

@Schema(description = "统一分页响应结构")
public record PageResponse<T>(
        @Schema(description = "当前页数据")
        List<T> items,
        @Schema(description = "当前页码，从 1 开始", example = "1")
        long page,
        @Schema(description = "每页数量", example = "20")
        long size,
        @Schema(description = "数据总数", example = "42")
        long total,
        @Schema(description = "总页数", example = "3")
        long totalPages) {

    public PageResponse {
        items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
        if (page < 1 || size < 1 || total < 0 || totalPages < 0) {
            throw new IllegalArgumentException("invalid pagination metadata");
        }
        if (totalPages != calculateTotalPages(total, size)) {
            throw new IllegalArgumentException("totalPages does not match total and size");
        }
    }

    public static <T> PageResponse<T> of(List<T> items, long page, long size, long total) {
        if (page < 1 || size < 1 || total < 0) {
            throw new IllegalArgumentException("invalid pagination metadata");
        }
        return new PageResponse<>(items, page, size, total, calculateTotalPages(total, size));
    }

    private static long calculateTotalPages(long total, long size) {
        return total == 0 ? 0 : ((total - 1) / size) + 1;
    }
}
