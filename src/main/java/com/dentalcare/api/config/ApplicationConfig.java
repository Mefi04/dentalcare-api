package com.dentalcare.api.config;

import java.time.Clock;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@ConfigurationPropertiesScan(basePackages = "com.dentalcare.api")
@EnableAsync
public class ApplicationConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
