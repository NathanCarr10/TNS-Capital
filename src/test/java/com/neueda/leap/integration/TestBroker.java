package com.neueda.leap.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stand-in for the Broker Simulator in the integration tests: the same HTTP
 * contract (POST /api/v1/orders, 202) and the same fill message on the
 * executions topic, but it fills each order at once, at its limit price.
 * 
 * Tests can make it hold fills (to act on an order while the broker works
 * it) or refuse orders (broker down). Call {@link #reset()} afterwards.
 */
public final class TestBroker {
    private final HttpServer server;
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper json = new ObjectMapper();
    private final List<JsonNode> held = new ArrayList<>();
    private volatile boolean holdFills;
    private volatile boolean down;

    TestBroker(String kafkaBootstrapServers) {
        producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        server.createContext("/api/v1/orders", this::accept);
        server.start();
    }

    public String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    /** Accept orders but keep their fills until {@link #releaseFills()} */
    public void holdFills() {
        holdFills = true;
    }

    /** Answer every order with 503, like a broker that is down */
    public void goDown() {
        down = true;
    }

    /** Orders accepted while fills are held */
    public synchronized int heldCount() {
        return held.size();
    }

    public synchronized void releaseFills() {
        holdFills = false;
        held.forEach(this::fill);
        held.clear();
    }

    public synchronized void reset() {
        holdFills = false;
        down = false;
        held.clear();
    }

    private void accept(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (down) {
                exchange.sendResponseHeaders(503, -1);
                return;
            }
            JsonNode order = json.readTree(exchange.getRequestBody());
            synchronized (this) {
                if (holdFills) {
                    held.add(order);
                } else {
                    fill(order);
                }
            }
            exchange.sendResponseHeaders(202, -1);
        }
    }

    private void fill(JsonNode order) {
        Map<String, Object> fill = new LinkedHashMap<>();
        fill.put("executionId", UUID.randomUUID().toString());
        fill.put("orderId", order.get("orderId").asText());
        fill.put("accountId", order.get("accountId").asLong());
        fill.put("symbol", order.get("symbol").asText());
        fill.put("side", order.get("side").asText());
        fill.put("quantity", order.get("quantity").asInt());
        fill.put("price", order.get("limitPrice").decimalValue());
        fill.put("venue", "TEST");
        fill.put("executedOn", Instant.now().toString());
        try {
            producer.send(new ProducerRecord<>("executions", order.get("accountId").asText(),
                    json.writeValueAsString(fill))).get();
        } catch (Exception ex) {
            throw new IllegalStateException("Test broker could not publish a fill", ex);
        }
    }
}
