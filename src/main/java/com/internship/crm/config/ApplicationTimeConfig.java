package com.internship.crm.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides the application clock so time-based defaults remain deterministic in tests. */
@Configuration
public class ApplicationTimeConfig {

    @Bean
    Clock applicationClock() {
        return Clock.systemDefaultZone();
    }
}
