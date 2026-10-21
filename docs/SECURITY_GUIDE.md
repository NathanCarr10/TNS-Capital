# Security Guide

## Overview

This document outlines the security measures implemented in the TNS Capital API and provides guidelines for maintaining security posture.

For the platform's OWASP Top 10 review, with a finding and disposition for every category, see [OWASP_REVIEW.md](OWASP_REVIEW.md).

---

## 1. Authentication & Authorization

### JWT (JSON Web Tokens)

**Implementation:**
- JWT tokens are signed using HMAC-SHA256 with a shared secret
- Configured in [SecurityConfig.java](../src/main/java/com/neueda/leap/security/SecurityConfig.java)
- Tokens are issued by the NestJS authentication service (`shared/auth-service/`)

**Best Practices:**
- **Shared secret management**: the secret comes from the `JWT_SECRET` environment variable, with
  no default. The API refuses to start if it is missing, shorter than 32 bytes or the
  `change-me` placeholder from `.env.example`
- **Token validation**: every request's token is checked for its signature, expiry, issuer
  (`urn:tns-capital:auth-service`) and type (`typ` = `access`)
- **Stateless authentication**: no session state is stored on the server

**Configuration** (`src/main/resources/application.yml`):
```yaml
jwt:
  # Must match the auth service. Set JWT_SECRET in the environment; there is no default.
  shared-secret: ${JWT_SECRET}
  issuer: urn:tns-capital:auth-service
```

**Token flow:**
```
1. Client signs in to the auth service (POST /auth/login) with an email and password
2. The auth service issues a 15-minute access token signed with the shared secret,
   and a 7-day single-use refresh token
3. Client includes the access token in the Authorization header: "Bearer <token>"
4. The API validates the token locally, using the shared secret
5. The request proceeds if the token is valid; the caller's roles and account decide what it may do
```

### Roles and Account Ownership

- `POST /register` on the auth service creates the user **and** opens their trading account
  (ACTIVE, cash balance 0), linked by `users.account_id`. The token's `roles` claim comes from
  `users.role`: `["USER"]` for registered users, `["SUPPORT"]` for `support@tns-capital.local` and
  `["ADMIN"]` for `admin@tns-capital.local`, both created by the auth service at startup.
- **USER**: may only use the account linked to their email (the token's `sub`) — read it,
  move cash, place/cancel orders on it, see its positions and orders. Anything else is
  `403 AUTH-403`, including account IDs that do not exist (no probing for valid IDs).
  `GET /api/v1/accounts` and `GET /api/v1/orders` return only the user's own rows.
- **SUPPORT**: may use every account (the trading app shows it read-only), suspend or reactivate
  accounts (`PUT /api/v1/accounts/{id}/status`), open accounts (`POST /api/v1/accounts`), add
  or change instruments, read and answer every support conversation, and review, replay and
  dismiss dead letters.
- **ADMIN**: the DLQ and the actuator's metrics, info and health details, plus the analytics
  dashboard. No access to traders' accounts: an admin is not support
  ([decision 10](DECISIONS.md#10-roles)).
- **STRATEGY**: a service role, never a user's. Only the strategy service's token has it, issued by
  `POST /auth/token` from `STRATEGY_CLIENT_SECRET`. It may place an order, read an order and read
  prices, and only for an account whose owner has an ACTIVE strategy; every other endpoint answers
  `403 AUTH-403` (`SecurityConfig`, `AccountAccessGuard.checkTradingAccess`;
  [decision 16](DECISIONS.md#16-sprint-10-extension-strategy-lab-automated-strategy-execution)).
- Enforced by [AccountAccessGuard.java](../src/main/java/com/neueda/leap/security/AccountAccessGuard.java).

---

## 2. Input Validation

### Validation Strategy

Input validation occurs at the **API boundary** using Jakarta Validation annotations.

### Request DTOs with Validation

#### PlaceOrderRequest.java
```java
public record PlaceOrderRequest(
    @NotNull(message = "Account ID is required")
    Long accountId,
    
    @NotNull @NotBlank
    @Pattern(regexp = "^[A-Z]{1,10}$", message = "Symbol: 1-10 uppercase letters")
    String symbol,
    
    @NotNull
    OrderSide side,
    
    @NotNull @Positive
    @Max(value = 1000000, message = "Quantity max: 1,000,000")
    Integer quantity,
    
    // Optional: when omitted the order is priced at the live market price
    @Positive
    @DecimalMax(value = "999999.99", message = "Price max: 999,999.99")
    BigDecimal price,
    
    @NotNull @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9_-]{1,100}$")
    String idempotencyKey
)
```

#### CreateInstrumentRequest.java
```java
public record CreateInstrumentRequest(
    @NotNull @NotBlank
    @Pattern(regexp = "^[A-Z]{1,10}$")
    String symbol,
    
    @NotNull @NotBlank
    @Size(min = 1, max = 255)
    String name,
    
    @NotNull @NotBlank
    @Pattern(regexp = "^[A-Z_]{1,20}$")
    String assetClass,
    
    @NotNull @NotBlank
    @Pattern(regexp = "^[A-Z]{3}$")
    String currency,
    
    @NotNull
    boolean tradable
)
```

### Validation Constraints Explained

| Constraint | Purpose | Example |
|-----------|---------|---------|
| `@NotNull` | Field is required | `@NotNull Long accountId` |
| `@NotBlank` | String cannot be empty | `@NotBlank String symbol` |
| `@Pattern` | Regex format validation | `@Pattern(regexp = "^[A-Z]{1,10}$")` |
| `@Positive` | Number must be > 0 | `@Positive Integer quantity` |
| `@Max/@Min` | Range constraints | `@Max(1000000)` |
| `@DecimalMax` | Decimal range | `@DecimalMax("999999.99")` |
| `@Size` | String length | `@Size(min=1, max=255)` |

### How Validation Works

```
1. Client sends HTTP POST /api/v1/orders with JSON body
2. Spring intercepts request due to @Valid annotation on @RequestBody
3. Jakarta Validation framework validates against constraints
4. If valid: Request proceeds to controller
5. If invalid: GlobalExceptionHandler catches MethodArgumentNotValidException
6. Handler returns 422 UNPROCESSABLE_ENTITY (VAL-422) with field-specific error messages
```

### Example Validation Error Response

```json
{
    "errorCode": "VAL-422",
    "message": "symbol: Symbol must contain 1-10 uppercase letters; quantity: Quantity exceeds maximum allowed (1,000,000)",
    "timestamp": "2026-09-28T14:15:00"
}
```

---

## 3. Password Hashing (Auth Service)

### Algorithm: Argon2id

**Implementation:** Located in `shared/auth-service/src/users/password-hasher.ts` (NestJS auth service)

**Choice Rationale:**
- **Argon2id** selected over bcrypt because it is memory-hard and resistant to GPU/ASIC attacks
- **Industry Standard**: RFC 9106, recommended by OWASP
- **Future-Proof**: Parameters (memory, time, parallelism) can be upgraded in stored hash format without requiring password re-entry

### Cost Parameters

| Parameter | Value | Justification |
|-----------|-------|----------------|
| Algorithm | Argon2id | Memory-hard, GPU-resistant |
| Memory | 65,540 KiB (~64 MiB) | Strong against brute force while maintaining reasonable hardware requirements |
| Time | 3 iterations | ~50-100ms per hash on typical 2025 server hardware |
| Parallelism | 4 threads | Balanced multi-core utilization without excessive overhead |

**Timing Justification**: Hashing a password takes ~50-100ms on typical server hardware, making brute-force attacks computationally expensive (billions of attempts would take weeks) while keeping legitimate user registration/login responsive.

### Hash Storage Format

Passwords are stored using Argon2id's standard format:
```
$argon2id$v=19$m=65540,t=3,p=4$<salt>$<hash>
```

This format encodes:
- **Algorithm**: `argon2id` (memory-hard variant)
- **Version**: `19` (Argon2 version)
- **Parameters**: `m=memory`, `t=iterations`, `p=parallelism`
- **Salt**: Cryptographically random, prevents rainbow tables
- **Hash**: Actual password verification hash

**Stored in Database:**
```sql
-- Complete hash string stored in password_hash column
password_hash VARCHAR(255) NOT NULL  -- e.g., $argon2id$v=19$m=65540,t=3,p=4$abc123$xyz789
```

**Upgrade Path**: Parameters can be increased in the future (e.g., `m=131080`) without requiring password re-entry — old and new hashes are verified using the same algorithm, and new passwords use updated parameters.

### Password Policies

- **Minimum Length**: 8 characters (enforced at registration)
- **Maximum Length**: 256 characters (no artificial limit; passphrases welcome)
- **No Character Restrictions**: User can use any printable characters
- **Storage**: Only the Argon2id hash is stored; plaintext is never persisted

### Plaintext Prevention

- Passwords hashed immediately upon registration using `hashPassword()` (`password-hasher.ts`)
- Hashes verified during login (plaintext input compared to stored hash via `verifyPassword()`)
- Error responses never include password or hash
- Response DTOs exclude passwordHash field
- Request logs record the method, path, status, time and user, never bodies, so no passwords or tokens

### Implementation Details

**Registration Flow** (`POST /auth/register`):
```
1. Client sends {email, password} in request body
2. Server validates email (a valid address, at most 320 chars) and password (8-256 chars),
   then trims and lower-cases the email
3. Server checks for a duplicate email → 409 USR-409 if exists
4. Server hashes password with Argon2id → passwordHash
5. Server inserts (email, passwordHash) into database and opens the trading account
6. Server returns {id, email, createdAt, accountId, accountNumber} (NO password or hash)
```

**Login Flow** (`POST /auth/login`):
```
1. Client sends {email, password} in request body
2. Server retrieves user record from database by email (trimmed, lower case)
3. Server verifies plaintext password against stored hash
4. If password matches: issue a 15-minute access token (JWT) and a 7-day refresh token,
   store the refresh token's SHA-256 hash in refresh_tokens under a new session family,
   return {accessToken, refreshToken}
5. If password doesn't match: Return 401 AUTH-401 (no hint about whether the email is registered)
```

**Refresh and Logout** (`POST /auth/refresh`, `POST /auth/logout`):
```
1. Client sends {refreshToken}
2. Server hashes it with SHA-256 and looks for an unexpired match in refresh_tokens
3. No match: 401 AUTH-401 "invalid or expired refresh token"
4. Refresh, token unspent: mark it spent and store its successor in the same family, in one
   transaction; return a new {accessToken, refreshToken}
5. Refresh, token already spent: it was copied, so delete the whole family (the user's
   other sessions are kept), log a warning, return 401 AUTH-401
6. Logout: delete the token's family, return {loggedOut: true}
```

**Why SHA-256 for refresh tokens but Argon2id for passwords?** A refresh token is 256 random
bits, so it cannot be guessed and needs no slow hash or salt; an unsalted hash lets the
database find it directly. Storing only the hash means a database leak does not leak
usable tokens. Logout revokes the refresh token's session only; an access token stays valid until
it expires (at most 15 minutes), because the trading API checks signatures without calling
this service.

**Why No Email Hint?**: Returning different errors for "user not found" vs "password wrong" reveals whether an email is registered, enabling account enumeration attacks. Always return "invalid email or password" for both cases.

### Verification & Testing

All password handling is tested in `shared/auth-service/tests/`:

**password-hasher.spec.ts** — Unit tests for hashing:
- Hash always differs from input (random salt)
- Same password produces different hashes (salt randomization)
- Correct password verifies against stored hash
- Incorrect password verification fails
- Hash string includes algorithm, parameters, and salt
- Invalid hash format returns false, not error

**users.service.spec.ts** — Integration tests for the SQL (needs Postgres; skipped unless `DB_HOST` is set):
- Registration opens an ACTIVE account with a 0 cash balance, linked via `users.account_id`
- Duplicate email is rejected without opening a second account
- A failed account insert rolls back the user
- Refresh tokens rotate, a reused token revokes its family, concurrent refreshes cannot both win, tokens expire, and logout revokes the family

**auth.service.spec.ts** — Unit tests for the auth logic:
- Password is Argon2id-hashed before it reaches the database layer
- Login succeeds with valid credentials and fails identically for a wrong password or unknown user

**guards.spec.ts**, **token.service.spec.ts** — Unit tests for `JwtAuthGuard`, `RolesGuard` and token handling:
- `JwtAuthGuard` verifies locally (no database lookup or network call) and gives the handler the verified claims
- Expired, wrong-signature, tampered, wrong-issuer, wrong-type and `alg: none` tokens, a missing header and a non-Bearer scheme all get the same 401 AUTH-401

**auth-api.spec.ts** — HTTP tests (no database): claims, roles, refresh, logout, guards, error envelope, validation.

**Run tests** (Node 22+; set `DB_*` to reach a Postgres with the `db/` schema):
```bash
cd shared/auth-service
npm install
npm test
```

### References

- [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
- [Argon2 RFC 9106](https://datatracker.ietf.org/doc/html/rfc9106)
- [Argon2 GitHub](https://github.com/P-H-C/phc-winner-argon2)
- [Memory-Hard Password Hashing](https://cheatsheetseries.owasp.org/cheatsheets/Nodejs_Security_Cheat_Sheet.html#use-a-library-for-password-hashing)

---

## 4. Error Handling

### GlobalExceptionHandler

Located in [GlobalExceptionHandler.java](../src/main/java/com/neueda/leap/controllers/GlobalExceptionHandler.java)

**Key Principles:**
- **No Stack Traces**: Never expose Java stack traces to clients
- **Sanitized Messages**: Remove sensitive information from error messages
- **Machine-Readable Codes**: Error codes for client-side handling
- **Server-Side Logging**: Full details logged for debugging

### Exception Handling Map

Codes follow section 21 of the specification; the codes marked * extend it for cases it does not list.

| Exception | HTTP Status | Error Code | Message |
|-----------|------------|-----------|---------|
| `AccountNotFoundException` | 404 | `ACC-404` | "The requested account could not be found" |
| `AccountNotActiveException` | 403 | `ACC-403` | "The account is not in an active state for this operation" |
| `AccountAlreadyExistsException`, `AccountDeletionConflictException` | 409 | `ACC-409`* | Duplicate account number / account has working orders |
| `InstrumentNotFoundException` (unknown or not tradable) | 404 | `INS-404` | "The requested instrument could not be found or is not tradable" |
| `DuplicateInstrumentException` | 409 | `INS-409`* | "An instrument with this symbol already exists" |
| `InsufficientFundsException` | 400 | `ORD-400` | "The account does not have sufficient funds for this operation" |
| `InsufficientHoldingsException`, `DuplicateOrderException`, `OrderCancellationConflictException` | 409 | `ORD-409` | Insufficient holdings / duplicate idempotency key / order cannot be cancelled |
| `LimitNotMarketableException` | 422 | `ORD-422`* | A BUY limit below the live price, or a SELL limit above it |
| `OrderNotFoundException` | 404 | `ORD-404`* | "The requested order could not be found" |
| `PositionNotFoundException` | 404 | `POS-404`* | "The requested position could not be found" |
| `PriceUnavailableException` | 503 | `PRC-503`* | No live price for an order sent without one |
| `MarketDataUnavailableException` | 503 | `MKT-503`* | Price bars could not be loaded |
| `ReplayInProgressException`, `ReplayNotFoundException` | 409 / 404 | `MKT-409`* / `MKT-404`* | A market replay is already running / no bars for that day, or no replay running |
| `OrderPublishException` | 503 | `SYS-503`* | Kafka did not acknowledge the order; it is stored as `REJECTED` |
| `OptimisticLockingFailureException` | 409 | `SYS-409`* | Two requests changed the same account at once; retry |
| `NewsletterNotFoundException` | 404 | `NWS-404`* | "The requested newsletter issue could not be found" |
| `NewsletterPublishingDisabledException` | 403 | `NWS-403`* | "Publishing the newsletter before the weekly run is switched off" |
| `MethodArgumentNotValidException`, malformed JSON, bad path variable | 422 | `VAL-422` | Field-specific validation messages |
| Missing or invalid JWT | 401 | `AUTH-401` | "Unauthorised or invalid token" |
| `AccessDeniedException` | 403 | `AUTH-403`* | "You do not have permission to access this resource" |
| Unsupported method / media type | 405 / 415 | `REQ-405`* / `REQ-415`* | |
| Unknown path | 404 | `NOT-404`* | "Resource not found: ..." |
| Generic Exception | 500 | `SYS-500`* | "An unexpected error occurred" |

Insufficient funds and holdings are only known once an order executes, which
happens asynchronously. The API accepts the order (202); if execution fails the
order is stored as `REJECTED` (visible on `GET /api/v1/orders/{id}`) and the
message is captured in the dead-letter queue, flagged as retryable or not.

### Example: Exception Handling Flow

```java
// Handler method
@ExceptionHandler(AccountNotFoundException.class)
public ResponseEntity<ErrorResponse> handleAccountNotFound(AccountNotFoundException e) {
    logger.warn("Account not found: {}", e.getMessage());  // Logged server-side
    return error(HttpStatus.NOT_FOUND, "ACC-404",
            "The requested account could not be found");  // Safe message
}
```

**Server Log (contains details):**
```
[WARN] Account not found: Account not found: 999
```

**Client Response (sanitized):**
```json
{
    "errorCode": "ACC-404",
    "message": "The requested account could not be found",
    "timestamp": "2026-09-28T14:15:00"
}
```

---

## 5. Security Headers

### Implemented Headers

Configured in [SecurityConfig.java](../src/main/java/com/neueda/leap/security/SecurityConfig.java)

```java
.headers(headers -> headers
    // Prevent clickjacking (UI redressing attacks)
    .frameOptions(frameOptions -> frameOptions.deny())
    // Prevent MIME type sniffing
    .contentTypeOptions(contentTypeOptions -> {})
    // Enforce HTTPS
    .httpStrictTransportSecurity(hsts -> hsts
        .includeSubDomains(true)
        .maxAgeInSeconds(31536000)  // 1 year
    )
)
```

### Header Descriptions

| Header | Value | Purpose |
|--------|-------|---------|
| `X-Frame-Options` | `DENY` | Prevents clickjacking by blocking iframe embedding |
| `X-Content-Type-Options` | `nosniff` | Prevents MIME type sniffing attacks |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` | Enforces HTTPS for 1 year |

### Example Response Headers

```
HTTP/1.1 200 OK
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
Strict-Transport-Security: max-age=31536000; includeSubDomains
Content-Type: application/json
```

### Frontend (nginx) and XSS

The Angular app is served by nginx, which adds a `Content-Security-Policy` and
`X-Content-Type-Options: nosniff` to every response. They're configured in
[security-headers.conf](../frontend/security-headers.conf). The CSP only runs scripts from the app's own
origin, so injected markup cannot run code even if it reaches the page. `ng serve`
does not send these headers, so check CSP changes against the `frontend` container.

Rules for keeping the frontend XSS-safe:

- Render user and API data with `{{ }}` or property bindings, which Angular escapes.
- Never use `[innerHTML]`, `ElementRef.nativeElement.innerHTML`, `document.write` or
  `DomSanitizer.bypassSecurityTrust*` on data a user can influence.
- Free-text fields use allow-list patterns on both sides. The frontend has
  [input-patterns.ts](../frontend/src/app/core/validation/input-patterns.ts), and the backends use
  `RegisterDto` and `HolderName.java`. When adding a field, add it to both.
- If the built `index.html` gains a new inline script or handler, the CSP will block it.
  Add its hash to `script-src` rather than `'unsafe-inline'`.

---

## 6. CSRF Protection

### Why CSRF is Disabled

CSRF protection is disabled for this API because:

- **Stateless Authentication**: Uses JWT tokens, not session cookies
- **REST API**: No traditional forms that are vulnerable to CSRF
- **Token-Based**: CSRF tokens would be redundant with JWT

**Configuration:**
```java
.csrf(csrf -> csrf.disable())  // Appropriate for JWT-authenticated REST APIs
```

---

## 7. Idempotency

### Purpose

Prevents duplicate processing if requests are retried due to network issues.

### Implementation

**Idempotency Key:**
```java
@NotNull @NotBlank
@Pattern(regexp = "^[a-zA-Z0-9_-]{1,100}$")
String idempotencyKey
```

**Validation Rules:**
- Required on every order request
- Must be 1-100 alphanumeric characters, hyphens, underscores
- Checked for duplicates before processing

**Example Flow:**
```
Request 1: POST /orders with idempotencyKey="ORDER-001" → Creates order
Request 2: POST /orders with idempotencyKey="ORDER-001" (retry) → Returns 409 ORD-409
```

---

## 8. Sensitive Data Protection

### What's Protected

| Data | Protection |
|------|-----------|
| Stack Traces | Never exposed to clients (logged server-side) |
| Database Details | Connection strings, SQL errors hidden |
| File Paths | System paths not exposed |
| Internal Details | Class names, method names hidden |
| Credentials | Environment variables, not in code |

### Example: No Data Leakage

**Bad (Exposed):**
```json
{
    "message": "SQLException at com.neueda.leap.repositories.OrderRepository.save:125 - Connection refused for jdbc:postgresql://localhost:5432/tns_capital"
}
```

**Good (Sanitized):**
```json
{
    "errorCode": "SYS-500",
    "message": "An unexpected error occurred. Please contact support with error timestamp if problem persists.",
    "timestamp": "2026-09-28T14:15:00"
}
```

### Configuration Protection

**Environment Variables:**
Secrets come from `.env` (gitignored; copy `.env.example`), which docker-compose
passes to the containers. `application.yml` has no defaults for them, so the app
refuses to start rather than run with a known value.

```bash
# .env — never committed
DB_PASSWORD=<your database password>
JWT_SECRET=<output of: openssl rand -hex 32>
```

---

## 9. API Endpoint Security

### Protected Endpoints (Require JWT)

"Owner" means a `USER` whose token's `sub` owns the account. `ADMIN` has no access to traders'
accounts or orders.

```
POST   /api/v1/orders              - Place order (owner; STRATEGY for an account with an ACTIVE strategy)
GET    /api/v1/orders              - List orders (own orders; SUPPORT sees all)
GET    /api/v1/orders/{id}         - Get order (owner, SUPPORT; STRATEGY as above)
DELETE /api/v1/orders/{id}         - Cancel order (owner)
GET    /api/v1/accounts            - List accounts (own account; SUPPORT sees all)
POST   /api/v1/accounts            - Open account (SUPPORT)
PUT    /api/v1/accounts/{id}/status - Suspend or reactivate (SUPPORT)
*      /api/v1/accounts/{id}/**    - Account, balance, deposit, withdraw, positions, orders (owner; SUPPORT reads)
GET    /api/v1/positions/{id}      - List positions (owner, SUPPORT)
POST   /api/v1/instruments, PUT /api/v1/instruments/{symbol} - Add or change instruments (SUPPORT)
GET    /api/v1/prices/**           - Prices, bars and news (any signed-in user; STRATEGY)
*      /api/v1/market-data/replays/** - Start, read and stop a market replay (any signed-in user)
*      /api/v1/support/conversations/** - Support chat (own conversations; SUPPORT sees all and
                                     alone closes them and reads notifications)
*      /api/v1/dlq/**              - Dead-letter queue (ADMIN, SUPPORT)
GET    /api/v1/newsletters, /api/v1/newsletters/{id} - Newsletter issues (any signed-in user)
GET    /api/v1/newsletters/preview - Preview this week's issue, not saved (SUPPORT)
POST   /api/v1/newsletters/publish - Publish this week's issue early, if NEWSLETTER_EARLY_PUBLISH is on (SUPPORT)
*      /actuator/**                - Metrics, info and health details (ADMIN)
```

The strategy service's endpoints (`/api/v1/strategies`, port 8087) use the same tokens:
traders manage their own strategies, support staff read every strategy, and admins see none.

### Public Endpoints (No Auth Required)

```
GET  /swagger-ui.html          - Swagger UI documentation
GET  /swagger-ui/**            - Swagger resources
GET  /v3/api-docs              - OpenAPI spec
GET  /v3/api-docs/**           - OpenAPI resources
GET  /actuator/health          - Health check: UP or DOWN; details for ADMIN only
```

### Authorization Check

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**")
    .permitAll()
    .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
    .requestMatchers("/actuator/**").hasRole("ADMIN")
    // The only endpoints the strategy service's STRATEGY token may call
    .requestMatchers(HttpMethod.POST, "/api/v1/orders").authenticated()
    .requestMatchers(HttpMethod.GET, "/api/v1/orders/*", "/api/v1/prices", "/api/v1/prices/**")
    .authenticated()
    // Everything else needs a user's token (USER, SUPPORT or ADMIN)
    .anyRequest().access(userOnly))
```

Role checks on staff operations use `@PreAuthorize`, and account ownership is enforced by
[AccountAccessGuard.java](../src/main/java/com/neueda/leap/security/AccountAccessGuard.java).

---

## 10. Security Testing

### Test Suites

| Test | Covers |
|---|---|
| `security/JwtSecurityTest.java` | Valid, expired, tampered, wrong-issuer, wrong-type and missing tokens; the security headers |
| `security/SecurityConfigSecretTest.java` | The API refuses a missing, short or placeholder `JWT_SECRET` |
| `security/AccountAccessGuardTest.java` | Ownership rules for USER, SUPPORT, ADMIN and STRATEGY |
| `integration/AccountOwnershipIT.java` | A trader cannot read or change another trader's account, orders or positions |
| `integration/StrategyServiceAccessIT.java` | What the STRATEGY token may and may not call |
| `integration/DatabaseRolesIT.java` | Each service's database role can only reach its own tables |
| `controllers/GlobalExceptionHandlerTest.java`, `GlobalExceptionHandlerTests.java` | Every exception maps to its code and a safe message, with no stack trace |
| `shared/auth-service/tests/` | Password hashing, tokens, guards, rate limiting and refresh token reuse (see section 3) |

### Running Tests

```bash
# Unit tests
mvn test

# Unit and integration tests (Docker must be running), with a JaCoCo report
mvn verify

# One class
mvn test -Dtest=JwtSecurityTest
```

---

## 11. Monitoring & Logging

### Logging Strategy

**What to Log:**
```java
logger.warn("Account not found: {}", e.getMessage());
logger.error("Unexpected exception occurred: ", e);  // Full stack trace
```

**What NOT to Log:**
- Passwords or credentials
- Card numbers or sensitive financial data
- Full response bodies containing sensitive info
- Personal identifiable information (PII)

### Health Checks

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      # /actuator/health is public: anyone gets UP or DOWN, only admins get the details
      show-details: when-authorized
      roles: ADMIN
```

**Health endpoint:** `/actuator/health` (public; details for `ADMIN` only). Metrics for the
monitoring stack are served on a separate, unpublished port (`METRICS_PORT`, 9464); see
[observability/README.md](../observability/README.md).

---

## 12. Deployment Security Checklist

Before deploying to production:

- [ ] JWT shared secret is configured via environment variable (not in code)
- [ ] Database credentials are in environment variables
- [ ] HTTPS is enabled (Strict-Transport-Security header)
- [ ] All validation constraints are tested
- [ ] Error handling doesn't expose sensitive data
- [ ] Logging doesn't expose credentials or PII
- [ ] Database is accessible only from application server
- [ ] API is behind a web application firewall (WAF)
- [ ] Rate limiting is configured (the auth service limits credential endpoints per client IP; `AUTH_RATE_LIMIT`, `AUTH_TRUST_PROXY`)
- [ ] Security headers are verified in responses
- [ ] All tests pass including security tests
- [ ] Dependencies are up-to-date (no known vulnerabilities)

---

## 13. Incident Response

### If Security Issue is Discovered

1. **Immediate Actions:**
   - Disable affected service/endpoint
   - Rotate credentials (JWT secret, database password)
   - Review logs for unauthorized access

2. **Investigation:**
   - Check server logs for error patterns
   - Review security test suite for coverage gaps
   - Identify root cause

3. **Fix:**
   - Add validation if input-related
   - Add error handling if exposure occurred
   - Add test coverage for the scenario

4. **Post-Incident:**
   - Update security documentation
   - Team training on the issue
   - Add automated checks to CI/CD

---

## 14. Reference Implementation

### Example: Secure Order Placement

**Request:**
```bash
curl -X POST http://localhost:3000/api/v1/orders \
  -H "Authorization: Bearer eyJhbGc..." \
  -H "Content-Type: application/json" \
  -d '{
    "accountId": 1,
    "symbol": "AAPL",
    "side": "BUY",
    "quantity": 100,
    "price": 150.00,
    "idempotencyKey": "ORDER-2026-001"
  }'
```

**Accepted Response (202):**
```json
{
    "orderId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "ACCEPTED",
    "price": 150.00,
    "message": "Order accepted for processing. Poll GET /api/v1/orders/{orderId} to track status."
}
```

The order executes asynchronously; `GET /api/v1/orders/{orderId}` then returns it
with status `FILLED` or `REJECTED`. Rejected orders are also captured in the
dead-letter queue (`/api/v1/dlq/messages`, ADMIN and SUPPORT) with the failure reason.

**Validation Error (422):**
```json
{
    "errorCode": "VAL-422",
    "message": "symbol: Symbol must contain 1-10 uppercase letters; quantity: Quantity must be positive",
    "timestamp": "2026-09-28T14:15:00"
}
```

**Duplicate Order (409):**
```json
{
    "errorCode": "ORD-409",
    "message": "An order with this idempotency key has already been submitted",
    "timestamp": "2026-09-28T14:15:00"
}
```

---

## 15. Additional Resources

- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [Spring Security Documentation](https://spring.io/projects/spring-security)
- [Jakarta Bean Validation](https://beanvalidation.org/)
- [JWT.io](https://jwt.io/)
- [NIST Cybersecurity Framework](https://www.nist.gov/cyberframework)

---

## 16. CI Security Scanning (DevSecOps)

Every Jenkins build (all branches and pull requests) runs automated security scans defined in the [Jenkinsfile](../Jenkinsfile). The scanners run as pinned Docker images, so the Jenkins agent only needs Docker.

| Stage | Tool | What it checks | Fails the build when |
|---|---|---|---|
| Secret Scan | Gitleaks | Full git history for committed credentials (keys, tokens, passwords) | Any secret is found |
| SAST | Semgrep (`p/java`, `p/owasp-top-ten`) | Source code and Dockerfiles for insecure patterns | An `ERROR`-severity rule matches |
| Dependency Scan | Trivy (`fs` + `image`) | The repo's manifests (`pom.xml` files, `package-lock.json` files, Python `requirements.txt` files), and every library and OS package in the built API, auth service and strategy service images, for known CVEs | A `CRITICAL` CVE with an available fix is found |

Each security stage uses `catchError`, so a failed gate marks the build **FAILED** but the remaining stages still run. That way one build reports every finding.

### Reading the results

- **Console output:** each stage prints its findings in the Jenkins console log.
- **Reports:** SARIF files are archived on every build under **Build Artifacts → `security-reports/`**:
  - `gitleaks.sarif`, `semgrep.sarif`, `trivy-fs.sarif`, `trivy-image.sarif`, `trivy-auth-image.sarif`, `trivy-strategy-image.sarif`
  - Trivy reports include `HIGH` findings as well as `CRITICAL` ones. `HIGH` findings do not fail the build but should be triaged.
  - SARIF opens in VS Code with the *SARIF Viewer* extension.

### Fixing or suppressing a finding

Always prefer fixing the finding. Only suppress it after confirming it is a false positive or an accepted risk, and say why in the PR.

| Tool | How to fix | How to suppress |
|---|---|---|
| Gitleaks | **Rotate the credential first**, since it is exposed in git history. Then move it to an env var or secret store. | Add the finding's fingerprint (`<commit>:<file>:<rule>:<line>`, printed in the log) to `.gitleaksignore` |
| Semgrep | Change the code as the rule message suggests | Add `// nosemgrep: <rule-id>` on the flagged line |
| Trivy | Upgrade the library. For Spring-managed libraries, bump the Spring Boot parent or override the version property in `pom.xml` (e.g. `<tomcat.version>`). | Add the CVE ID with a reason, owner and review date to `.trivyignore`, and record it in [DECISIONS.md](DECISIONS.md#11-accepted-vulnerabilities) |

### Running the scans locally

Run these from the repo root before pushing:

```bash
# Secrets
docker run --rm -v "$PWD":/repo -w /repo zricethezav/gitleaks:v8.21.2 git /repo --redact

# SAST
docker run --rm -v "$PWD":/src -w /src semgrep/semgrep:1.99.0 \
  semgrep scan --config p/java --config p/owasp-top-ten --metrics=off --exclude target

# Dependencies (repo manifests), then the built image
docker run --rm -v "$PWD":/src:ro aquasec/trivy:0.57.1 fs --scanners vuln /src
docker build -t tns-capital-skeleton:local .
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy:0.57.1 \
  image --scanners vuln tns-capital-skeleton:local
```

---

**Last updated:** 2026-10-21
