package com.internship.crm.common.response;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.internship.crm.testsupport.ReadableTestResultExtension;

@DisplayName("统一分页响应结构")
@ExtendWith(ReadableTestResultExtension.class)
class PageResponseTest {

    @Test
    @DisplayName("分页工厂计算总页数并复制当前页数据")
    void factoryCalculatesTotalPagesAndCopiesItems() {
        List<String> source = new ArrayList<>(List.of("first", "second"));

        PageResponse<String> response = PageResponse.of(source, 2, 2, 5);
        source.clear();

        assertAll(
                () -> assertEquals(List.of("first", "second"), response.items()),
                () -> assertEquals(2, response.page()),
                () -> assertEquals(2, response.size()),
                () -> assertEquals(5, response.total()),
                () -> assertEquals(3, response.totalPages()),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> response.items().add("third")));
    }

    @Test
    @DisplayName("空结果使用零总数和零总页数")
    void emptyResultUsesZeroTotalPages() {
        PageResponse<String> response = PageResponse.of(List.of(), 1, 20, 0);

        assertAll(
                () -> assertEquals(List.of(), response.items()),
                () -> assertEquals(0, response.total()),
                () -> assertEquals(0, response.totalPages()));
    }

    @Test
    @DisplayName("分页响应拒绝不一致的元数据")
    void rejectsInconsistentPaginationMetadata() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> PageResponse.of(List.of(), 0, 20, 0)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> PageResponse.of(List.of(), 1, 0, 0)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new PageResponse<>(List.of(), 1, 20, 21, 1)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> PageResponse.of(List.of("one", "two"), 1, 1, 2)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> PageResponse.of(List.of("one", "two"), 1, 20, 1)));
    }
}
