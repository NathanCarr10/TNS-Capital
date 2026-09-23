# Yahoo Finance HTTP 429 Rate Limiting - Solutions

## Current Status
✅ **Database & API fully functional** - 15 test records stored, all endpoints working
✅ **Exponential backoff implemented** - 5 retries with 5s→10s→20s→40s→80s + jitter
⚠️ **Yahoo Finance blocking** - HTTP 429 on all requests, even after delays

## Why 429 Errors Persist
- Yahoo Finance aggressively rate-limits even slow requests
- Java `yahoofinance-api` library has no advanced header controls
- IP/API key blocking likely occurring
- Yahoo actively discourages automated fetching

---

## Solution 1: Use Existing Database (RECOMMENDED) ✅
**Status: Fully working now**

The trading system works perfectly with stored price data:
```bash
# All endpoints functional with test data:
GET /api/v1/financial-data/history?symbol=AAPL
GET /api/v1/financial-data/moving-average?symbol=AAPL&days=5
GET /api/v1/financial-data/volatility?symbol=AAPL&days=30

# Today's 15 records available for:
# AAPL (3), GOOGL (4), MSFT (4), TESLA (4) - dates 2024-09-20 to 2024-09-23
```

**Pros:**
- Zero additional work
- All analysis features work perfectly
- Scheduler ready for future data

**Cons:**
- Data isn't live from Yahoo
- Need manual data injection for new dates

**Use Case:** Development, testing, demonstration

---

## Solution 2: Switch to Alpha Vantage API (MODERATE EFFORT)
**Better rate limiting, free tier available**

Alpha Vantage provides:
- 5 free API calls/minute (5x better than Yahoo's current state)
- Support for daily/weekly/monthly data
- Simpler HTTP-based API (no library wrestling)
- Explicit API key management

### Implementation Steps:

**Step 1: Get free API key**
```bash
Visit: https://www.alphavantage.co/
Sign up → Get free API key
```

**Step 2: Add dependency to pom.xml**
```xml
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>okhttp</artifactId>
    <version>4.11.0</version>
</dependency>
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.10.1</version>
</dependency>
```

**Step 3: Add API key to application.yml**
```yaml
alphavantage:
  api-key: YOUR_API_KEY_HERE
  base-url: https://www.alphavantage.co/query
```

**Step 4: Create AlphaVantageService**
```java
@Service
public class AlphaVantageService {
    @Value("${alphavantage.api-key}")
    private String apiKey;
    
    public List<PriceHistory> fetchHistoricalData(String symbol, LocalDate startDate, LocalDate endDate) {
        // Make HTTP GET request to Alpha Vantage API
        // Parse JSON response
        // Convert to PriceHistory objects
        // Respect 5 calls/minute limit
    }
}
```

**Pros:**
- Better official support for automation
- Predictable rate limiting
- Live data
- More reliable

**Cons:**
- Requires third-party API key
- Limit of 5 calls/minute (requires scheduling)
- Free tier limited to 500 calls/day

**Time to implement:** 2-3 hours

---

## Solution 3: Python yfinance Wrapper (QUICK & DIRTY)
**Use Python's yfinance library (better rate-limit handling) via subprocess**

Python's `yfinance` handles rate-limiting better than Java library.

### Implementation:

**Step 1: Create Python script** - `/home/ec2-user/TNS-Capital/scripts/fetch_yfinance.py`
```python
#!/usr/bin/env python3
import yfinance as yf
import json
import sys
from datetime import datetime

symbol = sys.argv[1]
start_date = sys.argv[2]
end_date = sys.argv[3]

try:
    data = yf.download(symbol, start=start_date, end=end_date, progress=False)
    result = []
    for date, row in data.iterrows():
        result.append({
            "symbol": symbol,
            "date": date.strftime('%Y-%m-%d'),
            "open": float(row['Open']),
            "high": float(row['High']),
            "low": float(row['Low']),
            "close": float(row['Close']),
            "adjClose": float(row['Adj Close']),
            "volume": int(row['Volume'])
        })
    print(json.dumps(result))
except Exception as e:
    print(json.dumps({"error": str(e)}), file=sys.stderr)
    sys.exit(1)
```

**Step 2: Call from Java YahooFinanceService**
```java
private List<PriceHistory> fetchViaPython(String symbol, LocalDate startDate, LocalDate endDate) {
    try {
        ProcessBuilder pb = new ProcessBuilder(
            "python3", 
            "scripts/fetch_yfinance.py",
            symbol,
            startDate.toString(),
            endDate.toString()
        );
        Process process = pb.start();
        
        // Parse JSON output and convert to PriceHistory
        // Return results
    } catch (IOException e) {
        // Fallback to database
    }
}
```

**Pros:**
- Python yfinance more forgiving with rate limits
- Works for live data
- Fallback to Java/DB if Python unavailable

**Cons:**
- Requires Python 3 + yfinance installed
- Subprocess overhead
- Additional dependency

**Time to implement:** 1-2 hours

---

## Solution 4: Mock Data Generator (FOR TESTING)
**Generate synthetic but realistic price data**

```java
@Service
public class MockDataService {
    public List<PriceHistory> generateMockHistory(String symbol, LocalDate startDate, LocalDate endDate) {
        // Generate realistic OHLCV data using:
        // - Random walk for prices
        // - Correlated volume
        // - Consistent bid-ask spreads
        // Store in database
    }
}
```

**Use Cases:**
- Development without external API
- Testing scheduler
- Demo purposes

---

## Solution 5: Rotate IP/User-Agent (ADVANCED)
**Requires VPN/proxy rotation**

Not recommended - violates Yahoo's ToS and requires complex infrastructure.

---

## Recommendation for You 🎯

### Immediate (Next 30 minutes):
1. **Use Solution 1**: Continue with database + test data
2. All endpoints work perfectly for analysis
3. Scheduler configured and ready

### Short-term (Next session):
1. **Implement Solution 3** (Python yfinance wrapper)
   - Fastest viable live data solution
   - Fall back to database if needed
   - Scheduler will handle rate limits intelligently

2. **If time permits**: Add Solution 2 (Alpha Vantage)
   - More official, future-proof
   - Better for production use

---

## Current System Status

| Component | Status | Notes |
|-----------|--------|-------|
| Database | ✅ Working | 15 records stored, all queries fast |
| API Endpoints | ✅ Working | All 6 endpoints functional with test data |
| Moving Average | ✅ Working | Accurate calculations |
| Volatility | ✅ Working | Accurate calculations |
| Scheduler | ✅ Configured | Ready to run (9:00 AM daily) |
| Exponential Backoff | ✅ Implemented | 5 retries with delays |
| Yahoo Finance Fetch | ❌ Blocked | HTTP 429 from Yahoo, not fixable without alternative |

---

## Implementation Priority

```
MUST HAVE (v1):
1. Keep database + mock data working ✅

NICE TO HAVE (v2):
2. Python yfinance wrapper for live data

FUTURE (v3):
3. Alpha Vantage API for production
4. Data caching & incremental fetches
5. Multiple data source fallback chain
```
