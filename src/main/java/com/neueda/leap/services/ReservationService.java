package com.neueda.leap.services;

import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.exceptions.InsufficientFundsException;
import com.neueda.leap.exceptions.InsufficientHoldingsException;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.Order;
import com.neueda.leap.model.Position;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.repositories.PositionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Holds the cash (BUY) or shares (SELL) an accepted order needs, so another
 * order or a withdrawal cannot use them while the broker works it.
 *
 * The Trade Executor reserves when it accepts the order. The hold is released
 * when the order is filled (settlement then moves the cash and shares at the
 * fill price), cancelled or rejected. {@link Order#isReserved()} records
 * whether an order holds one, so it is released exactly once.
 *
 * Every change locks the account row first; orders for the same account take
 * turns instead of both passing the check on the same cash or shares.
 */
@Service
public class ReservationService {
    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final OrderRepository orderRepository;

    public ReservationService(AccountRepository accountRepository, PositionRepository positionRepository,
            OrderRepository orderRepository) {
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.positionRepository = Objects.requireNonNull(positionRepository);
        this.orderRepository = Objects.requireNonNull(orderRepository);
    }

    /**
     * Reserves the order's cash (quantity x limit price) or shares. Does
     * nothing if the order already holds them, e.g. a redelivered message.
     *
     * @throws InsufficientFundsException    if the account's available cash is too low
     * @throws InsufficientHoldingsException if the account's available shares are too few
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(Order order) {
        if (order.isReserved()) {
            return;
        }
        Account account = lockAccount(order.getAccountId());
        if (order.getSide() == OrderSide.BUY) {
            if (account.getAvailableCash().compareTo(order.getValue()) < 0) {
                throw new InsufficientFundsException("Insufficient funds for buy order");
            }
            account.reserve(order.getValue());
            accountRepository.save(account);
        } else {
            Position position = position(order);
            position.reserve(order.getQuantity());
            positionRepository.save(position);
        }
        order.setReserved(true);
    }

    /**
     * Gives back what the order reserved. Does nothing if it holds nothing
     * (never accepted, or already released).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(Order order) {
        if (!order.isReserved()) {
            return;
        }
        Account account = lockAccount(order.getAccountId());
        if (order.getSide() == OrderSide.BUY) {
            account.release(order.getValue());
            accountRepository.save(account);
        } else {
            Position position = position(order);
            position.release(order.getQuantity());
            positionRepository.save(position);
        }
        order.setReserved(false);
    }

    /**
     * Stores a NEW order as REJECTED and releases what it reserved, in one
     * transaction. For callers outside a transaction, such as the Kafka
     * recovery handler.
     *
     * @return the rejected order, or empty if it is missing or no longer NEW
     */
    @Transactional
    public Optional<Order> rejectIfNew(UUID orderId, String reason) {
        Optional<Order> stored = orderRepository.findWithLockById(orderId);
        if (stored.isEmpty() || stored.get().getStatus() != OrderStatus.NEW) {
            return Optional.empty();
        }
        Order order = stored.get();
        release(order);
        order.setStatus(OrderStatus.REJECTED);
        order.setStatusReason(reason);
        return Optional.of(orderRepository.save(order));
    }

    private Account lockAccount(Long accountId) {
        return accountRepository.findWithLockById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
    }

    private Position position(Order order) {
        return positionRepository.findByAccountIdAndSymbol(order.getAccountId(), order.getSymbol())
                .orElseThrow(() -> new InsufficientHoldingsException("No position for symbol: " + order.getSymbol()));
    }
}
