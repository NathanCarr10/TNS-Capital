package com.neueda.leap.strategy.config;

import com.neueda.leap.strategy.web.dto.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The strategy service's OpenAPI document (/v3/api-docs), committed as
 * contracts/strategy-api.json for the Angular client. As in the trading API,
 * each error's description starts with its code, which gives it the
 * ErrorResponse body.
 */
@Configuration
public class OpenApiConfig {
    private static final Pattern ERROR_CODE = Pattern.compile("^[A-Z]+-\\d{3}\\b.*");

    @Bean
    public OpenAPI strategyApiOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Strategy API")
                        .description("Strategy Lab: backtest trading rules and run them on the live price stream")
                        .version("v1"))
                // Browsers reach it through the frontend's /api/v1/strategies proxy
                .servers(List.of(new Server().url("/").description("Same origin as the frontend")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("A user's accessToken from the auth service (POST /auth/login)")));
    }

    @Bean
    public OpenApiCustomizer errorResponses() {
        return openApi -> {
            ModelConverters.getInstance().read(ErrorResponse.class).forEach(openApi.getComponents()::addSchemas);
            Content errorBody = new Content().addMediaType("application/json",
                    new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")));
            openApi.getPaths().values().forEach(path -> path.readOperations()
                    .forEach(operation -> documentErrors(operation, errorBody)));
        };
    }

    private static void documentErrors(Operation operation, Content errorBody) {
        ApiResponses responses = operation.getResponses();
        responses.putIfAbsent("401", new ApiResponse().description("AUTH-401 Missing, invalid or expired access token"));
        responses.putIfAbsent("403", new ApiResponse().description("AUTH-403 Not allowed with this token"));
        responses.putIfAbsent("500", new ApiResponse().description("SYS-500 Unexpected error"));
        responses.forEach((status, response) -> {
            boolean coded = response.getDescription() != null && ERROR_CODE.matcher(response.getDescription()).matches();
            if (status.startsWith("4") || status.startsWith("5")) {
                response.setContent(coded ? errorBody : null);
            }
        });
        ApiResponses sorted = new ApiResponses();
        new TreeMap<>(responses).forEach(sorted::addApiResponse);
        operation.setResponses(sorted);
    }
}
