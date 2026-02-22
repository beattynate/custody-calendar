package com.custodycalendar.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI custodyCalendarOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Custody Calendar API")
                        .version("v1")
                        .description("Custody scheduling API"));
    }
}
