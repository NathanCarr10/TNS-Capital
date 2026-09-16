package com.neueda.leap.strategies;

import com.neueda.leap.domain.Account;
import com.neueda.leap.dtos.PlaceOrderRequest;

public interface OrderExecutionStrategy {
    void execute(Account account, PlaceOrderRequest request, String symbol);
}
