# TNS Capital Auth Service

A NestJS service that issues the JWTs the trading API trusts, and replaces `shared/auth-stub`.
Access tokens are signed with the same `JWT_SECRET` as before, so the trading API's
`SecurityConfig` accepts them unchanged.

## Run it

```bash
cd auth-service
npm install
npm run build
JWT_SECRET=<same value as the project .env> JWT_REFRESH_SECRET=<a different value> npm start
```

Listens on `http://localhost:3000` (set `PORT` to change it).

| Variable | Required | Purpose |
|---|---|---|
| `JWT_SECRET` | yes | Signs access tokens. Shared with the trading API (`jwt.shared-secret`). At least 32 characters. |
| `JWT_REFRESH_SECRET` | yes | Signs refresh tokens. Known only to this service, and must differ from `JWT_SECRET`. |
| `JWT_ISSUER` | no | `iss` claim written and checked. Default `tns-capital-auth`. |

## Endpoints

| Method and path | Auth | Returns |
|---|---|---|
| `POST /auth/login` `{username, password}` | none | `{accessToken, refreshToken}` |
| `POST /auth/refresh` `{refreshToken}` | none | a new `{accessToken, refreshToken}`; the old refresh token is spent |
| `GET /auth/me` | `Bearer <accessToken>` | the verified access-token claims |
| `GET /health` | none | `{status: "up"}` |

The seeded users match the old stub: `alice`/`mission123` (MISSION_OPERATOR, ADMIN) and
`bob`/`wrongpermissions` (GUEST). They are held in memory until registration and the Postgres
`users` table are built.

```bash
curl -s -X POST localhost:3000/auth/login -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"mission123"}'
curl -s localhost:3000/auth/me -H "Authorization: Bearer <accessToken>"
curl -s -X POST localhost:3000/auth/refresh -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refreshToken>"}'
```

## Tokens

| | Access token | Refresh token |
|---|---|---|
| Lifetime | **15 minutes** | **7 days** from issue |
| Signed with | `JWT_SECRET` (HS256) | `JWT_REFRESH_SECRET` (HS256) |
| Claims | `sub`, `roles`, `typ: "access"`, `iss`, `jti`, `iat`, `exp` | `sub`, `typ: "refresh"`, `fam`, `iss`, `jti`, `iat`, `exp` |
| Checked by | the trading API and `JwtAuthGuard` | `POST /auth/refresh` only |

Both lifetimes are constants in [src/config.ts](src/config.ts). Because each refresh issues a new
7-day token, an active user stays signed in indefinitely; a session that goes 7 days without a
refresh has to log in again.

## Protected routes: `JwtAuthGuard`

`@UseGuards(JwtAuthGuard)` on a route requires `Authorization: Bearer <access token>`. The guard
checks the signature, expiry, issuer and `typ`, with the algorithm pinned to HS256 (so `alg: none`
and algorithm-swap tokens fail). The handler gets the verified claims through `@Claims()`.

Any failure, including a missing header, the wrong scheme (for example `Basic`), a malformed,
expired or tampered token, the wrong issuer, or a refresh token, returns 401 with exactly this body:

```json
{"errorCode":"AUTH-401","message":"Unauthorised or invalid token"}
```

Verification is local: the guard depends only on `TokenService`, which holds the secret and makes
no network call or database lookup.

## Security review: refresh tokens

**Rotation.** Each refresh token works once. `POST /auth/refresh` spends the presented token and
returns a new access token and a new refresh token.

**Revocation is built.** Every token rotated from one login shares a family id (`fam`). If a spent
refresh token is presented again, the service answers AUTH-401 and revokes the whole family, so
the latest token in that session stops working as well. Replay means the token was copied, and at
that point the service cannot tell the thief from the user, so both have to log in again. Other
sessions for the same user (another device) are not affected.

**Storage.** Only a SHA-256 hash of each refresh token is stored, never the token itself. A fast
hash is enough because the token is signed and has a random `jti`, so it cannot be guessed;
bcrypt is kept for passwords.

**Type confusion.** Refresh tokens are signed with a secret the trading API does not have, so the
API cannot accept one as an access token. Both sides also check `typ`, so an access token is
refused at `/auth/refresh` and a refresh token is refused by the guard.

**Residual risks**

- The refresh-token store is in memory. Restarting the service logs everyone out, and it will not
  work with more than one instance. It moves to Postgres along with the users table.
- An access token cannot be revoked. A stolen one keeps working on the trading API until it
  expires, for at most 15 minutes. Revoking a session only stops new access tokens.
- A thief who uses a stolen refresh token before the real user does can keep rotating it until
  the user next refreshes. At that point the user's copy is a spent token, the reuse is detected,
  and the session is revoked for both. Without activity from the user, the thief's session ends
  only at the 7-day lifetime.
- The trading API does not check `iss` or `typ`. It relies on the secret alone, which is safe while
  `JWT_SECRET` is used only for access tokens. Adding an issuer check there is a small follow-up.

## Tests

```bash
npm test
```

- `test/jwt-auth.guard.spec.ts`: the guard. Valid token and claims; expired, wrong signature,
  tampered, malformed, missing header, wrong scheme, wrong issuer, `alg: none`, refresh token;
  identical bodies; and no network calls.
- `test/auth.service.spec.ts`: refresh rotation, reuse detection, the access-token claim set,
  expired, tampered or malformed refresh tokens, token-type confusion, and hashed storage.
- `test/auth.e2e.spec.ts`: the same flows over HTTP against the real Nest app.
