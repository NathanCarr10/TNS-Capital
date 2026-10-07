# TNS Capital Auth Service (NestJS)

The identity service the trading API trusts. It registers users, opening a trading account
for each one, and issues JWTs. The trading API only checks a token's *signature*; it never
sees a password.

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

## Register

```bash
curl -X POST http://localhost:4000/register \
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
curl -X POST http://localhost:4000/login \
  -H "Content-Type: application/json" \
  -d '{"username":"trader1","password":"password123"}'
```

Returns `{"token": "eyJhbGc..."}`, valid for 1 hour, with the claims `sub` (username),
`roles`, `iss`, `iat` and `exp` (see `contracts/auth-api.yaml`).

| User | Password | roles | Can access |
|---|---|---|---|
| any registered user | their own | `["USER"]` | only their own account |
| `admin` (seeded) | `adminPassword` | `["ADMIN"]` | every account, the DLQ, opening accounts |

Roles come from `users.role`. The trading API resolves which account a USER owns from the
token's `sub`.

## Errors

Every error is `{ "errorCode", "message", "timestamp" }`: `VAL-422` (400, invalid body or
unknown property), `AUTH-401` (wrong credentials, with no hint which part was wrong),
`USR-409` (username taken), `NOT-404`, `REQ-405` and `SYS-500`.

## Tests

```bash
npm test
```

- `tests/auth-api.spec.ts`: the HTTP contract, with the database replaced by a fake
- `tests/password-hasher.spec.ts`: Argon2id hashing
- `tests/users.service.spec.ts`: registration and login against a real Postgres with the
  `db/` schema (set `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD`)

## The shared secret

The trading API (Java) and this service both know the same HMAC secret, read from the
`JWT_SECRET` environment variable (set in the project's `.env`; there is no default). That
shared secret is the entire trust relationship. The trading API never calls this service at
request time; it only verifies that a token's signature could have come from something that
knows the same secret.
