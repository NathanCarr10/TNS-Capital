# TNS Capital - Leap Program 2026

A trading system backend for managing accounts, orders, positions, and instruments using Domain-Driven Design principles.

## Team

- Nathan Carr
- Sinead King
- Nokuvimba Chiyaka
- Tetiana Urbanovych
- Tiffanie Fitzgerald

## Tech Stack

- **Language:** Java
- **Build Tool:** Maven
- **Database:** PostgreSQL
- **Containerization:** Docker
- **CI/CD:** Jenkins
- **Version Control:** Git (Git Flow strategy)

---

## Git Flow Strategy

We follow **Git Flow** branching strategy:

- **`main`** - Production-ready code (stable releases)
- **`Development`** - Integration branch (staging/pre-release)


---

## Project Structure

### Source Code Organization (`src/main/java/com/neueda/leap/`)

```
src/main/java/com/neueda/leap/
├── domain/              # Domain model layer (core business logic)
├── enums/               # Enumeration types
├── exceptions/          # Custom exception classes
└── Main.java           # Application entry point
```

#### **domain/** - Domain Model Layer
**Responsibility:** Pure business logic and data validation

- **Account.java** - Trading account entity with cash balance management, debit/credit operations, and status tracking
- **Order.java** - Order entity with order details, idempotency key for duplicate prevention, and status lifecycle
- **Position.java** - Position entity tracking held securities with weighted average cost calculations
- **Instrument.java** - Tradable security/instrument definition with asset class and currency info

**Key Features:**
- Comprehensive input validation (null checks, range validation)
- Defensive copying for immutable data integrity
- equals() / hashCode() for proper object comparison in collections
- toString() for debugging and logging

#### **enums/** - Enumeration Types
**Responsibility:** Define fixed state values used across the domain

- **AccountStatus.java** - Account states: `ACTIVE`, `SUSPENDED`, `CLOSED`
- **OrderSide.java** - Order direction: `BUY`, `SELL`
- **OrderStatus.java** - Order lifecycle: `NEW`, `FILLED`, `REJECTED`, `CANCELLED`

#### **exceptions/** - Custom Exceptions
**Responsibility:** Business-specific exception handling

- **InsufficientFundsException.java** - Thrown when account lacks funds for a transaction
- **InsufficientHoldingsException.java** - Thrown when position lacks shares for a trade
- **InstrumentNotFoundException.java** - Thrown when instrument doesn't exist
- **AccountNotFoundException.java** - Thrown when account doesn't exist
- **DuplicateOrderException.java** - Thrown when order idempotency key already exists
- **AccountNotActiveException.java** - Thrown when trading on inactive account

---

## Database Setup

### Architecture
- PostgreSQL runs as a **long-lived container** on a shared machine (not per-build)
- App container connects via network (see `docker-compose.yml`)
- Initial schema loaded from `db/schema.sql`


---

## Docker & Deployment

### Files

#### **docker-compose.yml**
- **Purpose:** Orchestrates the application and database containers
- **Services:**
  - `app` - Java application container (port 8080)
  - `postgres` - PostgreSQL database (port 5432)
- **Usage:** `docker-compose up` to start both services

#### **Dockerfile**
- **Purpose:** Builds the Java application image
- **Process:**
  1. Compiles Maven project
  2. Creates lightweight runtime image
  3. Exposes application on port 8080
- **Usage:** Docker build is automated by Jenkins; manual build with `docker build -t tns-capital .`

---

## CI/CD Pipeline

### Jenkinsfile
- **Purpose:** Automates build, test, and deployment pipeline
- **Stages:**
  1. **Checkout** - Pull latest code from repository
  2. **Build** - Maven compile and package
  3. **Test** - Run unit tests
  4. **Docker Build** - Create application container image
  5. **Deploy** - Push to registry and deploy (Dev/Staging/Prod based on branch)


---


---
## Architecture Principles

- **Domain-Driven Design (DDD):** Domain layer contains core business logic
- **SOLID Principles:** Clean, maintainable code
- **Input Validation:** Fail-fast approach prevents invalid state
- **Defensive Copying:** BigDecimal fields copied to prevent external mutation
- **Testability:** Entity design supports unit testing and mocking

---


