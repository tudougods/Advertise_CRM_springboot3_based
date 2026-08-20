package com.internship.crm.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Advertiser CRM API",
        version = "v1",
        description = "Backend API for the advertiser CRM prototype"
    )
)
public class OpenApiConfig {
}
