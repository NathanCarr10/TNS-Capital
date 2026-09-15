package com.neueda.leap.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EnumTypesTest {

    @Test
    void accountStatusContainsExpectedValues() {
        assertArrayEquals(
                new AccountStatus[]{AccountStatus.ACTIVE, AccountStatus.INACTIVE},
                AccountStatus.values()
        );
    }

    @Test
    void orderSideContainsExpectedValues() {
        assertArrayEquals(
                new OrderSide[]{OrderSide.BUY, OrderSide.SELL},
                OrderSide.values()
        );
    }

    @Test
    void orderStatusContainsExpectedValues() {
        assertArrayEquals(
                new OrderStatus[]{OrderStatus.WORKING, OrderStatus.FILLED, OrderStatus.CANCELLED},
                OrderStatus.values()
        );
    }

    @Test
    void orderStatusCanBeReadByName() {
        assertEquals(OrderStatus.CANCELLED, OrderStatus.valueOf("CANCELLED"));
    }
}