package com.internship.crm.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.internship.crm.testsupport.ReadableTestResultExtension;
import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("应用业务时区配置")
@ExtendWith(ReadableTestResultExtension.class)
class ApplicationTimeConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(ApplicationTimeConfig.class);

    @Test
    @DisplayName("未配置业务时区时明确使用 UTC")
    void defaultsToUtc() {
        contextRunner.run(context -> {
            assertTrue(context.isRunning());
            assertEquals(ZoneId.of("UTC"), context.getBean(Clock.class).getZone());
        });
    }

    @Test
    @DisplayName("可以通过配置固定业务时区而不依赖服务器时区")
    void usesConfiguredBusinessZone() {
        contextRunner
                .withPropertyValues("app.business-zone=Australia/Sydney")
                .run(context -> {
                    assertTrue(context.isRunning());
                    assertEquals(
                            ZoneId.of("Australia/Sydney"),
                            context.getBean(Clock.class).getZone());
                });
    }
}
