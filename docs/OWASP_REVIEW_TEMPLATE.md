# OWASP Top 10 Security Review — Template

Copy this file to `OWASP_REVIEW.md` and fill it in. Do not edit the template itself.

The review records, for every OWASP Top 10 (2021) category, what was checked, what was found,
and what the team decided to do about it. Anyone reading it later should be able to follow each
finding to the code, test or config it is based on.

## Review details

| | |
|---|---|
| Platform reviewed | _components in scope, e.g. trading API, auth service, ETL, Kafka, Postgres, CI_ |
| Commit reviewed | _`git rev-parse --short HEAD` at the time of the review_ |
| Review date | _YYYY-MM-DD_ |
| Reviewers | _names_ |
| Standard | [OWASP Top 10 (2021)](https://owasp.org/Top10/) |

## Rules

1. **Every category has at least one finding.** No category is left blank or skipped.
2. **Every finding has evidence.** Evidence is a file, test, config or scan report someone else
   can open, written as a repo-relative path (for example `shared/auth-service/src/auth/token.service.ts`
   or `Jenkinsfile` stage `Dependency Scan`). "Reviewed the code" is not evidence.
3. **A finding of "None" names what was checked.** For example: "None. Checked every SQL
   MyBatis annotation in `src/main/java/com/neueda/leap/mappers/` for `${}` substitution;
   all use `#{}` binding."
4. **Every disposition comes from the vocabulary below**, written exactly as shown.
5. **An Accepted finding states its residual risk and why the team carries it**: what can
   still go wrong, how bad it is, and why fixing it now is not worth the cost.
6. **The focus categories go deepest.** This sprint's focus is A01, A02 and A07. Each needs
   a finding for every check listed under it.

## Disposition vocabulary

| Disposition | Use when | The finding must also give |
|---|---|---|
| **Fixed** | A weakness was found and the fix is merged. | The fix, and the test or scan that proves it. |
| **Accepted** | A weakness exists and the team chooses to carry it. | The residual risk and why the team carries it. |
| **Open** | A weakness exists and a fix is planned but not merged. | The ticket or issue that tracks the fix. |
| **No action** | The category was checked and nothing was found. | What was checked (rule 3). |
| **Not applicable** | The category cannot occur on this platform. | Why it cannot occur. |

## Summary

One row per category. If a category has several findings, list each disposition once.

| Category | Dispositions | Findings |
|---|---|---|
| A01 Broken Access Control | | |
| A02 Cryptographic Failures | | |
| A03 Injection | | |
| A04 Insecure Design | | |
| A05 Security Misconfiguration | | |
| A06 Vulnerable and Outdated Components | | |
| A07 Identification and Authentication Failures | | |
| A08 Software and Data Integrity Failures | | |
| A09 Security Logging and Monitoring Failures | | |
| A10 Server-Side Request Forgery | | |

## How to write a finding

Number findings by category (`A01-1`, `A01-2`, …) and use this table in every category:

| ID | Finding | Evidence | Disposition | Residual risk and rationale |
|---|---|---|---|---|
| A0X-1 | _what was checked and what was found_ | _file, test, config or report_ | _from the vocabulary_ | _required for Accepted; otherwise "—"_ |

---

## A01 Broken Access Control — focus

Check:

- Every trading API route requires a valid token unless it is deliberately public.
- A `USER` can read and change only their own account, orders and positions (ownership is
  resolved from the token's `sub`, never from a request parameter).
- `ADMIN`-only operations (opening accounts, the DLQ) reject a `USER` token.
- Auth service routes are protected unless marked `@Public()`.
- CORS allows only the origins the platform needs.

| ID | Finding | Evidence | Disposition | Residual risk and rationale |
|---|---|---|---|---|
| A01-1 | | | | |

## A02 Cryptographic Failures — focus

Check:

- Password hashing algorithm and parameters.
- How refresh tokens are stored at rest.
- JWT signing algorithm, whether it is pinned, and the strength of the secret.
- Where secrets come from (environment, `.env`, committed config) and whether any default or
  example secret could reach a running system.
- Whether traffic between services and to clients is encrypted.

| ID | Finding | Evidence | Disposition | Residual risk and rationale |
|---|---|---|---|---|
| A02-1 | | | | |

## A03 Injection

Check: SQL in the trading API mappers, the auth service and the ETL; input validation on
request DTOs.

| ID | Finding | Evidence | Disposition | Residual risk and rationale |
|---|---|---|---|---|
| A03-1 | | | | |

## A04 Insecure Design

Check: idempotency of order placement, business-rule limits (funds, holdings), and how failed
Kafka messages are handled.

| ID | Finding | Evidence | Disposition | Residual risk and rationale |
|---|---|---|---|---|
| A04-1 | | | | |

## A05 Security Misconfiguration

Check: security headers, error responses (no stack traces), default credentials, exposed ports
and admin endpoints in `docker-compose.yml`, and container users.

| ID | Finding | Evidence | Disposition | Residual risk and rationale |
|---|---|---|---|---|
| A05-1 | | | | |

## A06 Vulnerable and Outdated Components

Check: dependency scanning in CI (Trivy), the latest scan results, and how HIGH findings that do
not fail the build are triaged.

| ID | Finding | Evidence | Disposition | Residual risk and rationale |
|---|---|---|---|---|
| A06-1 | | | | |

## A07 Identification and Authentication Failures — focus

Check:

- Login errors do not reveal whether a username exists.
- Brute-force and credential-stuffing protection on `/auth/login` (rate limiting, lockout).
- Password rules on registration.
- Access and refresh token lifetimes.
- Refresh token rotation, reuse detection and revocation, **including the refresh revocation
  decision from story 6 and its residual risks**.
- Logout, and what happens to access tokens already issued.
- Token type confusion (a refresh token used as an access token, and the reverse).

| ID | Finding | Evidence | Disposition | Residual risk and rationale |
|---|---|---|---|---|
| A07-1 | | | | |

## A08 Software and Data Integrity Failures

Check: integrity of Kafka messages between services, pinned CI tool and base images, and
lockfiles committed for every build.

| ID | Finding | Evidence | Disposition | Residual risk and rationale |
|---|---|---|---|---|
| A08-1 | | | | |

## A09 Security Logging and Monitoring Failures

Check: which auth events are logged (success, failure, token reuse), that logs never contain
passwords or tokens, and whether anything alerts on suspicious activity.

| ID | Finding | Evidence | Disposition | Residual risk and rationale |
|---|---|---|---|---|
| A09-1 | | | | |

## A10 Server-Side Request Forgery

Check: whether any component fetches a URL that a user can influence.

| ID | Finding | Evidence | Disposition | Residual risk and rationale |
|---|---|---|---|---|
| A10-1 | | | | |
