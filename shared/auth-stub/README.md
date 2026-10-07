# TNS Capital Auth Service (NestJS)

The identity service the trading API trusts. It registers users, opening a trading account
for each one, and issues access tokens (JWTs) and refresh tokens. The trading API only checks
an access token's *signature*; it never sees a password.

## Run it

Normally it runs from the project's `docker-compose.yml` (service `auth-stub`, port 4000).
To run it on its own (Node 20+):

```bash
cd shared/auth-stub
npm install
npm run build
JWT_SECRET=<same value as in the project .env> DB_HOST=localhost DB_PASSWORD=... npm start
```

Listens on `http://localhost:4000`. It refuses to start without a `JWT_SECRET` of at least 32 bytes.

The users table gained two columns for refresh tokens. The schema scripts only run on an
empty database, so after pulling this change recreate yours with `scripts/recreate_db.sh`.

| Endpoint | Needs an access token | Purpose |
|---|---|---|
| `POST /auth/register` | no | create a user and their trading account |
| `POST /auth/login` | no | get an access token and a refresh token |
| `POST /auth/refresh` | no (takes the refresh token) | get a new access token |
| `POST /auth/logout` | no (takes the refresh token) | revoke the refresh token |
| `GET /auth/me` | **yes** | the user the access token belongs to |
| `GET /health` | no | liveness |

## Register

```bash
curl -X POST http://localhost:4000/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"trader1","password":"password123","holderName":"Ada Lovelace"}'
```

```json
{"id":4,"username":"trader1","createdAt":"...","accountId":11,"accountNumber":"ACC-U000004"}
```

Registration creates the user **and** their trading account in one transaction: status
`ACTIVE`, cash balance `0`, holder name `holderName` (defaults to the username). The user is
linked to it through `users.account_id`. Usernames are 3-100 characters, passwords 8-256.

## Log in

```bash
curl -X POST http://localhost:4000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"trader1","password":"password123"}'
```

```json
{"accessToken":"eyJhbGc...","refreshToken":"Xq3v...43 characters"}
```

- **`accessToken`**: a JWT valid for 1 hour, with the claims `sub` (username), `roles`, `iss`,
  `iat` and `exp`. Send it as `Authorization: Bearer <accessToken>` to the trading API and to
  `GET /auth/me`.
- **`refreshToken`**: an opaque random value valid for 7 days. Only its SHA-256 hash is
  stored. A user has one refresh token at a time: logging in again replaces it, so the
  previous one stops working.

| User | Password | roles | Can access |
|---|---|---|---|
| any registered user | their own | `["USER"]` | only their own account |
| `admin` (seeded) | `adminPassword` | `["ADMIN"]` | every account, the DLQ, opening accounts |

Roles come from `users.role`. The trading API resolves which account a USER owns from the
token's `sub`.

## Refresh and log out

```bash
# A new access token; the refresh token stays the same until it expires
curl -X POST http://localhost:4000/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refreshToken>"}'
# -> {"accessToken":"eyJhbGc..."}

# Revoke the refresh token
curl -X POST http://localhost:4000/auth/logout \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refreshToken>"}'
# -> {"loggedOut":true}
```

An unknown, expired or logged-out refresh token gets `401 AUTH-401` "invalid or expired
refresh token". Logging out revokes only the refresh token: access tokens already issued
keep working until they expire, at most an hour later.

## Protected routes

```bash
curl http://localhost:4000/auth/me -H "Authorization: Bearer <accessToken>"
# -> {"username":"trader1","roles":["USER"],"accountId":11}
```

Every route needs an access token unless it is marked `@Public()`. Two global guards
enforce this, in order:

1. `JwtAuthGuard` checks the bearer token (HS256 signature, issuer, expiry), looks the user
   up again and puts them on the request. Missing or invalid token: `401 AUTH-401`.
2. `RolesGuard` enforces `@Roles('ADMIN')`. Missing role: `403 AUTH-403`.

`@CurrentUser()` gives a handler the authenticated user.

## Code layout

| Layer | File | Job |
|---|---|---|
| HTTP | `auth/auth.controller.ts` | routes and DTO validation only |
| Service | `auth/auth.service.ts` | `register`, `login`, `refresh`, `logout`, `validate` |
| Service | `auth/token.service.ts` | `issue`, `verify`, `decode`; generating and hashing refresh tokens |
| Data | `users/users.service.ts` | SQL on the `users` and `accounts` tables; never sees a plaintext password |
| Access control | `auth/guards/`, `auth/decorators/` | `JwtAuthGuard`, `RolesGuard`, `@Public()`, `@Roles()`, `@CurrentUser()` |

## Errors

Every error is `{ "errorCode", "message", "timestamp" }`: `VAL-422` (400, invalid body or
unknown property), `AUTH-401` (wrong credentials, with no hint which part was wrong),
`AUTH-403` (missing role), `USR-409` (username taken), `NOT-404`, `REQ-405` and `SYS-500`.

## Tests

```bash
npm test
```

- `tests/auth-api.spec.ts`: the HTTP endpoints end to end, including the guards, with the
  database replaced by a fake
- `tests/auth.service.spec.ts`, `tests/token.service.spec.ts`, `tests/guards.spec.ts`: unit tests
- `tests/password-hasher.spec.ts`: Argon2id hashing
- `tests/users.service.spec.ts`: the SQL against a real Postgres with the `db/` schema. Skipped
  unless `DB_HOST` is set (also set `DB_PORT`, `DB_USER`, `DB_PASSWORD`)

## The shared secret

The trading API (Java) and this service both know the same HMAC secret, read from the
`JWT_SECRET` environment variable (set in the project's `.env`; there is no default). That
shared secret is the entire trust relationship. The trading API never calls this service at
request time; it only verifies that a token's signature could have come from something that
knows the same secret.
