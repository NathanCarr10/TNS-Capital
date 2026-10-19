package com.neueda.leap.metrics;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Serves the metrics in Prometheus text format at GET /metrics on their own
 * port (METRICS_PORT, 9464 in Docker Compose), for the metrics store to scrape.
 *
 * The API port keeps /actuator/** for admins only (SecurityConfig), and the
 * scraper has no token to send. So the metrics get their own port, which
 * Compose does not publish and nginx does not proxy: only containers on the
 * Compose network can reach it. Off unless METRICS_PORT is set.
 */
@Component
@Slf4j
@ConditionalOnExpression("${metrics.port:0} > 0")
public class MetricsServer {
    private final PrometheusMeterRegistry registry;
    private final int port;
    private HttpServer server;

    public MetricsServer(PrometheusMeterRegistry registry, @Value("${metrics.port}") int port) {
        this.registry = registry;
        this.port = port;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/metrics", this::handle);
        // The default executor runs requests on the server's one thread: a scrape every 10 s needs no more
        server.start();
        log.info("Serving metrics on port {}", port);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"GET".equals(exchange.getRequestMethod()) || !"/metrics".equals(exchange.getRequestURI().getPath())) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            byte[] body = registry.scrape().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }
}
