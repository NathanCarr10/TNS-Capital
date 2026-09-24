package com.neueda.leap.config;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.model.Order;
import com.neueda.leap.model.Position;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.InstrumentRepository;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.repositories.PositionRepository;
import com.neueda.leap.time.Clock;

/**
 * Loads comprehensive seed data on application startup.
 * Works alongside Flyway: DataLoader runs first after schema creation,
 * checking if tables are empty before inserting seed data.
 * NOTE: For production, consider migrating to pure Flyway-based seed data.
 */
@Component
public class DataLoader implements CommandLineRunner {

    private final AccountRepository accountRepository;
    private final InstrumentRepository instrumentRepository;
    private final PositionRepository positionRepository;
    private final OrderRepository orderRepository;
    private final Clock clock;

    public DataLoader(AccountRepository accountRepository, InstrumentRepository instrumentRepository,
            PositionRepository positionRepository, OrderRepository orderRepository, Clock clock) {
        this.accountRepository = accountRepository;
        this.instrumentRepository = instrumentRepository;
        this.positionRepository = positionRepository;
        this.orderRepository = orderRepository;
        this.clock = clock;
    }

    @Override
    public void run(String... args) throws Exception {
        if (instrumentRepository.count() == 0) {
            loadInstruments();
            loadAccounts();
            loadPositions();
            loadOrders();
            System.out.println("✓ Seed data loaded successfully");
        }
    }

    private void loadInstruments() {
        Instrument[] instruments = {
                new Instrument("ACME", "Acme Corp", "EQUITY", "USD", true),
                new Instrument("GLOB", "Global Growth Fund", "FUND", "USD", true),
                new Instrument("BOND1", "Corporate Bond Fund", "BOND", "USD", true),
                new Instrument("TECH", "TechHub Inc", "EQUITY", "USD", true),
                new Instrument("GOOG", "Google Inc", "EQUITY", "USD", true),
                new Instrument("APPL", "Apple Inc", "EQUITY", "USD", true),
                new Instrument("MSFT", "Microsoft Corp", "EQUITY", "USD", true),
                new Instrument("GOLD", "Gold ETF", "COMMODITY", "USD", true),
                new Instrument("UTIL", "Utility Sector Fund", "FUND", "USD", true),
                new Instrument("HEMP", "Hemp Industries Fund", "FUND", "USD", true)
        };

        for (Instrument instrument : instruments) {
            instrumentRepository.save(instrument);
        }
        System.out.println("✓ Loaded " + instruments.length + " instruments");
    }

    private void loadAccounts() {
        Object[][] accountData = {
                {"ACC-1001", "John Doe", "5000.00", AccountStatus.ACTIVE},
                {"ACC-1002", "Jane Smith", "12000.00", AccountStatus.ACTIVE},
                {"ACC-1003", "Bob Lee", "1000.00", AccountStatus.SUSPENDED},
                {"ACC-1004", "Alice Johnson", "8500.00", AccountStatus.ACTIVE},
                {"ACC-1005", "Charlie Brown", "15000.00", AccountStatus.ACTIVE},
                {"ACC-1006", "Diana Prince", "2500.00", AccountStatus.ACTIVE},
                {"ACC-1007", "Eve Wilson", "20000.00", AccountStatus.ACTIVE},
                {"ACC-1008", "Frank Miller", "500.00", AccountStatus.SUSPENDED},
                {"ACC-1009", "Grace Hopper", "11000.00", AccountStatus.ACTIVE},
                {"ACC-1010", "Henry Foster", "7250.00", AccountStatus.ACTIVE},
        };

        for (Object[] data : accountData) {
            Account account = new Account((String) data[0], (String) data[1], 
                    new BigDecimal((String) data[2]), clock);
            account.setStatus((AccountStatus) data[3]);
            accountRepository.save(account);
        }
        System.out.println("✓ Loaded " + accountData.length + " accounts");
    }

    private void loadPositions() {
        // Create a map of account_id -> Account entity for lookup
        Map<String, Account> accountMap = new HashMap<>();
        accountRepository.findAll().forEach(account -> 
            accountMap.put(account.getAccountId(), account)
        );

        Object[][] positionData = {
                {"ACC-1001", "ACME", 100, "25.00"},
                {"ACC-1002", "GLOB", 200, "10.00"},
                {"ACC-1002", "BOND1", 50, "40.00"},
                {"ACC-1003", "TECH", 75, "50.50"},
                {"ACC-1004", "GOOG", 30, "120.00"},
                {"ACC-1005", "APPL", 50, "155.00"},
                {"ACC-1007", "GOLD", 100, "65.25"},
                {"ACC-1001", "GLOB", 50, "12.00"},
                {"ACC-1004", "BOND1", 100, "40.00"},
                {"ACC-1009", "UTIL", 60, "30.00"},
        };

        for (Object[] data : positionData) {
            Account account = accountMap.get((String) data[0]);
            if (account != null) {
                Position position = new Position(account.getId(), (String) data[1], 
                        (Integer) data[2], new BigDecimal((String) data[3]));
                positionRepository.save(position);
            }
        }
        System.out.println("✓ Loaded " + positionData.length + " positions");
    }

    private void loadOrders() {
        // Create a map of account_id -> Account entity for lookup
        Map<String, Account> accountMap = new HashMap<>();
        accountRepository.findAll().forEach(account -> 
            accountMap.put(account.getAccountId(), account)
        );

        Object[][] orderData = {
                {"11111111-1111-1111-1111-111111111111", "ACC-1001", "ACME", OrderSide.BUY, 100, "25.00", OrderStatus.FILLED, "seed-key-1"},
                {"22222222-2222-2222-2222-222222222222", "ACC-1002", "GLOB", OrderSide.BUY, 200, "10.00", OrderStatus.FILLED, "seed-key-2"},
                {"33333333-3333-3333-3333-333333333333", "ACC-1002", "BOND1", OrderSide.BUY, 50, "40.00", OrderStatus.FILLED, "seed-key-3"},
                {"44444444-4444-4444-4444-444444444444", "ACC-1001", "GLOB", OrderSide.BUY, 10, "12.00", OrderStatus.REJECTED, "seed-key-4"},
                {"55555555-5555-5555-5555-555555555555", "ACC-1002", "BOND1", OrderSide.SELL, 10, "41.00", OrderStatus.NEW, "seed-key-5"},
                {"66666666-6666-6666-6666-666666666666", "ACC-1003", "TECH", OrderSide.BUY, 75, "50.50", OrderStatus.FILLED, "seed-key-6"},
                {"77777777-7777-7777-7777-777777777777", "ACC-1004", "GOOG", OrderSide.BUY, 30, "120.00", OrderStatus.FILLED, "seed-key-7"},
                {"88888888-8888-8888-8888-888888888888", "ACC-1005", "APPL", OrderSide.BUY, 50, "155.00", OrderStatus.FILLED, "seed-key-8"},
                {"99999999-9999-9999-9999-999999999999", "ACC-1006", "MSFT", OrderSide.SELL, 20, "310.00", OrderStatus.FILLED, "seed-key-9"},
                {"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "ACC-1007", "GOLD", OrderSide.BUY, 100, "65.25", OrderStatus.FILLED, "seed-key-10"},
        };

        int loadedCount = 0;
        for (Object[] data : orderData) {
            try {
                Account account = accountMap.get((String) data[1]);
                if (account != null) {
                    Order order = new Order(account.getId(), (String) data[2], (OrderSide) data[3], 
                            (Integer) data[4], new BigDecimal((String) data[5]), (String) data[7], clock);
                    order.setStatus((OrderStatus) data[6]);
                    orderRepository.save(order);
                    loadedCount++;
                }
            } catch (Exception e) {
                System.out.println("⚠ Skipped order: " + e.getMessage());
            }
        }
        System.out.println("✓ Loaded " + loadedCount + " orders");
    }
}
