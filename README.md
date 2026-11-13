# LedgerLine

LedgerLine is a local money-movement demo built around one core rule: every successful movement changes the account balance and writes matching, immutable-by-API debit/credit ledger entries in the same database transaction. A stable idempotency key prevents a repeated request from posting twice. It is not a bank or a payment processor; it does not connect to payment networks or move real money.

## What is implemented

- React + Vite financial dashboard with registration, sign-in, account management, deposits, withdrawals, transfers, transaction history, ledger proofs, CSV exports, analytics from recorded data, and role-aware audit logs.
- One-time scheduled transfers execute through the local ledger; profile, password, and display preferences are persisted in PostgreSQL.
- Administrator user management supports customer/auditor creation and role updates; it does not expose admin privilege escalation or delete users with historical ledger activity.
- Two-factor authentication is explicitly unavailable and is never displayed as enabled.
- Java 21 / Spring Boot REST API with BCrypt password hashing, signed expiring JWTs, customer/admin/auditor roles, and account ownership checks.
- Optional Redis-backed login rate limiting; it is disabled by default, requires a Redis password when enabled, and can use TLS for remote Redis connections.
- PostgreSQL schema managed by Flyway. Hibernate validates the schema rather than modifying it.
- Double-entry USD ledger, transaction-scoped audit events, non-negative balance constraint, row-level locking in deterministic account-ID order, and idempotent money-movement requests.
- Automated backend integration tests using H2 in PostgreSQL compatibility mode.
- Optional one-time fictional demo account seeding for local walkthroughs.

## Requirements

- Java 21 or newer (the project compiles with Java 21 language/API compatibility)
- Maven 3.9+
- Node.js 20+ and npm
- PostgreSQL 14+

## Run locally on Windows

Create a PostgreSQL database and least-privilege application user. Run these statements as a database administrator, changing the password to a locally managed secret:

```sql
CREATE USER ledgerline_app WITH PASSWORD 'replace-with-a-strong-local-secret';
CREATE DATABASE ledgerline_db OWNER ledgerline_app;
```

In PowerShell, set the database password and start the frontend and backend together:

```powershell
Set-Location .\frontend
$env:DB_PASSWORD = "your-local-database-password"
$env:LEDGER_JWT_SECRET = "replace-with-a-random-secret-at-least-32-characters"
$env:LEDGER_ADMIN_EMAIL = "admin@example.com"
$env:LEDGER_ADMIN_PASSWORD = "use-a-unique-password-of-at-least-12-characters"
npm install
npm run dev:all
```

The launcher starts both services, reuses a backend only when its API and PostgreSQL readiness check passes, and routes frontend `/api` requests to the backend. Open <http://localhost:5173>; the sign-in screen shows backend/database connection status, and the health check is <http://localhost:5173/api/health>. A ready health response includes `"status":"UP"` and `"database":"UP"`; if PostgreSQL is unavailable, the endpoint returns HTTP 503. If the launcher starts a service, keep its terminal open while using the app; Ctrl+C stops only services started by the launcher.

The admin is created once at startup if the email does not exist. To create a separate auditor, set `LEDGER_AUDITOR_EMAIL` and `LEDGER_AUDITOR_PASSWORD` before starting the backend. Do not use the local fallback JWT secret in a deployed environment. Keep secrets in your local environment; do not put them in source files or share them in chat.

### Optional local demo customer

To see backend-populated account balances and transaction history immediately after login, set these optional variables before the first backend start. Use a new email and a private password of at least 12 characters:

```powershell
$env:LEDGER_DEMO_EMAIL = "demo@example.com"
$env:LEDGER_DEMO_PASSWORD = "choose-a-private-password"
npm run dev:all
```

On first startup, LedgerLine creates this customer, two USD accounts, and four clearly labeled demo transactions using the normal double-entry ledger. The sample values are fictional and for local demonstration only. Seeding is disabled unless both variables are configured; it does not add demo activity to existing customer accounts. If the backend is already running, stop it with Ctrl+C before starting it with these variables. The customer is created only once; use a new email for a fresh demo dataset.

To run either service separately instead:

```powershell
# Backend (set DB_PASSWORD in this window first)
Set-Location .\backend\ledgerline
$env:DB_PASSWORD = "your-local-database-password"
mvn spring-boot:run

# Frontend, in another PowerShell window
Set-Location .\frontend
npm run dev
```

The frontend development server proxies `/api` to `http://localhost:8080` by default. To target a different API server, set `VITE_API_URL` before starting Vite.

Environment variables:

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/ledgerline_db` | PostgreSQL JDBC URL |
| `DB_USERNAME` | `ledgerline_app` | Database user |
| `DB_PASSWORD` | unset | Database password (required) |
| `LEDGER_JWT_SECRET` | local-only fallback | JWT signing secret; set a random 32+ character value |
| `LEDGER_JWT_EXPIRATION` | `PT8H` | JWT lifetime |
| `FRONTEND_ORIGIN` | `http://localhost:5173` | Single allowed browser origin |
| `LEDGER_ADMIN_EMAIL`, `LEDGER_ADMIN_PASSWORD` | unset | Optional first-run administrator |
| `LEDGER_AUDITOR_EMAIL`, `LEDGER_AUDITOR_PASSWORD` | unset | Optional first-run read-only auditor |
| `LEDGER_DEMO_EMAIL`, `LEDGER_DEMO_PASSWORD` | unset | Optional one-time local demo customer and sample ledger activity |
| `REDIS_LOGIN_RATE_LIMIT_ENABLED` | `false` | Enable Redis-backed login failure limiting; Redis is not needed for the default local run |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_USERNAME`, `REDIS_PASSWORD` | `localhost`, `6379`, unset, unset | Redis connection settings; password is required when rate limiting is enabled |
| `REDIS_SSL` | `false` | Enable TLS for Redis connections; use TLS for remote Redis deployments |
| `REDIS_LOGIN_MAX_ATTEMPTS`, `REDIS_LOGIN_WINDOW` | `5`, `PT15M` | Login attempts allowed per email/client-address pair and fixed expiry window |
| `PORT` | `8080` | Backend port |

When enabled, set `REDIS_LOGIN_RATE_LIMIT_ENABLED=true` and provide `REDIS_PASSWORD`. The limiter returns HTTP 429 with a `Retry-After` header after the configured number of failed attempts. It uses the direct client address from the servlet request; if the app runs behind a proxy, configure trusted proxy handling at the deployment boundary rather than trusting arbitrary forwarded headers. Redis connection failures are not bypassed, so login remains protected rather than silently failing open.

### Troubleshooting: PostgreSQL password authentication failed

If startup reports `FATAL: password authentication failed for user "ledgerline_app"`, the PostgreSQL server is reachable, but the password in `DB_PASSWORD` does not match that database user's password. Set the correct password in the same PowerShell window used to start Maven:

```powershell
$env:DB_PASSWORD = "the-password-configured-for-ledgerline_app"
mvn spring-boot:run
```

If you no longer know that password, connect to the local PostgreSQL server as a PostgreSQL administrator and reset the application user's password:

```sql
ALTER ROLE ledgerline_app WITH PASSWORD 'choose-a-new-local-secret';
```

Then set `DB_PASSWORD` to that exact new value in the backend PowerShell window and start the application again. Do not put database passwords in source files or share them in chat.

## API outline

All API paths are prefixed with `/api`.

| Method | Path | Access |
|---|---|---|
| `GET` | `/health` | Public |
| `POST` | `/auth/register`, `/auth/login` | Public |
| `GET`, `POST` | `/accounts` | Authenticated; customer account creation |
| `POST` | `/admin/accounts` | Administrator |
| `POST` | `/accounts/{id}/deposit` | Customer owning the account |
| `POST` | `/accounts/{id}/withdraw` | Customer owning the account |
| `POST` | `/accounts/{id}/transfer` | Customer owning the source account |
| `GET` | `/accounts/{id}/transactions`, `/transactions` | Authenticated; customer results are scoped to owned accounts |
| `GET` | `/ledger` | Authenticated; account ledger entries scoped to customer-owned accounts |
| `GET` | `/transactions/{reference}/proof` | Authenticated; returns the matching debit/credit entries and totals |
| `GET` | `/audit` | Administrator or auditor |
| `GET`, `PUT` | `/profile`, `/profile/preferences` | Current authenticated user |
| `POST` | `/profile/password` | Current authenticated user; verifies the current password |
| `GET`, `POST`, `PATCH` | `/admin/users` | Administrator; create and manage customer/auditor users |
| `GET`, `POST` | `/scheduled-transfers` | Customer; one-time local ledger transfers |
| `POST` | `/scheduled-transfers/{id}/cancel` | Customer; cancel an owned pending transfer |

Money-movement requests require an `Idempotency-Key` header. Reusing the key with the same request returns the original transaction; reusing it for different request data returns `409 Conflict`. Amounts must be positive, have at most two decimal places, and use USD. Registration creates an empty USD account.

## Verify

From `backend\ledgerline`:

```powershell
mvn test
```

From `frontend`:

```powershell
npm run build
```

## Deploy a demo

The repository includes a Render Blueprint for the Spring Boot API and PostgreSQL database, plus Vercel settings for the Vite frontend.

1. Push this repository to GitHub.
2. In Render, create a Blueprint from this repository and deploy `render.yaml`. This creates the API and database; Render generates the JWT secret.
3. In Vercel, import the same repository and set the project root directory to `frontend`. Deploy with the Vercel configuration in that folder.
4. In the Vercel project settings, add `VITE_API_URL` with the API URL from Render followed by `/api` (for example, `https://ledgerline-api.onrender.com/api`), then redeploy.
5. In the Render API environment settings, set `FRONTEND_ORIGIN` to the exact Vercel site origin (for example, `https://your-project.vercel.app`) and redeploy the API.

Render's free web service can sleep when idle, and its free PostgreSQL database expires after 30 days. These free resources are for previewing only, not production or durable data. LedgerLine is a fictional local/demo ledger, not a bank or payment processor; do not enter real financial or personal information or use it to move real money.

## Important scope

This is a local/demo application, not a regulated banking or payment processor. Deposits and withdrawals use a balanced `EXTERNAL:USD:CLEARING` ledger account and do not connect to a real cash rail. For production, add external payment-provider settlement, operational key rotation, refresh/session revocation, rate limiting, database backup/recovery, reconciliation operations, monitoring, and an independent security review. The browser keeps the short-lived access token in tab-scoped session storage; production deployments should evaluate an HttpOnly-cookie session and a hardened browser security policy.


## 🎥 Project Demo Video

Watch the complete LedgerLine Full Stack Web Application demo:
[▶ Watch LedgerLine Demo on YouTube](https://youtu.be/I9q7ZIE-pao)
