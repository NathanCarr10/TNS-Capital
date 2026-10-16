package com.neueda.leap.dtos;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.neueda.leap.enums.OrderSide;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Bean Validation rules on PlaceOrderRequest (spec §8.4 and §20 rules 4 and 5).
 * A request that breaks them is refused with 422 VAL-422 before any order logic runs.
 */
class PlaceOrderRequestValidationTest {

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

    private static PlaceOrderRequest request(Long accountId, String symbol, OrderSide side, Integer quantity,
            BigDecimal price, String idempotencyKey) {
        return new PlaceOrderRequest(accountId, symbol, side, quantity, price, idempotencyKey);
    }

    private static PlaceOrderRequest valid() {
        return request(1L, "ACME", OrderSide.BUY, 100, new BigDecimal("25.50"), "a1b2c3d4-e5f6");
    }

    /** The fields that failed validation */
    private static Set<String> invalidFields(PlaceOrderRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("The request from the spec's API example is valid")
    void testValidRequest() {
        assertTrue(validator.validate(valid()).isEmpty());
    }

    @Test
    @DisplayName("Price is optional: without one the order is priced at the live market price")
    void testValidRequestWithoutPrice() {
        assertTrue(validator.validate(request(1L, "ACME", OrderSide.SELL, 100, null, "key-1")).isEmpty());
    }

    @ParameterizedTest(name = "quantity {0}")
    @ValueSource(ints = { 0, -1, -100, 1_000_001 })
    @DisplayName("Quantity must be greater than 0 and at most 1,000,000 (rule 4)")
    void testInvalidQuantity(int quantity) {
        var request = request(1L, "ACME", OrderSide.BUY, quantity, new BigDecimal("25.50"), "key-1");

        assertEquals(Set.of("quantity"), invalidFields(request));
    }

    @ParameterizedTest(name = "price {0}")
    @ValueSource(strings = { "0", "0.00", "-25.50", "1000000.00" })
    @DisplayName("Price, when given, must be greater than 0 and at most 999,999.99 (rule 5)")
    void testInvalidPrice(String price) {
        var request = request(1L, "ACME", OrderSide.BUY, 100, new BigDecimal(price), "key-1");

        assertEquals(Set.of("price"), invalidFields(request));
    }

    static Stream<Arguments> requestsWithANullField() {
        BigDecimal price = new BigDecimal("25.50");
        return Stream.of(
                Arguments.of("accountId", request(null, "ACME", OrderSide.BUY, 100, price, "key-1")),
                Arguments.of("symbol", request(1L, null, OrderSide.BUY, 100, price, "key-1")),
                Arguments.of("side", request(1L, "ACME", null, 100, price, "key-1")),
                Arguments.of("quantity", request(1L, "ACME", OrderSide.BUY, null, price, "key-1")),
                Arguments.of("idempotencyKey", request(1L, "ACME", OrderSide.BUY, 100, price, null)));
    }

    @ParameterizedTest(name = "{0} is null")
    @MethodSource("requestsWithANullField")
    @DisplayName("Every field except price is required")
    void testNullFields(String field, PlaceOrderRequest request) {
        assertEquals(Set.of(field), invalidFields(request));
    }

    @Test
    @DisplayName("An empty request reports every required field")
    void testAllFieldsNull() {
        assertEquals(Set.of("accountId", "symbol", "side", "quantity", "idempotencyKey"),
                invalidFields(request(null, null, null, null, null, null)));
    }

    @ParameterizedTest(name = "symbol \"{0}\"")
    @ValueSource(strings = { "", " ", "acme", "AC ME", "ACME1", "TOOLONGSYMBOL", "<script>" })
    @DisplayName("Symbol must be 1-10 uppercase letters")
    void testInvalidSymbol(String symbol) {
        var request = request(1L, symbol, OrderSide.BUY, 100, new BigDecimal("25.50"), "key-1");

        assertEquals(Set.of("symbol"), invalidFields(request));
    }

    @ParameterizedTest(name = "idempotency key \"{0}\"")
    @ValueSource(strings = { "", " ", "key with spaces", "key;DROP TABLE orders", "<script>" })
    @DisplayName("Idempotency key must be letters, digits, '-' or '_'")
    void testInvalidIdempotencyKey(String key) {
        var request = request(1L, "ACME", OrderSide.BUY, 100, new BigDecimal("25.50"), key);

        assertEquals(Set.of("idempotencyKey"), invalidFields(request));
    }

    @Test
    @DisplayName("Idempotency key may be at most 100 characters")
    void testIdempotencyKeyLength() {
        var atLimit = request(1L, "ACME", OrderSide.BUY, 100, null, "k".repeat(100));
        var overLimit = request(1L, "ACME", OrderSide.BUY, 100, null, "k".repeat(101));

        assertTrue(validator.validate(atLimit).isEmpty());
        assertEquals(Set.of("idempotencyKey"), invalidFields(overLimit));
    }
}
