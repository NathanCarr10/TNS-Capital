# OWASP Top 10 Security Review

The review records, for every OWASP Top 10 (2021) category, what was checked, what was found,
and what the team decided to do about it. Built from [OWASP_REVIEW_TEMPLATE.md](OWASP_REVIEW_TEMPLATE.md).

## Review details

| | |
|---|---|
| Platform reviewed | Trading API (Spring Boot, `src/`), auth service (NestJS, `shared/auth-service/`), ETL (`etl/`), Postgres (`db/`), Kafka and kafka-ui, `docker-compose.yml`, Jenkins CI (`Jenkinsfile`) |
| Deployment assumed | Local `docker compose` on a developer machine. Nothing is internet-facing. Findings marked Accepted on that basis say so and must be revisited before any shared deployment. |
| Commit reviewed | `3c6b7eea` (Development, after PR #91) |
| Review date | 2026-10-08 |
| Reviewers | Nokuvimba |
| Standard | [OWASP Top 10 (2021)](https://owasp.org/Top10/) |

## Keeping this review current

This review describes the code at the commit above. It is only useful while it still matches the
code, so update it **in the same pull request** as the change, whenever a PR:

- **fixes a finding:** change Open to Fixed, and point the evidence at the fix and its test;
- **makes an Accepted reason untrue:** for example, the platform is deployed somewhere shared.
  Change it to Open;
- **adds a new risk:** for example, a new public route, secret, dependency, outbound URL
  fetch or Kafka topic. Add a finding to its category;
- **moves or renames evidence:** a file, test or config a finding points to.

When the whole platform is reviewed again, update the commit and date in the table above.
Never edit [OWASP_REVIEW_TEMPLATE.md](OWASP_REVIEW_TEMPLATE.md) to record findings; it stays blank.

## Rules

1. **Every category has at least one finding.** No category is left blank or skipped.
2. **Every finding has evidence**: a file, test, config or scan report, as a repo-relative path.
3. **A finding of "None" names what was checked.**
4. **Every disposition comes from the vocabulary below**, written exactly as shown.
5. **An Accepted finding states its residual risk and why the team carries it.**
6. **The focus categories go deepest.** This sprint's focus is A01, A02 and A07.

## Disposition vocabulary

| Disposition | Use when | The finding must also give |
|---|---|---|
| **Fixed** | A weakness was found and the fix is merged. | The fix, and the test or scan that proves it. |
| **Accepted** | A weakness exists and the team chooses to carry it. | The residual risk and why the team carries it. |
| **Open** | A weakness exists and a fix is planned but not merged. | The ticket or issue that tracks the fix. |
| **No action** | The category was checked and nothing was found. | What was checked (rule 3). |
| **Not applicable** | The category cannot occur on this platform. | Why it cannot occur. |

## Summary

| Category | Dispositions | Findings |
|---|---|---|
| A01 Broken Access Control | No action, Open, Accepted | 7 |
| A02 Cryptographic Failures | No action, Open, Accepted | 7 |
| A03 Injection | No action | 4 |
| A04 Insecure Design | Open, No action, Accepted | 4 |
| A05 Security Misconfiguration | Open, Accepted, No action | 6 |
| A06 Vulnerable and Outdated Components | No action, Fixed, Open | 4 |
| A07 Identification and Authentication Failures | No action, Accepted, Open, Fixed | 11 |
| A08 Software and Data Integrity Failures | Accepted, No action, Open | 4 |
| A09 Security Logging and Monitoring Failures | No action, Open, Accepted | 5 |
| A10 Server-Side Request Forgery | Not applicable | 1 |

### Open findings, highest risk first

| ID | Finding | Risk |
|---|---|---|
| A05-1 | kafka-ui is published on port 8090 with no login and can produce messages | High |
| A01-5 | Orders produced straight to Kafka bypass account ownership checks | High |
| A07-4 | A default `admin` / `adminPassword` account is seeded into every database | High |
| A07-3 | No rate limiting or lockout on login, register or refresh | High |
| A02-5 | The placeholder `JWT_SECRET` in `.env.example` passes the length check | High |
| A01-4 | Any `USER` can create instruments or make them untradable | Medium |
| A04-1 | `POST /api/v1/orders` ignores the idempotency key, so a retry places a second order | Medium |
| A06-3 | The auth service runs on Node.js 20, which reached end of life on 2026-04-30 | Medium |
| A08-4 | Auth service tests and image are not part of the Jenkins pipeline | Medium |
| A06-4 | The auth service image is never scanned by Trivy | Medium |
| A09-2 | Logins, successful or failed, are not logged | Medium |
| A09-4 | Failed orders in the DLQ are marked RESOLVED after 10 seconds without review | Medium |
| A07-11 | The auth contract and `.env.example` still describe signed (JWT) refresh tokens | Low |

---

## A01 Broken Access Control — focus

| ID | Finding | Evidence | Disposition | Residual risk and rationale |
|---|---|---|---|---|
| A01-1 | **Default deny on the trading API.** Every route needs a valid token except Swagger (`/swagger-ui/**`, `/v3/api-docs/**`). Missing, malformed, expired, wrongly signed and unsigned tokens all get 401 AUTH-401. | `src/main/java/com/neueda/leap/security/SecurityConfig.java` (`anyRequest().authenticated()`); `src/test/java/com/neueda/leap/security/JwtSecurityTest.java` (`noTokenIsRejected`, `malformedTokenIsRejected`, `wrongSignatureIsRejected`, `expiredTokenIsRejected`, `unsignedTokenIsRejected`) | No action | — |
| A01-2 | **Account ownership.** Every route that takes an account, position or order calls `AccountAccessGuard.checkAccess`. Ownership is resolved from the token's `sub` through `users.account_id`, never from the request. `placeOrder` checks ownership before checking the account exists, so non-owners cannot probe account IDs. | `src/main/java/com/neueda/leap/security/AccountAccessGuard.java`; `controllers/AccountController.java`, `OrderController.java`, `PositionController.java`; `src/test/java/com/neueda/leap/integration/AccountOwnershipIT.java` (`cannotReadOtherAccount`, `cannotChangeOtherAccount`, `cannotOrderOnOtherAccount`, `cannotSeeOtherOrders`, `unknownAccountIsForbidden`); `security/AccountAccessGuardTest.java` | No action | — |
| A01-3 | **Admin-only operations.** Opening accounts and every DLQ endpoint need `ROLE_ADMIN` through `@PreAuthorize`. The token's `roles` claim maps to Spring roles. | `controllers/AccountController.java` (`createAccount`); `controllers/DeadLetterQueueController.java` (all 5 endpoints); `JwtSecurityTest` (`dlqRequiresAdminRole`, `dlqRefusesTokenWithoutRoles`, `dlqAllowsAdminRoleFromToken`); `AccountOwnershipIT.cannotCreateAccounts` | No action | — |
| A01-4 | **Instrument writes are open to every user.** `POST /api/v1/instruments` and `PUT /api/v1/instruments/{symbol}` have no role check. Any registered `USER` can add instruments, or set `tradable: false` on one and stop all trading in it. No test covers these routes with a `USER` token. **Fix:** add `@PreAuthorize("hasRole('ADMIN')")` to both, plus a test that a `USER` gets 403. | `src/main/java/com/neueda/leap/controllers/InstrumentController.java` (`createInstrument`, `updateInstrument`) | Open | Tracked: _ticket to raise_ |
| A01-5 | **Orders produced to Kafka bypass ownership.** `OrderMessageListener` executes any message on the `orders` topic for the `accountId` inside it. Ownership is only checked at the REST layer. Anyone who can produce to `orders` can trade on any account, and kafka-ui (A05-1) lets anyone who can reach port 8090 do exactly that. **Fix:** close A05-1 and keep Kafka reachable only from the compose network (A05-2). | `src/main/java/com/neueda/leap/kafka/OrderMessageListener.java`; `services/OrderService.java` (`processOrderEvent`); `docker-compose.yml` (`kafka-ui`, `kafka` ports) | Open | Tracked: _ticket to raise_ (same fix as A05-1) |
| A01-6 | **Auth service default deny.** `JwtAuthGuard` and `RolesGuard` are registered globally. Only register, login, refresh, logout and health are `@Public()`. `/auth/me` needs an access token. | `shared/auth-service/src/auth/auth.module.ts` (`APP_GUARD`); `src/auth/auth.controller.ts`; `src/health/health.controller.ts`; `tests/guards.spec.ts`; `tests/auth-api.spec.ts` (`GET /auth/me (protected by JwtAuthGuard)`) | No action | — |
| A01-7 | **CORS.** Checked `SecurityConfig.java` and the auth service's `main.ts` / `app.module.ts`. Neither enables CORS, so browsers block cross-origin calls to both APIs. | `src/main/java/com/neueda/leap/security/SecurityConfig.java`; `shared/auth-service/src/main.ts` | No action | — |

## A02 Cryptographic Failures — focus

| ID | Finding | Evidence | Disposition | Residual risk and rationale |
|---|---|---|---|---|
| A02-1 | **Password hashing.** Argon2id with m = 64 MiB, t = 3, p = 4, above the OWASP minimum (m = 19 MiB, t = 2). Each hash has a random salt. Before PR #91 a second auth service used bcrypt. It was removed (`cfa4ee7b`), and code, schema comments and `SECURITY_GUIDE.md` now all say Argon2id. | `shared/auth-service/src/users/password-hasher.ts`; `tests/password-hasher.spec.ts`; `db/tables/08_users.sql`; `docs/SECURITY_GUIDE.md` §3 | No action | — |
| A02-2 | **Refresh tokens at rest.** Each is 256 random bits (`randomBytes(32)`), and only its SHA-256 hash is stored. A fast hash is enough because the token cannot be guessed, so a database leak gives no usable tokens. | `shared/auth-service/src/auth/token.service.ts` (`generateRefreshToken`, `hashRefreshToken`); `db/tables/09_refresh_tokens.sql`; `tests/token.service.spec.ts` (`generates a 256-bit random token and its SHA-256 hash`); `tests/auth-api.spec.ts` (`stores only the hash of the refresh token, valid for 7 days`) | No action | — |
| A02-3 | **JWT algorithm pinned.** The auth service signs and verifies with HS256 only (`algorithms: ['HS256']`), never trusting the token header. The trading API's `NimbusJwtDecoder.withSecretKey` accepts HS256 only. Unsigned (`alg: none`) and forged tokens are rejected on both sides. | `shared/auth-service/src/auth/auth.module.ts`, `token.service.ts` (`verify`); `tests/token.service.spec.ts` (tampered payload), `tests/auth-api.spec.ts` (`signs with HS256 and the shared secret`); `JwtSecurityTest.unsignedTokenIsRejected`, `wrongSignatureIsRejected` | No action | — |
| A02-4 | **Secret handling.** `JWT_SECRET` and `DB_PASSWORD` come only from the environment. `application.yml` has no default, and `docker-compose.yml` refuses to start without them (`${VAR:?}`). The auth service refuses a secret shorter than 32 bytes. `.env` is gitignored and Gitleaks scans the full history on every build. | `src/main/resources/application.yml` (`jwt.shared-secret: ${JWT_SECRET}`); `shared/auth-service/src/config/env.validation.ts`; `tests/auth-api.spec.ts` (`JWT_SECRET validation`); `.gitignore`; `Jenkinsfile` stage `Secret Scan` | No action | — |
| A02-5 | **The example secret works as a real one.** `.env.example` sets `JWT_SECRET=change-me-to-a-random-value-of-at-least-32-bytes`, which is 48 bytes and passes the length check. Copying the file without editing it starts both services with a published signing key, and anyone could forge an `ADMIN` token. `SECURITY_GUIDE.md` §1 also shows a literal example secret. **Fix:** leave the value empty in `.env.example`, reject values starting with `change-me` in `validateEnv`, and replace the guide's example with `${JWT_SECRET}`. | `.env.example`; `shared/auth-service/src/config/env.validation.ts`; `docs/SECURITY_GUIDE.md` §1 | Open | Tracked: _ticket to raise_ |
| A02-6 | **Shared symmetric key.** The trading API holds the same HS256 secret the auth service signs with, so a compromised trading API could mint tokens. | `src/main/resources/application.yml` (`jwt.shared-secret`); `shared/auth-service/src/auth/auth.module.ts` | Accepted | **Residual risk:** an attacker who reads the trading API's environment can sign tokens for any user, including `ADMIN`. **Why carried:** both services sit in one trust boundary, run by one team, and the trading API's environment is already the most sensitive part of the platform. Moving to RS256 with a public key adds key distribution for no gain until a third service consumes tokens. Revisit then. |
| A02-7 | **No encryption in transit.** Every hop is plain HTTP, Kafka `PLAINTEXT` or default Postgres. The trading API sends an HSTS header, but it has no effect without TLS. | `docker-compose.yml` (published ports, `KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092`); `SecurityConfig.java` (`httpStrictTransportSecurity`) | Accepted | **Residual risk:** anyone who can capture traffic on the host network sees passwords at login, refresh tokens and access tokens. **Why carried:** the platform runs on a developer machine and its traffic stays on loopback or the Docker bridge. TLS termination is a precondition for any shared deployment and must be added then. |

## A03 Injection

| ID | Finding | Evidence | Disposition | Residual risk and rationale |
|---|---|---|---|---|
| A03-1 | **None.** Checked every MyBatis annotation in `src/main/java/com/neueda/leap/mappers/` for `${}` substitution (0 found; all use `#{}` binding), and both `@Query` methods (named parameters only). Semgrep `p/owasp-top-ten` runs on every build. | `src/main/java/com/neueda/leap/mappers/`; `repositories/AccountRepository.java`, `DeadLetterMessageRepository.java`; `Jenkinsfile` stage `SAST` | No action | — |
| A03-2 | **None.** Checked every `query()` call in the auth service: all values go through `$1` parameters. The only template interpolation is the constant column list `USER_COLUMNS`. | `shared/auth-service/src/users/users.service.ts`, `refresh-tokens.service.ts` | No action | — |
| A03-3 | **None.** The ETL builds table and column names with f-strings, but they come from code (`PRIMARY_KEYS`, columns of its own `SELECT`), never from input. Values go through `execute_values`. If a name ever comes from outside, it must use `psycopg2.sql.Identifier`. | `etl/load.py` (`upsert`); `etl/extract.py` | No action | — |
| A03-4 | **None.** Checked request validation. Trading API DTOs use Bean Validation with `@Valid`, and the idempotency key is restricted to `^[a-zA-Z0-9_-]{1,100}$`. Auth DTOs use class-validator with length limits and reject unknown properties. | `src/main/java/com/neueda/leap/dtos/PlaceOrderRequest.java`, `CashTransactionRequest.java`; `controllers/OrderControllerValidationTest.java`; `shared/auth-service/src/auth/dto/`; `tests/auth-api.spec.ts` (`rejects unexpected properties`) | No action | — |

## A04 Insecure Design

| ID | Finding | Evidence | Disposition | Residual risk and rationale |
|---|---|---|---|---|
| A04-1 | **REST idempotency key is not used.** `POST /api/v1/orders` requires an `idempotencyKey` but never passes it on. It generates a new `orderId` per request, and `OrderEvent` has no key field. A client that retries after a timeout places the order twice. Redelivered Kafka messages are still de-duplicated by `orderId` (A04-2). No test covers a repeated POST. **Fix:** carry the key in `OrderEvent`, return the existing order for a repeated key from the same account, and add an integration test. | `src/main/java/com/neueda/leap/controllers/OrderController.java` (`placeOrder`); `kafka/events/OrderEvent.java`; `services/OrderService.java` (`processOrderEvent`) | Open | Tracked: _ticket to raise_ |
| A04-2 | **None.** Checked Kafka redelivery: `processOrderEvent` skips an `orderId` it has already stored, so a redelivered message does not execute twice. Failed orders go to the DLQ, and only `ADMIN` can replay them. A test proves a failed order is stored once. No test redelivers a *successful* order; adding one would harden this finding. | `services/OrderService.java` (`processOrderEvent`); `integration/RejectedOrderIT.java` (`Failed order is saved as REJECTED with exactly one trade event and DLQ entry, and can be replayed`); `integration/DeadLetterQueueEndToEndIT.java` | No action | — |
| A04-3 | **None.** Checked business limits: buys need sufficient cash and sells need sufficient holdings. Withdrawals cannot overdraw. | `strategies/BuyOrderStrategyTest.java`, `SellOrderStrategyTest.java`; `AccountOwnershipIT.cannotOverdraw` | No action | — |
| A04-4 | **Self-funded accounts.** Anyone can register (opening an `ACTIVE` account) and deposit up to 1,000,000,000.00 per request into their own account, with nothing behind the money. | `shared/auth-service/src/auth/auth.controller.ts` (`register`, `@Public()`); `src/main/java/com/neueda/leap/dtos/CashTransactionRequest.java` (`@DecimalMax`) | Accepted | **Residual risk:** balances do not represent real money, and anyone can create many funded accounts. **Why carried:** this is a trading simulation. There is no real money or payment rail, and self-service registration and deposits are features. Revisit if balances are ever linked to real funds. |

## A05 Security Misconfiguration

| ID | Finding | Evidence | Disposition | Residual risk and rationale |
|---|---|---|---|---|
| A05-1 | **kafka-ui is open.** It is published on port 8090 (all host interfaces) with no authentication and no read-only flag. Anyone who can reach the host can read every order and trade event and produce messages to any topic, including `orders` (A01-5). **Fix:** set `KAFKA_CLUSTERS_0_READONLY: "true"`, bind the port to `127.0.0.1`, or remove kafka-ui from the default profile. | `docker-compose.yml` (`kafka-ui`) | Open | Tracked: _ticket to raise_ |
| A05-2 | **Infrastructure ports are published to all interfaces.** Kafka 9092 (PLAINTEXT, no SASL), Zookeeper 2181 and Postgres 5432 are bound to `0.0.0.0`. | `docker-compose.yml` (`ports` on `kafka`, `zookeeper`, `postgres`) | Accepted | **Residual risk:** a machine on the same network can reach them. Postgres still needs `DB_PASSWORD`, and Kafka advertises `kafka:9092`, which does not resolve outside the compose network, so ordinary clients fail after bootstrap. **Why carried:** developers connect to these from the host with their own tools. Binding to `127.0.0.1` is the cheap next step and should come with A05-1. |
| A05-3 | **None.** Checked error responses. Both services return the platform envelope with no stack traces or framework messages. Unexpected errors are logged server-side and returned as SYS-500. | `src/main/java/com/neueda/leap/controllers/GlobalExceptionHandler.java` (`handleGenericException`); `GlobalExceptionHandlerTest.java` (`Unexpected exceptions return SYS-500 without leaking the message`); `shared/auth-service/src/common/api-exception.filter.ts`; `tests/auth-api.spec.ts` (`hides internal errors behind SYS-500`) | No action | — |
| A05-4 | **Auth service sends no security headers.** The trading API sets `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff` and HSTS. The auth service sets none (no `helmet`). | `SecurityConfig.java` (`headers`); `JwtSecurityTest.validTokenIsAccepted` (asserts the headers); `shared/auth-service/package.json` (no `helmet`) | Accepted | **Residual risk:** a browser rendering an auth service response gets no clickjacking or MIME-sniffing protection. **Why carried:** the service returns only JSON to API clients and serves no HTML, so these headers protect nothing today. Add `helmet` if a browser UI is ever served from it. |
| A05-5 | **Actuator details go to every user.** `/actuator/health` (`show-details: always`), `/actuator/info` and `/actuator/metrics` need a token but no role, so any `USER` sees database status and JVM and HTTP metrics. | `src/main/resources/application.yml` (`management`); `SecurityConfig.java` | Accepted | **Residual risk:** a logged-in user learns component health and request volumes; no secrets are exposed. **Why carried:** the information is low value and helps the team debug. Restrict `metrics` and health details to `ADMIN` before any shared deployment. |
| A05-6 | **None.** Checked container users and dev-only settings. The trading API, auth service and ETL images all drop root (`USER app`, `USER node`, `USER etl`). The H2 console and SQL TRACE logging are only in `application-dev.yml`, which `docker-compose.yml` does not activate. | `Dockerfile`; `shared/auth-service/Dockerfile`; `etl/Dockerfile`; `src/main/resources/application-dev.yml` | No action | — |

## A06 Vulnerable and Outdated Components

| ID | Finding | Evidence | Disposition | Residual risk and rationale |
|---|---|---|---|---|
| A06-1 | **None.** Checked that dependency scanning exists and gates the build. Trivy scans the repo manifests (`pom.xml`, `shared/auth-service/package-lock.json`, `etl/requirements.txt`) and the trading API image, failing on fixable CRITICAL CVEs. ETL dependencies are pinned to exact versions so Trivy can match them. | `Jenkinsfile` stage `Dependency Scan`; `etl/requirements.txt`; SARIF reports under `security-reports/` in each Jenkins build | No action | — |
| A06-2 | **Vulnerable `tar` removed.** `argon2` was upgraded to 0.44, which dropped a dependency on a vulnerable `tar`. | Commit `729721d3`; `shared/auth-service/package.json` (`"argon2": "^0.44.0"`) | Fixed | — |
| A06-3 | **Node.js 20 is end of life.** The auth service image is built on `node:20-alpine`, and Node.js 20 stopped receiving security fixes on 2026-04-30. **Fix:** move to `node:22-alpine` (LTS), update `engines` and `@types/node`, and re-run the tests. | `shared/auth-service/Dockerfile`; `shared/auth-service/package.json` (`engines`, `@types/node`) | Open | Tracked: _ticket to raise_ |
| A06-4 | **The auth service image is not scanned.** Trivy's image scan covers only the trading API image. The auth service lockfile is scanned, but its OS packages and Node runtime are not. HIGH findings never fail the build, and there is no record of who triages them. **Fix:** build and Trivy-scan the auth service image in CI (with A08-4), and record HIGH triage in this review. | `Jenkinsfile` stages `Build Image`, `Dependency Scan` | Open | Tracked: _ticket to raise_ |

## A07 Identification and Authentication Failures — focus

| ID | Finding | Evidence | Disposition | Residual risk and rationale |
|---|---|---|---|---|
| A07-1 | **Generic login error.** A wrong password and an unknown username get the same 401 AUTH-401 "invalid username or password". | `shared/auth-service/src/auth/auth.service.ts` (`login`); `tests/auth-api.spec.ts` (`gives the same answer for an unknown user, so usernames cannot be probed`) | No action | — |
| A07-2 | **Usernames can still be discovered.** `login` skips the Argon2 check for an unknown username, so it answers measurably faster. Separately, `/auth/register` answers 409 USR-409 for a taken username. | `src/auth/auth.service.ts` (`login`, `register`); `tests/auth-api.spec.ts` (`rejects a duplicate username with 409 USR-409`) | Accepted | **Residual risk:** an attacker can build a list of valid usernames to target with password guessing. **Why carried:** public registration has to tell a person their chosen name is taken, so closing the timing gap alone gains nothing. Usernames are not secret. The real defence is limiting guesses (A07-3). |
| A07-3 | **No brute-force or credential-stuffing protection.** `/auth/login`, `/auth/register` and `/auth/refresh` have no rate limit, no lockout and no back-off. Only Argon2's cost (~50–100 ms per guess) slows an attacker. **Fix:** add `@nestjs/throttler`, limited per IP and per username on login, and log the rejections (A09-2). | `shared/auth-service/package.json` (no throttler); `src/auth/auth.controller.ts` | Open | Tracked: _ticket to raise_ |
| A07-4 | **Default admin credentials.** `db/data/06_users_seed.sql` seeds `admin` / `adminPassword` with a real hash and the `ADMIN` role into every database the `db` image creates. The password is also published in the auth service README. Anyone who knows it can read and trade on every account. **Fix:** seed the admin only when a password is supplied at init time (for example `ADMIN_PASSWORD` in `.env`), or force a password change on first login. | `db/data/06_users_seed.sql`; `shared/auth-service/README.md` (users table); `tests/users.service.spec.ts` (`finds the seeded admin with ADMIN and no account`) | Open | Tracked: _ticket to raise_ |
| A07-5 | **Password policy.** Length 8 to 256, enforced in the DTO and again in `hashPassword`. No complexity rules (in line with NIST SP 800-63B) and no breached-password check. | `src/auth/dto/register.dto.ts`; `src/users/password-hasher.ts`; `tests/password-hasher.spec.ts` (`should reject passwords shorter than 8 characters`) | Accepted | **Residual risk:** users can choose common passwords such as `password1`, which credential stuffing finds quickly. **Why carried:** the length rule follows current NIST guidance, and screening against a breached-password list needs an external service or a large bundled list. Revisit together with A07-3, which limits how many guesses are possible. |
| A07-6 | **Token lifetimes.** Access tokens last 15 minutes. Refresh tokens last 7 days from issue, and each refresh issues a new one, so an active user stays signed in and a session idle for 7 days ends. | `src/auth/token.service.ts` (`ACCESS_TOKEN_TTL_SECONDS`, `REFRESH_TOKEN_TTL_SECONDS`); `tests/auth-api.spec.ts` (`expires the token 15 minutes after it is issued`, `stores only the hash of the refresh token, valid for 7 days`) | No action | — |
| A07-7 | **Refresh revocation: story 6's decision.** Story 6 decided that refresh tokens must be revocable, not just left to expire. It chose **rotation with reuse detection**: each refresh token works once; tokens rotated from one login share a family id; presenting a spent token revokes the whole family (thief and user both log in again); logout revokes the whole family; other devices' sessions are untouched. The first implementation (`8e47994d`) kept tokens in memory, so a restart logged everyone out and it could not run as more than one instance. PR #91 (`781f44dc`) moved the store to Postgres, with the spend-and-replace done in one transaction so only one of two concurrent refreshes wins. | `shared/auth-service/src/users/refresh-tokens.service.ts` (`rotate`, `revokeFamily`); `db/tables/09_refresh_tokens.sql`; `tests/auth-api.spec.ts` (`lets each refresh token be used only once`, `revokes the whole session when a spent refresh token is reused`, `leaves the user's other sessions alone when one is revoked for reuse`, `revokes the later tokens of the session too`); `tests/users.service.spec.ts` (`lets only one of two concurrent refreshes with the same token win`) | Fixed | — |
| A07-8 | **Revocation does not reach access tokens** (story 6 residual risk). Logout, reuse detection and role changes stop *new* access tokens only. The trading API verifies tokens locally, so an access token already issued keeps working, with the roles it was issued with, until it expires. | `shared/auth-service/src/auth/auth.service.ts` (`logout` comment); `shared/auth-service/README.md` (Refresh and log out); `SecurityConfig.java` (local `NimbusJwtDecoder`, no revocation lookup) | Accepted | **Residual risk:** a stolen access token, or one belonging to a user just demoted from `ADMIN`, works for at most 15 minutes. **Why carried:** checking a deny-list on every request would put the auth service or a shared store in the path of every trading call. The 15-minute lifetime was chosen to bound this window. |
| A07-9 | **A thief who refreshes first keeps the session** (story 6 residual risk). If a stolen refresh token is used before the user's copy, the thief holds the live token. Reuse is only detected when the user next refreshes (whole family revoked) or logs out (`revokeFamily` matches spent tokens too). Until then the thief can keep rotating. | `shared/auth-service/src/users/refresh-tokens.service.ts` (`rotate`, `revokeFamily`); `tests/auth-api.spec.ts` (`revokes the whole session when a spent refresh token is reused`) | Accepted | **Residual risk:** while the real user is inactive, a thief with a stolen refresh token stays signed in. **Why carried:** this is inherent to bearer refresh tokens. Closing it needs sender-constrained tokens (for example DPoP) bound to a client key, which no client of this platform supports. Detection on the user's next refresh limits the damage. |
| A07-10 | **Token type confusion.** Refresh tokens are opaque random strings, not JWTs, so they cannot pass as access tokens. The auth service also checks `typ: "access"` and `iss`. The trading API checks signature and expiry but not `iss` or `typ`. | `shared/auth-service/src/auth/token.service.ts` (`verify`); `tests/auth-api.spec.ts` (`rejects a refresh token used as an access token`, `rejects an access token used as a refresh token`); `SecurityConfig.java` (`jwtDecoder`, no claim validators) | Accepted | **Residual risk:** the trading API would accept any JWT signed with `JWT_SECRET`, whatever its issuer or type. **Why carried:** only the auth service holds the secret and it only signs access tokens, so no other JWT can exist today. Adding issuer and `typ` validators to the decoder is a small follow-up if a second token type or issuer appears. |
| A07-11 | **Docs still describe the old refresh tokens.** The auth contract says refresh tokens are "signed with a separate secret (`JWT_REFRESH_SECRET`)", and `.env.example` asks for that variable. Since PR #91 refresh tokens are opaque and nothing reads it. Readers may wrongly believe refresh tokens are JWTs. **Fix:** update both. | `contracts/auth-api.yaml` (`/auth/refresh` description); `.env.example` (`JWT_REFRESH_SECRET`) | Open | Tracked: _ticket to raise_ |

## A08 Software and Data Integrity Failures

| ID | Finding | Evidence | Disposition | Residual risk and rationale |
|---|---|---|---|---|
| A08-1 | **Kafka messages are not signed.** Order and trade events carry no signature or producer identity, so the consumer cannot tell a trading API message from an injected one (see A01-5). | `src/main/java/com/neueda/leap/kafka/events/MessageEnvelope.java`; `kafka/OrderMessageListener.java` | Accepted | **Residual risk:** anyone able to produce to Kafka can inject orders or trade events. **Why carried:** signing messages adds key management to every producer and consumer. Restricting who can reach Kafka (A01-5, A05-1, A05-2) is the proportionate control while the trading API is the only legitimate producer. |
| A08-2 | **None.** Checked build inputs. The CI scanner images are pinned to exact versions (Gitleaks v8.21.2, Semgrep 1.99.0, Trivy 0.57.1). `package-lock.json` is committed and installed with `npm ci`, and ETL dependencies are pinned exactly. | `Jenkinsfile` (`environment`); `shared/auth-service/Dockerfile` (`npm ci`); `etl/requirements.txt` | No action | — |
| A08-3 | **Base images are pinned by tag, not digest.** Examples: `node:20-alpine`, `python:3.12-slim`, `postgres:16`, `eclipse-temurin:21-jre-alpine`, `confluentinc/cp-kafka:7.5.0`, `kafbat/kafka-ui:v1.5.0`. | `Dockerfile`; `shared/auth-service/Dockerfile`; `etl/Dockerfile`; `db/Dockerfile`; `docker-compose.yml` | Accepted | **Residual risk:** if an upstream tag is re-pointed, a rebuild silently picks up different contents. **Why carried:** these are official or vendor images, and floating minor tags bring OS security fixes automatically. The Trivy image scan catches known CVEs in what is pulled. Digest pinning would need a bot to keep images current. |
| A08-4 | **Auth service not in CI.** Jenkins runs the Maven tests and the ETL tests but never runs `npm test` or builds `shared/auth-service`. The refresh-rotation, guard and token tests behind A07 can fail without blocking a merge. **Fix:** add an auth service stage (`npm ci && npm test`, then build its image) to the `Jenkinsfile`. | `Jenkinsfile` (stages `Test`, `ETL Tests`; no auth service stage) | Open | Tracked: _ticket to raise_ |

## A09 Security Logging and Monitoring Failures

| ID | Finding | Evidence | Disposition | Residual risk and rationale |
|---|---|---|---|---|
| A09-1 | **Token reuse is logged.** A reused refresh token is logged as a warning with the user id, never the token. | `shared/auth-service/src/auth/auth.service.ts` (`refresh`); `tests/auth.service.spec.ts` (`rejects a reused token with AUTH-401 and logs the reuse`) | No action | — |
| A09-2 | **Logins are not logged.** Neither successful nor failed logins are logged, so a password-guessing or credential-stuffing run leaves no trace. **Fix:** log `login_success` and `login_failure` with username, client IP and timestamp (never the password or tokens), together with A07-3. | `shared/auth-service/src/auth/auth.service.ts` (`login`, no logger call) | Open | Tracked: _ticket to raise_ |
| A09-3 | **None.** Checked that logs carry no secrets. Searched both services for logged passwords, tokens or `Authorization` headers and found none. The trading API logs 403s with the account id only. | `src/main/java/com/neueda/leap/controllers/GlobalExceptionHandler.java` (`handleAccessDenied`); `shared/auth-service/src/` | No action | — |
| A09-4 | **Failed orders are auto-resolved.** With the default config, any DLQ message still `PENDING` after 10 seconds is marked `RESOLVED`. A failed order therefore leaves the pending queue before an admin can see or replay it. **Fix:** set `dlq.auto-resolve.enabled: false` outside demos, or alert before resolving. | `src/main/resources/application.yml` (`dlq.auto-resolve`); `src/main/java/com/neueda/leap/services/DeadLetterService.java` (`autoResolvePendingMessages`) | Open | Tracked: _ticket to raise_ |
| A09-5 | **No alerting.** Logs only go to container stdout. Nothing collects them or alerts on reuse warnings, 401 or 403 spikes, or SYS-500s. | `docker-compose.yml` (no logging driver or collector) | Accepted | **Residual risk:** an attack in progress is only noticed if someone happens to read the logs. **Why carried:** on a single developer machine a log pipeline costs more than it returns. A collector with alerts on the A09-1 and A09-2 events is required before any shared deployment. |

## A10 Server-Side Request Forgery

| ID | Finding | Evidence | Disposition | Residual risk and rationale |
|---|---|---|---|---|
| A10-1 | **None.** Searched `src/main`, `shared/auth-service/src` and `etl/` for outbound HTTP clients (`RestTemplate`, `WebClient`, `HttpClient`, `new URL(`, `fetch(`, `axios`, `requests`, `urllib`) and found none. The only outbound connections go to Postgres and Kafka at hosts fixed in config. The only URL fetched at all is the container healthcheck's fixed `http://localhost:4000/health`. No component fetches a URL a user can influence. | `src/main/java/`; `shared/auth-service/src/`; `etl/*.py`; `docker-compose.yml` (`healthcheck`) | Not applicable | — |
