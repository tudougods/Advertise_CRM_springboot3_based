package com.internship.crm.report.controller;

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
@DisplayName("投放报表 OpenAPI 文档")
@ExtendWith(ReadableTestResultExtension.class)
class DeliveryReportOpenApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("四类报表接口均出现在 OpenAPI 文档中")
    void documentsAllDeliveryReportEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/reports/delivery/overview'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reports/delivery/trend'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reports/delivery/by-advertiser'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reports/delivery/by-ad-type'].get").exists());
    }
}
