package com.dentalcare.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI dentalCareOpenApi() {
        return new OpenAPI().info(new Info()
                .title("DentalCare API")
                .version("1.0")
                .description("REST API for the DentalCare system"));
    }
}
