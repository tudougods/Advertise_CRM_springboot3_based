package com.internship.crm.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides an explicitly zoned application clock for business dates and timestamps. */
@Configuration
public class ApplicationTimeConfig {

    @Bean
    Clock applicationClock(@Value("${app.business-zone:UTC}") String businessZone) {
        return Clock.system(ZoneId.of(businessZone));
    }
}
