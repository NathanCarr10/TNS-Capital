# Yahoo Finance Integration - Quick Reference

## Start the Application

```bash
cd /home/ec2-user/TNS-Capital

# Start database
docker run -d --name tns-postgres \
  -e POSTGRES_USER=tns-capital-db-user \
  -e POSTGRES_DB=tns_capital \
  -e POSTGRES_PASSWORD=password \
  -p 5432:5432 postgres:16-alpine

# Wait for DB, then apply migrations
sleep 10
for f in db/00_init.sql db/tables/*.sql; do
  psql -U tns-capital-db-user -d tns_capital -h localhost -f "$f"
done

# Build and run application
mvn clean package -DskipTests
java -jar target/tns-capital-skeleton.jar
```

App runs on: `http://localhost:3000`

---

## API Quick Commands

### 1️⃣ Add an Instrument
```bash
psql -U tns-capital-db-user -d tns_capital -h localhost << EOF
INSERT INTO instruments (symbol, name, asset_class, currency, tradable)
VALUES ('AAPL', 'Apple Inc.', 'EQUITY', 'USD', true);
EOF
```

### 2️⃣ Fetch from Yahoo Finance
```bash
curl "http://localhost:3000/api/v1/financial-data/fetch?symbol=AAPL&startDate=2024-09-01&endDate=2024-09-30"
```

### 3️⃣ Get Price History
```bash
curl "http://localhost:3000/api/v1/financial-data/history?symbol=AAPL&startDate=2024-09-01&endDate=2024-09-30" | jq
```

### 4️⃣ Calculate Moving Average
```bash
# 50-day MA
curl "http://localhost:3000/api/v1/financial-data/moving-average?symbol=AAPL&days=50" | jq

# 20-day MA
curl "http://localhost:3000/api/v1/financial-data/moving-average?symbol=AAPL&days=20" | jq
```

### 5️⃣ Calculate Volatility
```bash
# 30-day volatility
curl "http://localhost:3000/api/v1/financial-data/volatility?symbol=AAPL&days=30" | jq

# 15-day volatility
curl "http://localhost:3000/api/v1/financial-data/volatility?symbol=AAPL&days=15" | jq
```

### 6️⃣ Get Current Price
```bash
curl "http://localhost:3000/api/v1/financial-data/current-price?symbol=AAPL" | jq
```

---

## All 5 API Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/v1/financial-data/fetch` | GET | Fetch & store from Yahoo Finance |
| `/api/v1/financial-data/current-price` | GET | Get live current price |
| `/api/v1/financial-data/history` | GET | Get stored historical data |
| `/api/v1/financial-data/moving-average` | GET | Calculate SMA |
| `/api/v1/financial-data/volatility` | GET | Calculate volatility |

---

## Using in Your Code

```java
@RestController
public class MyStrategyController {
    
    @Autowired
    private YahooFinanceService yahooFinanceService;
    
    @GetMapping("/my-strategy")
    public ResponseEntity<?> executeStrategy(@RequestParam String symbol) {
        // Get price data
        List<PriceHistory> history = yahooFinanceService
            .getPriceHistory(symbol, startDate, endDate);
        
        // Calculate moving average
        Optional<BigDecimal> ma50 = yahooFinanceService
            .calculateMovingAverage(symbol, 50);
        
        // Calculate volatility
        Optional<BigDecimal> volatility = yahooFinanceService
            .calculateVolatility(symbol, 30);
        
        // Your trading logic here...
        return ResponseEntity.ok(/* your response */);
    }
}
```

---

## Key File Locations

| Component | Location |
|-----------|----------|
| Database Schema | `db/tables/05_price_history.sql` |
| Domain Model | `src/main/java/com/neueda/leap/model/PriceHistory.java` |
| Repository | `src/main/java/com/neueda/leap/repositories/PriceHistoryRepository.java` |
| Service | `src/main/java/com/neueda/leap/services/YahooFinanceService.java` |
| REST API | `src/main/java/com/neueda/leap/controllers/FinancialDataController.java` |
| DTO | `src/main/java/com/neueda/leap/dtos/PriceHistoryDTO.java` |

---

## Troubleshooting

### App won't start?
```bash
# Check logs
tail -100 /tmp/app.log | grep ERROR
```

### No data returned?
```bash
# Verify data exists in DB
psql -U tns-capital-db-user -d tns_capital -h localhost \
  -c "SELECT COUNT(*) FROM price_history WHERE symbol = 'AAPL';"
```

### Database connection refused?
```bash
# Check if container is running
docker ps | grep postgres

# Restart if needed
docker start tns-postgres
```

### Yahoo Finance rate limited (429 error)?
- This is normal - Yahoo Finance has rate limits
- Wait a few seconds and retry
- Or use pre-populated test data in database

---

## Database Queries

### See all OHLCV data for a symbol
```sql
SELECT * FROM price_history WHERE symbol = 'AAPL' ORDER BY date DESC;
```

### Count records
```sql
SELECT symbol, COUNT(*) FROM price_history GROUP BY symbol;
```

### Delete old data
```sql
DELETE FROM price_history WHERE symbol = 'OLD_SYMBOL';
```

### Check latest price
```sql
SELECT * FROM price_history 
WHERE symbol = 'AAPL' 
ORDER BY date DESC LIMIT 1;
```

---

## Configuration

Edit `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/tns_capital
    username: tns-capital-db-user
    password: password  # Change if needed

server:
  port: 3000  # Change to different port if needed
```

Then rebuild: `mvn clean package -DskipTests`

---

## Branch Info

Currently on: `refactor/yfinance_db`

Safe to test without affecting main code:
- ✅ No existing code modified
- ✅ All changes isolated
- ✅ Easy to rollback
- ✅ Ready to merge when satisfied

---

**Last Updated:** 2026-09-23
**Status:** ✅ Production Ready
