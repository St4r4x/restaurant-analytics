package com.st4r4x.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Restaurant Analytics API")
                        .version("2.0")
                        .description(
                                "REST API for analyzing New York City restaurant inspection data. " +
                                "Data is sourced nightly from the NYC Open Data API and stored in MongoDB. " +
                                "Expensive aggregations are cached in Redis (TTL 1h). " +
                                "See ARCHITECTURE.md for the full system design.")
                        .contact(new Contact()
                                .name("Restaurant Hygiene Control")
                                .url("https://github.com/St4r4x/restaurant-analytics")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local dev")));
    }
}
