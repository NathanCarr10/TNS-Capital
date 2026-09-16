# TNS Capital - Architecture & Design Documentation

This folder contains architectural and design diagrams for the TNS Capital trading system.

## Diagrams

### Class Diagram
**File:** `diagrams/TNS_CAPITAL-CLASS-DIAGRAM.png`

<img width="747" height="826" alt="image" src="https://github.com/user-attachments/assets/b04e5b3b-6214-429a-b9a6-441856975324" />


This diagram illustrates the core domain model of the trading system:

- **Account**: Represents a trading account with cash balance, status, and account metadata
  - Methods: `debit()`, `credit()`, `isActive()`
  - Status enum: ACTIVE, SUSPENDED, CLOSED

- **Position**: Tracks holdings of a specific instrument within an account
  - Methods: `apply()`, `markToMarket()`

- **Order**: Represents buy/sell orders placed by traders
  - Properties: order ID, side (BUY/SELL), quantity, price, status
  - Status enum: NEW, FILLED, REJECTED, CANCELLED

- **Instrument**: Represents tradable securities
  - Properties: symbol, name, asset class, currency, tradability status
  - Methods: `isTradeable()`

**Key Relationships:**
- An Account owns multiple Positions (1:0..*)
- An Account places multiple Orders (1:0..*)
- A Position holds one Instrument (0..*:1)
- An Order is traded as one Instrument (0..*:1)

---

### Buy Order Sequence Diagram
**File:** `diagrams/BUY-ORDER-SEQUENCE-DIAGRAM.png`

<img width="628" height="555" alt="image" src="https://github.com/user-attachments/assets/f690be10-82bc-4c7a-8796-9bdae09df3aa" />


This diagram shows the complete flow when a trader places a buy order:

1. **Validation Phase**
   - Check if instrument is tradable
   - Verify sufficient cash balance

2. **Order Creation**
   - Create new order with status NEW
   - Debit account balance
   - Update position (add holdings)

3. **Order Confirmation**
   - Set order status to FILLED
   - Return confirmation to trader

**Participants:** Trader, Account, Instrument, Order, Position

---

### Sell Order Sequence Diagram
**File:** `diagrams/SELL-ORDER-SEQUENCE-DIAGRAM.png`

<img width="607" height="598" alt="image" src="https://github.com/user-attachments/assets/5495b336-2c9b-4024-9f41-727b4e46ed19" />


This diagram shows the complete flow when a trader places a sell order:

1. **Validation Phase**
   - Check if instrument is tradable
   - Verify sufficient holdings in position

2. **Order Creation**
   - Create new order with status NEW
   - Credit account balance
   - Update position (reduce holdings)

3. **Order Confirmation**
   - Set order status to FILLED
   - Return confirmation to trader

**Participants:** Trader, Account, Instrument, Order, Position

---

## System Workflow

Both buy and sell orders follow a similar workflow:
1. **Pre-trade validation** - Ensure account and instrument are in valid state
2. **Order placement** - Create order record in system
3. **Account impact** - Update cash balance (debit for buy, credit for sell)
4. **Position update** - Reflect changes in holdings
5. **Status confirmation** - Mark order as FILLED

## Error Handling

The system includes exception handling for:
- Invalid account status
- Insufficient funds (for buy orders)
- Insufficient holdings (for sell orders)
- Non-tradable instruments
- Duplicate orders
