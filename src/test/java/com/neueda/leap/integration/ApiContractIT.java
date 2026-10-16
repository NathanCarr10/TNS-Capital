package com.neueda.leap.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The committed API contract (contracts/trading-api.json) must match what the
 * running API documents at /v3/api-docs. The Angular clients are generated
 * from the committed file, so a change to a controller or DTO that is not
 * copied into it would leave the frontend coding against an old API.
 *
 * When this fails, the live spec has been written to target/trading-api.json.
 * Review the difference, then update the contract in its usual format with
 *   python3 -m json.tool --indent 2 target/trading-api.json contracts/trading-api.json
 */
@DisplayName("API contract")
class ApiContractIT extends AbstractIntegrationTest {

    private static final Path CONTRACT = Path.of("contracts/trading-api.json");
    private static final Path LIVE_COPY = Path.of("target/trading-api.json");

    @Autowired
    private MockMvc mockMvc;

    // Decimals read as BigDecimal, so bounds such as 999999.99 keep their exact value
    private final ObjectMapper json = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    @Test
    @WithAnonymousUser
    @DisplayName("contracts/trading-api.json matches the live OpenAPI spec")
    void contractMatchesLiveSpec() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode live = withContractServer(json.readTree(body));
        JsonNode committed = json.readTree(Files.readString(CONTRACT));

        boolean matches = committed.equals(SAME_NUMBER, live);
        if (!matches) {
            Files.createDirectories(LIVE_COPY.getParent());
            Files.writeString(LIVE_COPY, json.writeValueAsString(live));
        }
        assertTrue(matches, "contracts/trading-api.json is out of date. The live spec is in " + LIVE_COPY
                + "; review the difference, then run: python3 -m json.tool --indent 2 " + LIVE_COPY + " " + CONTRACT);
    }

    /**
     * Numbers are equal if they have the same value: the contract's 0 and the
     * live spec's 0.00 are the same minimum. Everything else compares as usual.
     */
    private static final Comparator<JsonNode> SAME_NUMBER = (a, b) -> {
        if (a.isNumber() && b.isNumber()) {
            return a.decimalValue().compareTo(b.decimalValue());
        }
        return a.equals(b) ? 0 : 1;
    };

    /**
     * The test server runs on a random port; the contract documents the
     * compose port, so use the contract's server entry.
     */
    private JsonNode withContractServer(JsonNode spec) throws Exception {
        ObjectNode copy = spec.deepCopy();
        copy.set("servers", json.readTree(Files.readString(CONTRACT)).get("servers"));
        return copy;
    }
}
