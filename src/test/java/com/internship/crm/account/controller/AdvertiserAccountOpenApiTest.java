package com.internship.crm.account.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.internship.crm.testsupport.ReadableTestResultExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties =
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@AutoConfigureMockMvc
@DisplayName("广告主账户 OpenAPI 文档")
@ExtendWith(ReadableTestResultExtension.class)
class AdvertiserAccountOpenApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("余额、消费和流水接口均被记录且流水不暴露修改删除操作")
    void documentsAccountEndpointsAndImmutableTransactionContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/advertisers/{advertiserId}/account'].get")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/advertisers/{advertiserId}/account/consumptions'].post")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/advertisers/{advertiserId}/account/transactions'].get")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/advertisers/{advertiserId}/account/transactions'].post")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/advertisers/{advertiserId}/account/transactions'].patch")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/advertisers/{advertiserId}/account/transactions'].delete")
                        .doesNotExist());
    }
}
