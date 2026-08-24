package com.internship.crm.advertiser.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.internship.crm.advertiser.dto.request.CreateAdvertiserRequest;
import com.internship.crm.advertiser.dto.response.AdvertiserResponse;
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
@DisplayName("广告主列表数据库分页")
@ExtendWith(ReadableTestResultExtension.class)
class AdvertiserPaginationPersistenceTest {

    @Autowired
    private AdvertiserService advertiserService;

    @Test
    @DisplayName("列表使用数据库物理分页并返回正确总数")
    void advertiserListUsesDatabasePagination() {
        long existingTotal = advertiserService.findAll(1, 1).total();
        String suffix = UUID.randomUUID().toString();
        advertiserService.create(advertiserRequest("pagination-first-" + suffix));
        AdvertiserResponse second = advertiserService.create(advertiserRequest("pagination-second-" + suffix));
        advertiserService.create(advertiserRequest("pagination-third-" + suffix));

        PageResponse<AdvertiserResponse> page = advertiserService.findAll(existingTotal + 2, 1);

        assertAll(
                () -> assertEquals(1, page.items().size()),
                () -> assertEquals(second.id(), page.items().getFirst().id()),
                () -> assertEquals(existingTotal + 3, page.total()),
                () -> assertEquals(existingTotal + 3, page.totalPages()));
    }

    private CreateAdvertiserRequest advertiserRequest(String name) {
        return new CreateAdvertiserRequest(name, null, null, null, null, null, null, null);
    }
}
