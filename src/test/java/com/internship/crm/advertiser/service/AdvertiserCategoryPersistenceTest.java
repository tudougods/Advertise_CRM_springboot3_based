package com.internship.crm.advertiser.service;

import static org.junit.jupiter.api.Assertions.assertNull;

import com.internship.crm.advertiser.api.AdvertiserCategoryResponse;
import com.internship.crm.advertiser.api.AdvertiserResponse;
import com.internship.crm.advertiser.api.CreateAdvertiserCategoryRequest;
import com.internship.crm.advertiser.api.CreateAdvertiserRequest;
import com.internship.crm.advertiser.api.UpdateAdvertiserRequest;
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
@DisplayName("广告主分类数据库关系")
@ExtendWith(ReadableTestResultExtension.class)
class AdvertiserCategoryPersistenceTest {

    @Autowired
    private AdvertiserCategoryService categoryService;

    @Autowired
    private AdvertiserService advertiserService;

    @Test
    @DisplayName("删除分类后广告主保留且 categoryId 由外键置空")
    void deletingCategoryKeepsAdvertiserAndClearsCategoryId() {
        String suffix = UUID.randomUUID().toString();
        AdvertiserCategoryResponse category = categoryService.create(
                new CreateAdvertiserCategoryRequest("integration-category-" + suffix, null, null, 0));
        AdvertiserResponse advertiser = advertiserService.create(
                new CreateAdvertiserRequest(
                        "integration-advertiser-" + suffix,
                        null,
                        category.id(),
                        null,
                        null,
                        null,
                        null,
                        null));

        categoryService.delete(category.id());

        AdvertiserResponse reloaded = advertiserService.findById(advertiser.id());
        assertNull(reloaded.categoryId(), "删除分类后广告主的 categoryId 应由数据库设置为 null");
    }

    @Test
    @DisplayName("主动解除分类会把 categoryId 持久化为 null")
    void clearingCategoryPersistsANullCategoryId() {
        String suffix = UUID.randomUUID().toString();
        AdvertiserCategoryResponse category = categoryService.create(
                new CreateAdvertiserCategoryRequest("clear-category-" + suffix, null, null, 0));
        AdvertiserResponse advertiser = advertiserService.create(
                new CreateAdvertiserRequest(
                        "clear-advertiser-" + suffix,
                        null,
                        category.id(),
                        null,
                        null,
                        null,
                        null,
                        null));

        advertiserService.update(advertiser.id(), new UpdateAdvertiserRequest(
                null, null, null, true, null, null, null, null, null));

        AdvertiserResponse reloaded = advertiserService.findById(advertiser.id());
        assertNull(reloaded.categoryId(), "主动解除分类后 categoryId 应持久化为 null");
    }
}
