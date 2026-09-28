package com.banking.processor.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI bankingOpenAPI() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Banking Transaction Processor API")
                                .description(
                                        "High-concurrency RESTful banking service supporting"
                                                + " account management, audit ledgers, deposits,"
                                                + " withdrawals, and inter-account transfers with"
                                                + " pessimistic locking.")
                                .version("v1.0.0")
                                .contact(
                                        new Contact()
                                                .name("Core Platform Engineering")
                                                .email("engineering@banking-kata.local"))
                                .license(
                                        new License()
                                                .name("Apache 2.0")
                                                .url(
                                                        "https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(
                        List.of(
                                new Server()
                                        .url("http://localhost:" + serverPort)
                                        .description("Local Development Environment")))
                .components(
                        new Components().addSchemas("ProblemDetail", rfc7807ProblemDetailSchema()));
    }

    /**
     * Standard RFC-7807 ProblemDetail Schema for consistent error representation across Swagger UI.
     */
    private Schema<?> rfc7807ProblemDetailSchema() {
        return new ObjectSchema()
                .description("RFC-7807 compliant error payload")
                .addProperty("type", new StringSchema().example("urn:problem:insufficient-funds"))
                .addProperty("title", new StringSchema().example("Insufficient Funds"))
                .addProperty("status", new IntegerSchema().example(422))
                .addProperty(
                        "detail",
                        new StringSchema()
                                .example(
                                        "Account ACC-100 has insufficient balance: 50.00,"
                                                + " requested: 100.00"))
                .addProperty("instance", new StringSchema().example("/api/v1/accounts/transfers"))
                .addProperty("timestamp", new DateTimeSchema().example("2026-09-28T16:15:30Z"))
                .addProperty(
                        "invalidFields",
                        new ObjectSchema()
                                .description("Validation error map for HTTP 400 bad requests")
                                .example("{\"amount\": \"must be at least 0.01\"}"));
    }
}
