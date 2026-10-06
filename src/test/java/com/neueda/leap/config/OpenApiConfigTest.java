package com.neueda.leap.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class OpenApiConfigTest {

    @Test
    void offersPasswordLoginAndPastedTokenAsAlternatives() {
        OpenApiConfig config = new OpenApiConfig();
        ReflectionTestUtils.setField(config, "tokenUrl", "http://auth.example/token");

        OpenAPI openApi = config.tradeApiOpenAPI();

        SecurityScheme login = openApi.getComponents().getSecuritySchemes().get(OpenApiConfig.LOGIN_SCHEME);
        assertEquals(SecurityScheme.Type.OAUTH2, login.getType());
        assertEquals("http://auth.example/token", login.getFlows().getPassword().getTokenUrl());

        SecurityScheme bearer = openApi.getComponents().getSecuritySchemes().get(OpenApiConfig.BEARER_SCHEME);
        assertEquals("bearer", bearer.getScheme());

        // Two separate requirements = either one authorises the request
        assertEquals(2, openApi.getSecurity().size());
    }
}
