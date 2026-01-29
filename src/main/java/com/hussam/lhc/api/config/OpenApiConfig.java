package com.hussam.lhc.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configures OpenAPI/Swagger documentation for the REST API.
 * <p>
 * Provides API metadata and server information for Swagger UI at /swagger-ui.html
 * </p>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI lhcEventProcessorOpenAPI() {
        Server server = new Server();
        server.setUrl("http://localhost:8080");
        server.description("Development server");

        Contact contact = new Contact();
        contact.setEmail("contact@hussam.com");
        contact.setName("Hussam");
        contact.setUrl("https://github.com/hussam");

        License license = new License()
                .name("MIT License")
                .url("https://choosealicense.com/licenses/mit/");

        Info info = new Info()
                .title("LHC Event Processor API")
                .version("1.0.0")
                .contact(contact)
                .description("REST API for querying particle collision events from the LHC Event Processor system. " +
                        "This API provides endpoints to retrieve high-energy particle events, " +
                        "view system statistics, and monitor processing status.")
                .license(license);

        return new OpenAPI()
                .info(info)
                .servers(List.of(server));
    }
}
