package com.neueda.leap.services;

import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.time.Clock;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.Order;
import com.neueda.leap.model.OrderHistory;
import com.neueda.leap.model.Position;
import com.neueda.leap.strategies.OrderExecutionStrategy;
import com.neueda.leap.utils.InputNormalizer;
import com.neueda.leap.exceptions.InsufficientFundsException;
import com.neueda.leap.exceptions.InsufficientHoldingsException;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.exceptions.InstrumentNotFoundException;
import com.neueda.leap.exceptions.OrderNotFoundException;
import com.neueda.leap.exceptions.OrderCancellationConflictException;
import com.neueda.leap.exceptions.NonRetryableOrderException;
import com.neueda.leap.exceptions.DuplicateOrderException;
import com.neueda.leap.exceptions.OrderPublishException;
import com.neueda.leap.exceptions.AccountNotActiveException;
import com.neueda.leap.exceptions.BrokerUnavailableException;
import com.neueda.leap.exceptions.SettlementException;
import com.neueda.leap.execution.BrokerSimulatorClient;
import com.neueda.leap.kafka.events.ExecutionEvent;
import com.neueda.leap.model.Execution;
import com.neueda.leap.repositories.ExecutionRepository;
import java.math.BigDecimal;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.InstrumentRepository;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.repositories.OrderHistoryRepository;
import com.neueda.leap.repositories.PositionRepository;
import com.neueda.leap.kafka.OrderEventPublisher;
import com.neueda.leap.kafka.TradeEventPublisher;
import com.neueda.leap.kafka.events.OrderEvent;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates order placement: validates, executes, and persists.
 */
@Service
@Transactional
@Slf4j
public class OrderService {
    private final AccountRepository accountRepository;
    private final InstrumentRepository instrumentRepository;
    private final OrderRepository orderRepository;
    private final OrderHistoryRepository orderHistoryRepository;
    private final PositionRepository positionRepository;
    private final OrderValidator validator;
    private final Map<OrderSide, OrderExecutionStrategy> strategies;
    private final Clock clock;
    private final OrderEventPublisher orderEventPublisher;
    private final TradeEventPublisher tradeEventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final BrokerSimulatorClient brokerClient;
    private final ExecutionRepository executionRepository;

    public OrderService(
            AccountRepository accountRepository,
            InstrumentRepository instrumentRepository,
            OrderRepository orderRepository,
            OrderHistoryRepository orderHistoryRepository,
            PositionRepository positionRepository,
            OrderValidator validator,
            Map<OrderSide, OrderExecutionStrategy> strategies,
            Clock clock,
            OrderEventPublisher orderEventPublisher,
            TradeEventPublisher tradeEventPublisher,
            PlatformTransactionManager transactionManager,
            BrokerSimulatorClient brokerClient,
            ExecutionRepository executionRepository) {
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.instrumentRepository = Objects.requireNonNull(instrumentRepository);
        this.orderRepository = Objects.requireNonNull(orderRepository);
        this.orderHistoryRepository = Objects.requireNonNull(orderHistoryRepository);
        this.positionRepository = Objects.requireNonNull(positionRepository);
        this.validator = Objects.requireNonNull(validator);
        this.strategies = Objects.requireNonNull(strategies);
        this.clock = Objects.requireNonNull(clock);
        this.orderEventPublisher = Objects.requireNonNull(orderEventPublisher);
        this.tradeEventPublisher = Objects.requireNonNull(tradeEventPublisher);
        this.transactionTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.brokerClient = Objects.requireNonNull(brokerClient);
        this.executionRepository = Objects.requireNonNull(executionRepository);
    }

    /**
     * Stores an order as NEW before it is published for execution, so it can be
     * looked up and cancelled while it waits.
     * 
     * The order keeps the client's idempotency key. Reusing a key is rejected
     * with DuplicateOrderException (ORD-409); two requests racing with the same
     * key are caught by the unique constraint on flush (also ORD-409).
     * 
     * Commits before returning, so the order exists before its event is
     * published and the consumer can always find it.
     *
     * @param request the validated order request
     * @return the stored NEW order
     * @throws DuplicateOrderException if the idempotency key was already used
     */
    public Order acceptOrder(PlaceOrderRequest request) {
        Objects.requireNonNull(request);

        String idempotencyKey = InputNormalizer.normalize(request.idempotencyKey());
        if (orderRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            throw new DuplicateOrderException("Order already submitted: " + request.idempotencyKey());
        }

        Order order = new Order(request.accountId(), request.symbol(), request.side(), request.quantity(),
                request.price(), request.idempotencyKey(), clock);
        return orderRepository.saveAndFlush(order);
    }

    /**
     * Places an order: checks the account and instrument exist, stores the
     * order as NEW (see {@link #acceptOrder}) and publishes it to the orders
     * topic for the Trade Executor.
     * 
     * Runs without a surrounding transaction so the NEW order is committed
     * before its event is published; the consumer can then always find it.
     *
     * @param request the validated order request
     * @return the stored NEW order
     * @throws AccountNotFoundException    if the account does not exist
     * @throws InstrumentNotFoundException if the instrument does not exist
     * @throws DuplicateOrderException     if the idempotency key was already used
     * @throws OrderPublishException       if Kafka could not take the order; it
     *                                     is then stored as REJECTED
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Order submitOrder(PlaceOrderRequest request) {
        Objects.requireNonNull(request);

        // Fail fast: these cannot succeed later, so don't store or queue the order
        accountRepository.findById(request.accountId())
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + request.accountId()));
        String symbol = InputNormalizer.normalize(request.symbol());
        instrumentRepository.findBySymbol(symbol)
                .orElseThrow(() -> new InstrumentNotFoundException("Instrument not found: " + symbol));

        Order order = transactionTemplate.execute(status -> acceptOrder(request));
        try {
            orderEventPublisher.publish(order);
        } catch (OrderPublishException ex) {
            // Never left at NEW: an order that could not be queued will not be executed
            rejectUnqueuedOrder(order, ex);
            throw ex;
        }
        log.info("Order published for async processing: orderId={}, accountId={}, symbol={}",
                order.getId(), order.getAccountId(), order.getSymbol());
        return order;
    }

    private void rejectUnqueuedOrder(Order order, OrderPublishException cause) {
        try {
            Order rejected = transactionTemplate.execute(status -> {
                Order stored = orderRepository.findWithLockById(order.getId()).orElse(order);
                if (stored.getStatus() != OrderStatus.NEW) {
                    return null;
                }
                stored.setStatus(OrderStatus.REJECTED);
                stored.setStatusReason("Could not be queued for execution: " + cause.getMessage());
                return orderRepository.save(stored);
            });
            if (rejected != null) {
                tradeEventPublisher.publish(rejected, OrderStatus.NEW, rejected.getStatusReason());
            }
        } catch (RuntimeException ex) {
            log.error("Failed to mark unqueued order REJECTED: orderId={}, error={}", order.getId(), ex.getMessage());
        }
    }

    /**
     * @return every order (admin view)
     */
    @Transactional(readOnly = true)
    public List<Order> findAllOrders() {
        return orderRepository.findAll();
    }

    /**
     * @throws OrderNotFoundException if there is no such order
     */
    @Transactional(readOnly = true)
    public Order getOrder(UUID orderId) {
        Objects.requireNonNull(orderId, "Order ID cannot be null");
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
    }

    /**
     * The archived copy of a cancelled order.
     *
     * @throws OrderNotFoundException if the order has no history record
     */
    @Transactional(readOnly = true)
    public OrderHistory getOrderHistory(UUID orderId) {
        return orderHistoryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order history not found: " + orderId));
    }

    /**
     * Archived cancelled orders for an account; works even for closed accounts.
     */
    @Transactional(readOnly = true)
    public List<OrderHistory> getAccountOrderHistory(Long accountId) {
        return orderHistoryRepository.findByAccountId(accountId);
    }

    /**
     * The Trade Executor: works an order event from the orders topic.
     * 
     * Called by OrderMessageListener. Validates the order, checks the account
     * can afford it at its limit price (cash for a BUY, holdings for a SELL)
     * and sends it to the Broker Simulator. Nothing moves yet: the order stays
     * NEW until the broker's fill arrives on the executions topic and
     * {@link #settleExecution} settles it.
     * 
     * TRANSACTION BOUNDARIES: The checks run in their own transaction, which is
     * committed before the order goes to the broker. If a check fails, or the
     * broker cannot be reached, the order is persisted as REJECTED in a second,
     * independent transaction, so the rejection survives the exception that is
     * rethrown to the Kafka error handler.
     * 
     * TRADE EVENTS: Exactly one lifecycle event is published per order, and only
     * after the corresponding status has been committed (FILLED or REJECTED).
     * 
     * DEFENSIVE VALIDATION: Even though REST layer validates Account/Instrument
     * existence, this method also validates defensively to catch race conditions
     * (e.g., account deleted between REST validation and async processing).
     * Missing resources are wrapped in NonRetryableOrderException so the DLQ
     * records them as not replayable.
     * 
     * NO RETRY LOGIC: All exceptions are terminal - the order is persisted with
     * REJECTED status and the exception is rethrown so the message is routed to
     * the DLQ.
     * 
     * @param event the order event to process
     * @return the order, NEW and with the broker (or its final status if it
     *         was already processed or cancelled)
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Order processOrderEvent(OrderEvent event) {
        return processOrderEvent(event, false);
    }

    /**
     * Re-processes an order event that was routed to the DLQ.
     * 
     * Unlike {@link #processOrderEvent(OrderEvent)}, an existing REJECTED order is
     * not treated as already processed: it is checked and sent to the broker
     * again, replacing the REJECTED record. If the replay fails again, the original REJECTED
     * order is kept and no further trade event is published.
     * 
     * @param event the order event to replay
     * @return the processed order
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Order replayOrderEvent(OrderEvent event) {
        return processOrderEvent(event, true);
    }

    private Order processOrderEvent(OrderEvent event, boolean replay) {
        Objects.requireNonNull(event, "Order event cannot be null");

        // Idempotency check: a redelivered message must not execute the order twice.
        // A NEW order was stored by acceptOrder and is waiting for this message; any
        // other status means it was already processed or cancelled.
        Optional<Order> existingOrder = orderRepository.findById(event.orderId());
        boolean replayingRejected = replay && existingOrder.isPresent()
                && existingOrder.get().getStatus() == OrderStatus.REJECTED;
        boolean waiting = existingOrder.isPresent() && existingOrder.get().getStatus() == OrderStatus.NEW;
        if (existingOrder.isPresent() && !waiting && !replayingRejected) {
            log.info("Order already exists, skipping re-processing: orderId={}, status={}",
                    event.orderId(), existingOrder.get().getStatus());
            return existingOrder.get();
        }

        Order order;
        try {
            order = transactionTemplate.execute(status -> {
                if (replayingRejected) {
                    // Remove the previous REJECTED attempt; restored if this attempt rolls back
                    orderRepository.delete(existingOrder.get());
                    orderRepository.flush();
                }
                return prepareOrderEvent(event);
            });
            if (order != null) {
                // Committed as NEW above, so the fill can always find the order
                brokerClient.submit(order);
            }
        } catch (AccountNotFoundException | InstrumentNotFoundException ex) {
            log.warn("Order rejected due to missing resource (non-retryable): orderId={}, error={}, type={}",
                    event.orderId(), ex.getMessage(), ex.getClass().getSimpleName());
            recordRejectedOrder(event, ex);
            throw new NonRetryableOrderException(ex.getMessage(), ex);
        } catch (InsufficientFundsException | InsufficientHoldingsException ex) {
            log.warn("Order rejected due to insufficient resources: orderId={}, error={}", event.orderId(),
                    ex.getMessage());
            recordRejectedOrder(event, ex);
            throw ex;
        } catch (BrokerUnavailableException ex) {
            // Retryable: an admin can replay it from the DLQ once the broker is back
            log.warn("Order rejected, broker unavailable: orderId={}, error={}", event.orderId(), ex.getMessage());
            recordRejectedOrder(event, ex);
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Unexpected error processing order event: orderId={}, error={}", event.orderId(),
                    ex.getMessage(), ex);
            recordRejectedOrder(event, ex);
            throw ex;
        }

        if (order == null) {
            log.info("Order is no longer NEW, not sending it to the broker: orderId={}", event.orderId());
            return orderRepository.findById(event.orderId()).orElse(null);
        }

        log.info("Order with the broker, awaiting its fill: orderId={}, accountId={}", event.orderId(),
                event.accountId());
        return order;
    }

    /**
     * Validates the order and checks the account can afford it; changes no
     * cash or positions. Must run inside a transaction.
     * 
     * Locks the stored NEW order first, so a cancellation cannot slip in
     * between the status check and sending the order to the broker.
     * 
     * @return the NEW order, saved, or null if the stored order is no longer
     *         NEW (cancelled while it waited)
     */
    private Order prepareOrderEvent(OrderEvent event) {
        Optional<Order> stored = orderRepository.findWithLockById(event.orderId());
        if (stored.isPresent() && stored.get().getStatus() != OrderStatus.NEW) {
            return null;
        }

        String symbol = InputNormalizer.normalize(event.symbol());

        // The client's idempotency key was checked by acceptOrder and is already on
        // the stored order, so the validator's duplicate check runs on the order ID
        PlaceOrderRequest request = new PlaceOrderRequest(
                event.accountId(),
                event.symbol(),
                event.side(),
                event.quantity(),
                event.price(),
                event.orderId().toString());

        validator.validate(request);

        // DEFENSIVE VALIDATION: account or instrument may have been deleted between
        // REST validation and async processing
        Account account = accountRepository.findById(event.accountId())
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + event.accountId()));
        instrumentRepository.findBySymbol(symbol)
                .orElseThrow(() -> new InstrumentNotFoundException("Instrument not found: " + symbol));

        checkAffordable(account, request, symbol);

        // Messages published before orders were stored as NEW (and DLQ replays,
        // which removed the REJECTED record) have no stored order yet
        return orderRepository.save(stored.orElseGet(() -> newOrderFromEvent(event)));
    }

    /**
     * Rejects an order the account cannot afford at its limit price. Checked
     * again at settlement, at the fill price, since other orders may have used
     * the cash or holdings in the meantime.
     */
    private void checkAffordable(Account account, PlaceOrderRequest request, String symbol) {
        if (request.side() == OrderSide.BUY) {
            BigDecimal cost = request.price().multiply(BigDecimal.valueOf(request.quantity()));
            if (account.getCashBalance().compareTo(cost) < 0) {
                throw new InsufficientFundsException("Insufficient funds for buy order");
            }
        } else {
            Position position = positionRepository.findByAccountIdAndSymbol(request.accountId(), symbol)
                    .orElseThrow(() -> new InsufficientHoldingsException("No position for symbol: " + symbol));
            if (position.getQuantity() < request.quantity()) {
                throw new InsufficientHoldingsException("Insufficient holdings for symbol: " + symbol);
            }
        }
    }

    /**
     * Settles a fill from the Broker Simulator (executions topic): moves the
     * cash and position at the fill price, records the execution and marks the
     * order FILLED, all in one transaction, then publishes the FILLED event.
     * 
     * A fill for an order that is no longer NEW is ignored: it was cancelled
     * while the broker worked it, or this fill is a redelivery. If the account
     * can no longer afford the order (another order used the cash or holdings
     * first) or is no longer active, nothing moves, the order is stored as
     * REJECTED and a SettlementException is thrown for the DLQ.
     *
     * @return the FILLED order, or null if the fill was ignored
     * @throws SettlementException if the order was rejected at settlement
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Order settleExecution(ExecutionEvent fill) {
        Objects.requireNonNull(fill, "Fill cannot be null");
        Order filled;
        try {
            filled = transactionTemplate.execute(status -> settle(fill));
        } catch (AccountNotFoundException | AccountNotActiveException | InsufficientFundsException
                | InsufficientHoldingsException ex) {
            log.warn("Order rejected at settlement: orderId={}, error={}", fill.orderId(), ex.getMessage());
            OrderEvent event = orderRepository.findById(fill.orderId()).map(this::eventFromOrder).orElse(null);
            if (event == null) {
                throw ex;
            }
            recordRejectedOrder(event, ex);
            throw new SettlementException(event, ex);
        }

        if (filled == null) {
            log.info("Fill ignored, order is not NEW: orderId={}", fill.orderId());
            return null;
        }
        log.info("Order FILLED: orderId={}, quantity={}, price={}", filled.getId(), fill.quantity(), fill.price());
        tradeEventPublisher.publish(filled, OrderStatus.NEW, null);
        return filled;
    }

    private Order settle(ExecutionEvent fill) {
        Order order = orderRepository.findWithLockById(fill.orderId()).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.NEW) {
            return null;
        }

        Account account = accountRepository.findById(order.getAccountId())
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + order.getAccountId()));
        if (!account.isActive()) {
            throw new AccountNotActiveException("Account not active: " + order.getAccountId());
        }
        OrderExecutionStrategy strategy = strategies.get(order.getSide());
        if (strategy == null) {
            throw new IllegalStateException("No strategy registered for order side: " + order.getSide());
        }

        // The strategies re-check funds or holdings, at the fill price this time
        PlaceOrderRequest atFillPrice = new PlaceOrderRequest(order.getAccountId(), order.getSymbol(),
                order.getSide(), fill.quantity(), fill.price(), order.getIdempotencyKey());
        strategy.execute(account, atFillPrice, order.getSymbol());

        order.setStatus(OrderStatus.FILLED);
        orderRepository.save(order);
        executionRepository.save(new Execution(fill.executionId(), order.getId(), fill.quantity(), fill.price(),
                fill.venue(), fill.executedOn()));
        return order;
    }

    private OrderEvent eventFromOrder(Order order) {
        return new OrderEvent(order.getId(), order.getAccountId(), order.getSymbol(), order.getSide(),
                order.getQuantity(), order.getPrice(), order.getCreatedOn(), order.getIdempotencyKey());
    }

    /**
     * Persists the order as REJECTED in its own transaction and publishes the
     * REJECTED trade event once it is committed.
     * 
     * Updates the stored NEW order. Does nothing if the order already has a
     * final status (e.g. a failed DLQ replay of an order that was already
     * rejected), so each order produces at most one REJECTED event. Failures here are logged and swallowed so the original
     * exception still reaches the Kafka error handler.
     */
    private void recordRejectedOrder(OrderEvent event, RuntimeException cause) {
        String reason = cause.getMessage();
        try {
            Order rejectedOrder = transactionTemplate.execute(status -> {
                Optional<Order> stored = orderRepository.findWithLockById(event.orderId());
                if (stored.isPresent() && stored.get().getStatus() != OrderStatus.NEW) {
                    return null;
                }
                Order order = stored.orElseGet(() -> newOrderFromEvent(event));
                order.setStatus(OrderStatus.REJECTED);
                order.setStatusReason(DeadLetterService.buildFailureReason(cause));
                orderRepository.save(order);
                return order;
            });

            if (rejectedOrder == null) {
                log.info("Order already recorded, not saving REJECTED again: orderId={}", event.orderId());
                return;
            }

            log.info("Saved rejected order: orderId={}, reason={}", event.orderId(), reason);
            tradeEventPublisher.publish(rejectedOrder, OrderStatus.NEW, reason);
        } catch (RuntimeException ex) {
            log.error("Failed to record REJECTED order: orderId={}, error={}", event.orderId(), ex.getMessage(), ex);
        }
    }

    private Order newOrderFromEvent(OrderEvent event) {
        // Older messages carry no idempotency key; the order ID is unique too
        String idempotencyKey = event.idempotencyKey() != null ? event.idempotencyKey() : event.orderId().toString();
        Order order = new Order(
                event.accountId(),
                event.symbol(),
                event.side(),
                event.quantity(),
                event.price(),
                idempotencyKey,
                clock);
        order.setId(event.orderId());
        return order;
    }

    public Optional<Order> findByIdempotencyKey(String key) {
        return orderRepository.findByIdempotencyKey(key);
    }

    public Optional<Position> findPosition(Long accountId, String symbol) {
        return positionRepository.findByAccountIdAndSymbol(accountId, InputNormalizer.normalize(symbol));
    }

    /**
     * Retrieves all orders for a given account.
     *
     * @param accountId the account ID
     * @return list of orders for the account (empty list if no orders found)
     * @throws IllegalArgumentException if accountId is null
     */
    public List<Order> getOrdersByAccountId(Long accountId) {
        Objects.requireNonNull(accountId, "Account ID cannot be null");
        return orderRepository.findByAccountId(accountId);
    }

    /**
     * Cancels an order if its status is NEW.
     * 
     * Cancellation is only allowed for orders in NEW state.
     * Attempting to cancel FILLED, REJECTED, or CANCELLED orders throws a conflict
     * exception.
     * When an order is cancelled, it is archived to OrderHistory for audit trail
     * and a lifecycle event is published to Kafka.
     *
     * @param orderId the order ID to cancel
     * @return the cancelled order
     * @throws OrderNotFoundException             if order is not found
     * @throws OrderCancellationConflictException if order status is not NEW
     */
    public Order cancelOrder(UUID orderId) {
        Objects.requireNonNull(orderId, "Order ID cannot be null");

        // Locked so the order consumer cannot fill it while it is being cancelled
        Order order = orderRepository.findWithLockById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        // Validate cancellation is allowed (only NEW orders can be cancelled)
        if (order.getStatus() != OrderStatus.NEW) {
            throw new OrderCancellationConflictException(
                    String.format("Cannot cancel order in %s status. Only NEW orders can be cancelled.",
                            order.getStatus()));
        }

        // Track status transition for event
        OrderStatus previousStatus = order.getStatus();

        // Update order status to CANCELLED
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        // Archive cancelled order to history for audit trail
        OrderHistory history = new OrderHistory(order, clock);
        orderHistoryRepository.save(history);

        // Publish lifecycle event to Kafka
        tradeEventPublisher.publish(order, previousStatus, "User requested cancellation");

        return order;
    }
}
