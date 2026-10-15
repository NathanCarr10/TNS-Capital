package com.neueda.leap.dtos;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class HolderNameValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<script>alert(1)</script>",
            "<img src=x onerror=alert(1)>",
            "\"><svg onload=alert(1)>",
            "javascript:alert(1)" })
    void rejectsScriptPayloads(String payload) {
        var create = new CreateAccountRequest("ACC-1", payload, BigDecimal.ONE);
        var update = new UpdateAccountRequest(payload);

        assertTrue(validator.validate(create).stream().anyMatch(v -> v.getMessage().equals(HolderName.MESSAGE)));
        assertTrue(validator.validate(update).stream().anyMatch(v -> v.getMessage().equals(HolderName.MESSAGE)));
    }

    @ParameterizedTest
    @ValueSource(strings = { "John Doe", "Zoë O'Neill-Smith", "Dr. Ada Lovelace, Jr." })
    void acceptsRealNames(String name) {
        assertTrue(validator.validate(new CreateAccountRequest("ACC-1", name, BigDecimal.ONE)).isEmpty());
        assertTrue(validator.validate(new UpdateAccountRequest(name)).isEmpty());
    }
}
