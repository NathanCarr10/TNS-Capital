package com.neueda.leap.config;

import com.neueda.leap.dtos.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The trading API's OpenAPI document (/v3/api-docs, Swagger UI), which is
 * also committed as contracts/trading-api.json for the Angular clients.
 *
 * Each controller method documents its success status and the errors it
 * can return with @ApiResponse, starting each error's description with its
 * code from the spec's catalogue (§21), e.g. "ACC-404 Account not found".
 * {@link #errorResponses()} then adds the errors every endpoint shares and
 * gives each coded error the ErrorResponse body.
 */
@Configuration
public class OpenApiConfig {

    private static final String ERROR_SCHEMA = "ErrorResponse";
    // A description that starts with a code such as ACC-404 has an ErrorResponse body
    private static final Pattern ERROR_CODE = Pattern.compile("^[A-Z]+-\\d{3}\\b.*");

    @Bean
    public OpenAPI tradeApiOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Trade REST API")
                        .description("Order placement, cancellation, history, and portfolio/account endpoints")
                        .version("v1")
                        .contact(new Contact()
                                .name("Team Leap")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new io.swagger.v3.oas.models.Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter the accessToken obtained from the auth service (POST /auth/login)")));
    }

    /**
     * Documents the error envelope on every endpoint:
     * - 401 AUTH-401 and 500 SYS-500, which any endpoint can return
     * - 422 VAL-422 on endpoints that take input
     * - the ErrorResponse body on every coded error; errors without a code
     *   (a few dead-letter queue answers) have no body
     */
    @Bean
    public OpenApiCustomizer errorResponses() {
        return openApi -> {
            ModelConverters.getInstance().read(ErrorResponse.class).forEach(openApi.getComponents()::addSchemas);
            Content errorBody = new Content().addMediaType("application/json",
                    new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + ERROR_SCHEMA)));

            openApi.getPaths().values().forEach(path -> path.readOperations()
                    .forEach(operation -> documentErrors(operation, errorBody)));
        };
    }

    private static void documentErrors(Operation operation, Content errorBody) {
        ApiResponses responses = operation.getResponses();
        responses.putIfAbsent("401", new ApiResponse().description("AUTH-401 Missing, invalid or expired access token"));
        if (takesInput(operation)) {
            responses.putIfAbsent("422", new ApiResponse().description("VAL-422 Invalid input"));
        }
        responses.putIfAbsent("500", new ApiResponse().description("SYS-500 Unexpected error"));

        responses.forEach((status, response) -> {
            if (isError(status)) {
                response.setContent(hasErrorCode(response) ? errorBody : null);
            }
        });
        operation.setResponses(sortedByStatus(responses));
    }

    private static boolean isError(String status) {
        return status.startsWith("4") || status.startsWith("5");
    }

    private static boolean hasErrorCode(ApiResponse response) {
        return response.getDescription() != null && ERROR_CODE.matcher(response.getDescription()).matches();
    }

    private static ApiResponses sortedByStatus(ApiResponses responses) {
        ApiResponses sorted = new ApiResponses();
        new TreeMap<>(responses).forEach(sorted::addApiResponse);
        return sorted;
    }

    private static boolean takesInput(Operation operation) {
        return operation.getRequestBody() != null
                || (operation.getParameters() != null && !operation.getParameters().isEmpty());
    }
}
