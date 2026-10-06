package com.neueda.leap.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI offers two ways to authenticate (either one is enough):
 * - "login": OAuth2 password flow - type a username/password in the Authorize
 *   dialog and Swagger fetches the JWT from the auth service's /token endpoint
 * - "bearerAuth": paste a JWT obtained elsewhere (e.g. curl to /login)
 */
@Configuration
public class OpenApiConfig {
    static final String LOGIN_SCHEME = "login";
    static final String BEARER_SCHEME = "bearerAuth";

    // URL the *browser* uses to reach the auth service, so it must be reachable
    // from wherever Swagger UI is opened, not from inside the container
    @Value("${auth.token-url:http://localhost:4000/token}")
    private String tokenUrl;

    @Bean
    public OpenAPI tradeApiOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Trade REST API")
                        .description("Order placement, cancellation, history, and portfolio/account endpoints")
                        .version("v1")
                        .contact(new Contact()
                                .name("Team Leap")))
                // Separate requirements are alternatives: either scheme authorises a request
                .addSecurityItem(new SecurityRequirement().addList(LOGIN_SCHEME))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new io.swagger.v3.oas.models.Components()
                        .addSecuritySchemes(LOGIN_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.OAUTH2)
                                        .description("Log in with your username and password (leave client_id/secret empty)")
                                        .flows(new OAuthFlows()
                                                .password(new OAuthFlow()
                                                        .tokenUrl(tokenUrl)
                                                        .scopes(new Scopes()))))
                        .addSecuritySchemes(BEARER_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Or paste a JWT obtained from the auth service's /login endpoint")));
    }
}
