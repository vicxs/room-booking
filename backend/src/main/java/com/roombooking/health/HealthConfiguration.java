package com.roombooking.health;

import com.roombooking.health.domain.DatabaseProbe;
import com.roombooking.health.domain.HealthCheck;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class HealthConfiguration {

    @Bean
    HealthCheck healthCheck(DatabaseProbe databaseProbe) {
        return new HealthCheck(databaseProbe);
    }
}
