package com.example.spring_crud_demo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI jewelryStoreOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Jewelry Store REST API")
                        .version("1.0.0")
                        .description("""
                                RESTful API for managing jewelry products.
                                Supports product retrieval, creation,
                                updating, deletion, filtering and pagination.
                                """)
                        .contact(new Contact()
                                .name("Jewelry Store Development ThuNT")));
    }

    @Bean
    public GroupedOpenApi v1Api() {
        return GroupedOpenApi.builder()
                .group("v1")
                .pathsToMatch("/api/v1/**")
                .build();
    }

    @Bean
    public GroupedOpenApi v2Api() {
        return GroupedOpenApi.builder()
                .group("v2")
                .pathsToMatch("/api/v2/**")
                .build();
    }
}