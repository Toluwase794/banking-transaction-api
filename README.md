# Banking Transaction API

[![Build and test](https://github.com/Toluwase794/banking-transaction-api/actions/workflows/build.yml/badge.svg)](https://github.com/Toluwase794/banking-transaction-api/actions/workflows/build.yml)

A Java 21 / Spring Boot backend for simulated banking. Create accounts, deposit and withdraw funds, make atomic transfers, and inspect persisted transaction history. PostgreSQL stores the data; Flyway owns the schema. Swagger provides an interactive API explorer.

This is a portfolio simulation using simulated funds. It has no authentication or authorization and binds to localhost by default. It is intended for local development; production banking requires substantially more controls.

## Demo and verification evidence

The initial local verification passed **42 tests**: 21 with H2 and the same 21 with PostgreSQL 16, with no failures or skips. A Swagger walkthrough verified a deposit, a transfer and its replay, an overdraft rejection, final balances, and transaction history. See the [demo walkthrough and results](docs/demo.md).

![Swagger showing one transfer and one deposit in transaction history](docs/images/swagger-review.png)

## Run locally

Install a **JDK 21** and Docker Desktop with the Linux engine running. Set `JAVA_HOME` to your JDK directory. Maven is downloaded by the included wrapper on first use.

On Windows, `scripts/start.ps1` starts the database, reads datasource settings from `.env`, and runs the API. It accepts `-JdkHome` or uses `JAVA_HOME`; if neither is set, it checks the optional ignored `.tools/jdk` directory. Fresh clones need their own JDK.

```powershell
.\scripts\start.ps1
```

For manual setup:

PowerShell, from this directory:

```powershell
Copy-Item .env.example .env
# Local development password; use the same value as POSTGRES_PASSWORD in .env.
$env:DB_PASSWORD = 'local-development-only'
docker compose up -d --wait
.\mvnw.cmd spring-boot:run
```

Compose reads `.env` for PostgreSQL configuration. Spring reads process environment variables, so set `DB_PASSWORD` separately as above. If you change database settings, also set `DB_URL` and `DB_USERNAME` to match. The example password is for a local disposable database only.

On macOS/Linux, use `export DB_PASSWORD=local-development-only` and `sh ./mvnw spring-boot:run` after copying `.env.example` to `.env` and starting Compose.

- Swagger UI: <http://localhost:8080/swagger-ui/index.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>
- Stop the database: `docker compose stop` (data stays in the named volume).

## Try a complete flow

With the API running, execute:

```powershell
.\scripts\demo.ps1
```

The script creates two accounts, deposits 500 into the sender, transfers 150 twice with the same key, and verifies final balances of 350 and 150. Each run creates new accounts and uses a new key.

Or create an account manually:

```powershell
$account = Invoke-RestMethod http://localhost:8080/api/accounts -Method Post -ContentType application/json -Body '{"ownerName":"Ada Lovelace"}'
Invoke-RestMethod "http://localhost:8080/api/accounts/$($account.id)/deposits" -Method Post -ContentType application/json -Body '{"amount":500.00}'
Invoke-RestMethod "http://localhost:8080/api/accounts/$($account.id)/transactions"
```

## Endpoints

| Method | Path | Request / result |
| --- | --- | --- |
| POST | `/api/accounts` | `{"ownerName":"Ada Lovelace"}` → 201, zero balance, Location header |
| GET | `/api/accounts/{id}` | Account and current balance |
| GET | `/api/accounts?page=0&size=20` | Page of accounts |
| POST | `/api/accounts/{id}/deposits` | `{"amount":200.00}` → transaction |
| POST | `/api/accounts/{id}/withdrawals` | `{"amount":100.00}` → transaction |
| POST | `/api/transfers` | Transfer request below → original or new transaction |
| GET | `/api/accounts/{id}/transactions?page=0&size=20` | History, newest first |

```json
{
  "fromAccountId": "replace-with-sender-uuid",
  "toAccountId": "replace-with-recipient-uuid",
  "amount": 150.00,
  "idempotencyKey": "transfer-001"
}
```

Amounts must be positive with at most two fractional digits and 17 integer digits. All accounts use one implicit simulated currency. Accounts start at zero; initial funds must be deposited so history accounts for the opening funds. Maximum balance is `99999999999999999.99`.

Transfer keys are required, globally unique, and 1–128 characters using letters, numbers, `.`, `_`, `:`, or `-`. Repeating a key and the same source, destination, and numeric amount returns the original transaction with 200. Reusing it with different details returns 409. Keys persist with history. Deposits and withdrawals do not support idempotency; retrying them can create another operation.

Lists return `content`, `page`, `size`, `totalElements`, and `totalPages`. Pages start at zero and size is 1–100. Account order is creation time then ID; history order is descending creation time then ID. Offset pages may shift while new transactions arrive.

Errors use `application/problem+json`: 400 for invalid input, 404 for missing accounts, 409 for insufficient funds, balance overflow, key conflicts, or lock contention. Lock contention can be retried; transfer retries must keep their key. JSON field names are strict, including rejection of unknown fields.

## Architecture and consistency

`REST controller → service → JPA repositories → PostgreSQL`

- DTOs define the API contract; persistence entities stay internal.
- Services use `BigDecimal` and reject fractional cents instead of rounding.
- Deposits and withdrawals lock the account with `PESSIMISTIC_WRITE`.
- Transfers lock both accounts in UUID order to avoid opposing lock orders. Balances and the history row commit in one `@Transactional` operation.
- Idempotency is checked again after lock acquisition. A database unique constraint handles concurrent key reuse across disjoint account pairs. The facade handles a constraint collision only after the losing transaction has rolled back, then reads the winner in a fresh transaction.
- Database checks enforce nonnegative balances, positive amounts, valid transaction shapes, and unique account numbers/transfer keys.
- Foreign keys preserve transaction references. The API has no deletion or editing endpoints for history.
- PostgreSQL lock waits are limited to five seconds. There is no automatic retry of money operations.
- Migrations are in `src/main/resources/db/migration`; Hibernate validates rather than creates the schema.

## Verification

```powershell
.\mvnw.cmd verify
```

The default API suite uses an isolated H2 database in PostgreSQL compatibility mode and runs the actual migrations. The same suite also runs against PostgreSQL 16 through Testcontainers when Docker is available. PostgreSQL tests are **reported as skipped** when no Docker engine is accessible; an H2 pass alone does not establish PostgreSQL behavior.

Tests cover account creation/list/get, deposits and withdrawals, validation, missing accounts, paginated history, insufficient funds, balance overflow, same-account transfers, sequential and concurrent duplicate keys, conflicting keys across disjoint account pairs, opposite-direction transfers, concurrent withdrawals, OpenAPI availability, and rollback after both updated balances have been flushed to SQL but history storage fails.

Run just the PostgreSQL suite with Docker running:

```powershell
.\mvnw.cmd '-Dtest=PostgresBankingApiTest' test
```

The [GitHub Actions workflow](https://github.com/Toluwase794/banking-transaction-api/actions/workflows/build.yml) builds and runs both suites on an Ubuntu runner with Docker and uploads test reports. CI fails if PostgreSQL tests are skipped. The API runs locally; no public API deployment is included.

To package: `.\mvnw.cmd package`. To run the jar, set database environment variables then use `java -jar target/banking-api-0.0.1-SNAPSHOT.jar`.
