package com.neueda.leap.execution;

import com.neueda.leap.exceptions.BrokerUnavailableException;
import com.neueda.leap.model.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

/**
 * The Trade Executor's connection to the Broker Simulator API: sends a
 * validated order to POST {broker.url}/api/v1/orders. The broker answers 202
 * straight away; the fill arrives later on the executions topic and is
 * settled by SettlementService.
 */
@Component
@Slf4j
public class BrokerSimulatorClient {
    private final RestClient restClient;

    public BrokerSimulatorClient(RestClient.Builder builder,
            @Value("${broker.url:http://localhost:8085}") String brokerUrl,
            @Value("${broker.timeout-ms:3000}") long timeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restClient = builder.baseUrl(brokerUrl).requestFactory(requestFactory).build();
    }

    /** The order as the broker's API expects it */
    record BrokerOrder(UUID orderId, Long accountId, String symbol, String side, Integer quantity,
            BigDecimal limitPrice) {
    }

    /**
     * Sends the order to the broker to be worked.
     *
     * @throws BrokerUnavailableException if the broker cannot be reached or rejects the order
     */
    public void submit(Order order) {
        BrokerOrder request = new BrokerOrder(order.getId(), order.getAccountId(), order.getSymbol(),
                order.getSide().name(), order.getQuantity(), order.getPrice());
        try {
            restClient.post()
                    .uri("/api/v1/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Order sent to the broker: orderId={}", order.getId());
        } catch (RestClientException ex) {
            throw new BrokerUnavailableException("Broker did not accept order " + order.getId() + ": "
                    + ex.getMessage(), ex);
        }
    }
}
