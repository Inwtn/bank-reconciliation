# Bank Reconciliation Engine

[![Java CI](https://github.com/Inwtn/bank-reconciliation/actions/workflows/ci.yml/badge.svg)](https://github.com/Inwtn/bank-reconciliation/actions/workflows/ci.yml)

A Java 17 and Spring Boot application that reconciles OFX and CSV bank statements with internal financial entries. It separates matched bank transactions, unmatched bank transactions, and internal entries that are still pending. The browser dashboard calls the real authenticated API.

## Why it exists

Finance teams often compare bank statements with accounts payable and receivable by hand. The engine checks amount, transaction type, and date, then returns a report and keeps an import history. A failed import remains visible without leaving partial statement rows in the database.

## Features

- OFX and CSV imports with exact amount and debit/credit matching
- Exact-date match first, then a ±1-day fallback to the closest date
- Matched, unmatched, and pending records with totals and a match percentage
- PostgreSQL persistence and import history, including failed imports
- HTTP Basic accounts: `admin` can read and write; `viewer` can only read
- Browser dashboard for entries, imports, results, and history
- Public health check and admin-only Prometheus metrics
- H2 unit and security tests, PostgreSQL integration tests with Testcontainers, dashboard client tests, and GitHub Actions CI

## Run locally

You need Docker and Docker Compose. Copy `.env.example` to `.env` and set three different passwords. `.env` is ignored by Git.

```bash
docker compose up --build
```

Open the dashboard at **http://localhost:3000**. The API is at **http://localhost:8080/api** and Swagger UI is at **http://localhost:8080/api/swagger-ui**. The dashboard is served on port 3000 so the API's CORS rules permit it; opening `dashboard.html` directly as a `file://` page does not provide the same origin.

To try the complete flow:

1. Connect as `admin` with the password from `.env`.
2. Click **Create sample entries**. This writes three example records to PostgreSQL.
3. Download and import **sample.csv** from the dashboard.
4. Inspect the matched, unmatched, and pending tabs, then the import history.
5. Connect as `viewer` to inspect the read-only experience.

The sample data uses a fixed date, **2026-10-29**, so its matching behavior is reproducible. Creating sample entries repeatedly adds more rows; use a fresh database volume for a clean walkthrough.

For a backend-only development run, start PostgreSQL, set `DB_URL`, `DB_USER`, `DB_PASSWORD`, `APP_ADMIN_PASSWORD`, and `APP_VIEWER_PASSWORD`, then run `mvn spring-boot:run` from `backend/`.

## API

The existing API paths and JSON field names are kept for compatibility. They are documented here in English.

| Method | Path | Purpose | Access |
| --- | --- | --- | --- |
| `GET` | `/api/actuator/health` | Health check | Public |
| `GET` | `/api/actuator/prometheus` | Import metrics | Admin |
| `GET` | `/api/lancamentos` | List entries; optional `status=PENDENTE` | Admin, viewer |
| `POST` | `/api/lancamentos` | Create an entry | Admin |
| `DELETE` | `/api/lancamentos/{id}` | Delete an entry | Admin |
| `GET` | `/api/lancamentos/dashboard/stats` | Entry statistics | Admin, viewer |
| `POST` | `/api/conciliacao/importar` | Upload and reconcile OFX/CSV, multipart field `arquivo` | Admin |
| `GET` | `/api/conciliacao/historico` | Import history | Admin, viewer |
| `GET` | `/api/conciliacao/historico/{id}` | One import record | Admin, viewer |

Example:

```bash
curl -u viewer:YOUR_VIEWER_PASSWORD http://localhost:8080/api/lancamentos
curl -u admin:YOUR_ADMIN_PASSWORD http://localhost:8080/api/actuator/prometheus
```

The dashboard stores credentials only in the current tab's memory and clears the password input after connecting. HTTP Basic must use HTTPS outside localhost. Set passwords using your deployment platform's secret manager and do not expose PostgreSQL publicly.

## Statement format

The sample file uses English headers and transaction types:

```csv
Date,Description,Amount,Type
2026-10-29,Supplier payment ABC,-1500.00,DEBIT
2026-10-29,Customer payment XYZ,3200.00,CREDIT
```

The parser also accepts Portuguese header aliases, `DEBITO` and `CREDITO`, and signed amounts. Dates may use `yyyy-MM-dd`, `dd/MM/yyyy`, `dd-MM-yyyy`, or `MM/dd/yyyy`. For OFX, it reads `STMTTRN` records with amount, date, and transaction identifiers.

## Architecture

`backend/` contains the Spring API, parsers, reconciliation service, repositories, and tests. `frontend/` contains the static dashboard and a small API client. Docker Compose runs PostgreSQL, the API, and Nginx for the dashboard. The API never stores user credentials in the frontend; authentication is checked server-side for each request.

The reconciliation service records the start of an import in a separate transaction, runs matching in one transaction, and marks the import as completed or failed in another. This keeps failure history while rolling back partial matching work.

## Tests

```bash
mvn --batch-mode -f backend/pom.xml verify
npm test --prefix frontend
```

The PostgreSQL Testcontainers tests require Docker. GitHub Actions runs both commands on every pull request and on pushes to `main`.

## License

MIT. See [LICENSE](LICENSE).
