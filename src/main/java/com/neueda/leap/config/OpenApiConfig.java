package com.neueda.leap.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI tradeApiOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Trade REST API")
                        .description("Order placement, cancellation, history, and portfolio/account endpoints")
                        .version("v1")
                        .contact(new Contact()
                                .name("Team Leap")));
    }
}