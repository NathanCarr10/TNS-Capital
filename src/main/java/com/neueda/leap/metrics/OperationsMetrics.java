package com.neueda.leap.metrics;

import com.neueda.leap.enums.DLQStatus;
import com.neueda.leap.marketdata.AlpacaPriceStream;
import com.neueda.leap.repositories.DeadLetterMessageRepository;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/**
 * Gauges read when the metrics are scraped (every 10 s):
 *
 * - trading.dlq.pending: dead letters awaiting an admin's review (OWASP A09-4)
 * - marketdata.stream.state{state}: 1 for the Alpaca stream's current state, 0 for the others
 * - marketdata.stream.trades: trades received from the stream
 */
@Component
public class OperationsMetrics implements MeterBinder {
    private final DeadLetterMessageRepository deadLetters;
    private final AlpacaPriceStream priceStream;

    public OperationsMetrics(DeadLetterMessageRepository deadLetters, AlpacaPriceStream priceStream) {
        this.deadLetters = deadLetters;
        this.priceStream = priceStream;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("trading.dlq.pending", deadLetters, repository -> repository.countByStatus(DLQStatus.PENDING))
                .description("Dead-letter messages awaiting review")
                .register(registry);
        for (AlpacaPriceStream.State state : AlpacaPriceStream.State.values()) {
            Gauge.builder("marketdata.stream.state", priceStream, stream -> stream.state() == state ? 1 : 0)
                    .description("1 for the Alpaca price stream's current state")
                    .tag("state", state.name())
                    .register(registry);
        }
        FunctionCounter.builder("marketdata.stream.trades", priceStream, AlpacaPriceStream::tradesReceived)
                .description("Trades received from the Alpaca price stream")
                .register(registry);
    }
}
