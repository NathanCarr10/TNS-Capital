package com.neueda.leap.strategies;

import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.model.Account;

public interface OrderExecutionStrategy {
    void execute(Account account, PlaceOrderRequest request, String symbol);
}
