package com.internship.crm.advertiser.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.internship.crm.advertiser.dto.request.CreateAdvertiserCategoryRequest;
import com.internship.crm.advertiser.dto.response.AdvertiserCategoryResponse;
import com.internship.crm.advertiser.entity.AdvertiserStatus;
import com.internship.crm.common.response.PageResponse;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties =
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@Transactional
@DisplayName("广告主分类列表数据库分页")
@ExtendWith(ReadableTestResultExtension.class)
class AdvertiserCategoryPaginationPersistenceTest {

    @Autowired
    private AdvertiserCategoryService categoryService;

    @Test
    @DisplayName("分类列表使用数据库物理分页并保持展示顺序")
    void categoryListUsesDatabasePaginationAndStableOrdering() {
        long existingTotal = categoryService.findAll(1, 1).total();
        String suffix = UUID.randomUUID().toString();
        categoryService.create(categoryRequest("pagination-first-" + suffix));
        AdvertiserCategoryResponse second = categoryService.create(
                categoryRequest("pagination-second-" + suffix));
        categoryService.create(categoryRequest("pagination-third-" + suffix));

        PageResponse<AdvertiserCategoryResponse> page = categoryService.findAll(existingTotal + 2, 1);

        assertAll(
                () -> assertEquals(1, page.items().size()),
                () -> assertEquals(second.id(), page.items().getFirst().id()),
                () -> assertEquals(existingTotal + 3, page.total()),
                () -> assertEquals(existingTotal + 3, page.totalPages()));
    }

    private CreateAdvertiserCategoryRequest categoryRequest(String name) {
        return new CreateAdvertiserCategoryRequest(
                name, null, AdvertiserStatus.ACTIVE, Integer.MAX_VALUE);
    }
}
