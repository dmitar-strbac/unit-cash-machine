<div align="center">

# 🏧 Unit Cash Machine

### Cash deposit machine simulator

A local cash deposit machine simulator built with Spring Boot and Angular,
featuring a touchscreen-inspired interface, simulated hardware and live diagnostics.

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.2-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)
![Angular](https://img.shields.io/badge/Angular-21-DD0031?style=for-the-badge&logo=angular&logoColor=white)

**Simulated hardware · Explicit domain rules · Testable fault scenarios**

[Overview](#-overview) ·
[Architecture](#architecture) ·
[Quick start](#-quick-start) ·
[API demo](#-api-demo) ·
[Roadmap](#roadmap)

</div>

---

## 🎯 Overview

A cash deposit machine needs to do more than count banknotes. It must know whether its door is secured, its cashbox is available and its hardware is operational—and explain what went wrong when it cannot proceed.

**Unit Cash Machine** is a local simulator inspired by observed workflows on a physical cash deposit machine. The current backend models six hardware components and exposes a consistent view of machine readiness and diagnostics.

The project is developed incrementally. Hardware simulation, diagnostics,
PIN-based authentication and EUR deposits are implemented. Cash collection,
reporting, maintenance commands and extended recovery scenarios are planned.

## ✨ Features

| Area | Description | Status |
| :--- | :--- | :--- |
| 🖥️ Machine interface | Touchscreen-inspired navigation, machine information and live diagnostics | Available |
| 🔌 Hardware simulation | Six modeled components, readiness rules and API-based fault injection | Available |
| 🚦 Diagnostics | Component alerts, severity indicators and connection-loss feedback | Available |
| 🔐 Authentication | Profile selection, PIN login and role-based access | Available |
| 💶 Deposits | EUR banknote acceptance and persistent deposit records | Available |
| 🚚 Cash collection | CIT workflow and cashbox replacement | Planned |
| 🧾 Reporting | Daily, per-user and device totals with simulated printing | Planned |
| 🛠️ Maintenance | Diagnostics commands and simulated device lifecycle operations | Planned |

### Six components, one consistent snapshot

| Component | Modeled states |
| :--- | :--- |
| Banknote acceptor | `READY` · `ACCEPTING` · `JAMMED` · `OFFLINE` · `UNKNOWN` |
| Cashbox | `PRESENT` · `FULL` · `ABSENT` · `UNKNOWN` |
| Door | `CLOSED` · `OPEN` · `UNKNOWN` |
| Electronic lock | `LOCKED` · `UNLOCKED` · `FAULT` · `UNKNOWN` |
| Extension board | `ONLINE` · `OFFLINE` · `UNKNOWN` |
| Printer | `READY` · `OUT_OF_PAPER` · `OFFLINE` · `FAULT` · `UNKNOWN` |

## 🧠 Engineering decisions

- **Hardware behind an interface.** The application reads hardware through `HardwareStatusPort`, keeping status retrieval separate from the simulator implementation.
- **One snapshot per status request.** Readiness and diagnostics use the same immutable hardware snapshot.
- **Domain rules outside controllers.** Readiness and alert policies can be tested without starting Spring or connecting to a database.
- **Atomic state replacement.** The simulator replaces the complete snapshot without exposing a partially updated state.
- **Versioned database schema.** Flyway applies migrations; Hibernate validates the resulting schema.
- **Behavior-focused tests.** Tests cover readiness rules, authentication, deposit validation, duplicate requests and selected failure and recovery paths. Extended concurrency and interruption testing is planned.
- **Role-specific simulation controls.** Hardware changes require the `simulation` profile, `MAINTENANCE` access and CSRF protection. Simulated banknote insertion requires the `simulation` profile, `CASHIER` access and CSRF protection.
- **Immediate banknote persistence.** Each accepted banknote is recorded in its own request transaction; logout completes the deposit without being responsible for saving previously accepted money.
- **Duplicate-request protection.** Each banknote operation has a unique identifier. Repeating the same operation returns its recorded outcome without counting it again; reusing the identifier with different contents is rejected.
- **One active deposit per machine.** Transactional row locking and a database constraint prevent multiple active deposits.
- **Server-authoritative totals.** Deposit totals are calculated from accepted banknote records and displayed by the frontend.
- **Unconfirmed-operation handling.** The frontend preserves a pending operation in session storage and retries it with the same identifier after an uncertain response.

<a id="architecture"></a>
## 🏗️ Architecture

The Spring Boot backend is organized by feature, with separate API,
application, domain and infrastructure responsibilities where needed.
The Angular frontend provides the machine interface.

```mermaid
flowchart TD
    UI["Angular machine interface"]
    API["Spring Boot REST API"]
    Domain["Application services and domain rules"]
    Database[("PostgreSQL")]
    Hardware["Hardware ports"]
    Simulator["In-memory hardware simulator"]
    Controls["Simulation API client"]

    UI --> API
    API --> Domain
    Domain --> Database
    Domain --> Hardware
    Hardware --> Simulator
    Controls -->|"simulation profile"| API
```

Persistent application data is stored in PostgreSQL, with schema changes
managed by Flyway. Simulated hardware state is held in memory and resets
when the backend restarts.

User accounts and machine configuration use Spring Data JPA. Deposit records
and banknote operations use a dedicated JDBC repository with explicit SQL
and transactional locking.

Authenticated HTTP sessions are not restored after a backend restart.
Deposit records remain in PostgreSQL. When the next cashier deposit starts,
the application closes stale active deposits as `EXPIRED` or `INTERRUPTED`.
This reconciliation runs on the next cashier login, not as a background task.

## 🚦 Readiness and diagnostics

<details>
<summary><strong>🔎 Hardware states and readiness rules</strong></summary>

| Question | Rule |
| :--- | :--- |
| **Is the machine secure?** | Door is closed and lock is locked |
| **Is deposit hardware ready?** | Machine is secure, acceptor is ready, cashbox is present and extension board is online |
| **Is printing available?** | Printer is ready |

</details>

Indicator color summarizes the current alerts:

```mermaid
flowchart TD
    Snapshot["Current hardware snapshot"] --> Evaluate["Evaluate component alerts"]
    Evaluate --> HasAlerts{"Any alerts?"}
    HasAlerts -->|No| Green["🟢 GREEN"]
    HasAlerts -->|Yes| HasError{"Any ERROR?"}
    HasError -->|No| Amber["🟠 AMBER"]
    HasError -->|Yes| Red["🔴 RED"]
```

**Color and readiness serve different purposes.**

- An offline printer produces an amber warning and disables printing, while deposits remain available.
- An open door produces an amber warning and blocks deposits.
- A jammed acceptor produces a red error and blocks deposits.
- An acceptor in `ACCEPTING` is busy, not faulty: it produces no alert but is not ready for a new deposit.

## ⚙️ Technology stack

| Area | Technologies |
| :--- | :--- |
| Backend | Java 21, Spring Boot 4.1.2, Spring MVC |
| Frontend | Angular 21, TypeScript, SCSS, standalone components |
| Validation | Jakarta Bean Validation, Problem Details responses |
| Persistence | Spring Data JPA, Hibernate, PostgreSQL 17 |
| Migrations | Flyway |
| Security | Spring Security, BCrypt PIN hashing, server-side sessions, CSRF protection |
| Persistence | Spring Data JPA, Hibernate, Spring JDBC (`JdbcTemplate`), PostgreSQL 17 |
| Testing | JUnit, Mockito, Spring Boot Test, MockMvc |
| Frontend testing | Vitest, Angular TestBed |
| Formatting | Spotless, google-java-format with AOSP style, Prettier |
| Coverage | JaCoCo |
| Build | Maven Wrapper, Angular CLI |
| Local database | Docker Compose |

## 🚀 Quick start

### 1. Prerequisites

- **JDK 21**
- **Node.js 22.19.0**
- **npm 11.6.2**
- **Docker Desktop with Compose** for the local database setup
- **Git**

Maven is supplied through the wrapper, and Angular CLI is installed with the frontend dependencies. Node.js is only required for the frontend.

### 2. Start the database

From the repository root, create a local `.env` file containing:

```dotenv
DB_PASSWORD=replace-with-your-local-password
```

Start PostgreSQL using the repository's Compose configuration:

```powershell
docker compose up -d --wait
docker compose ps
```

The default backend connection expects:

| Setting | Value |
| :--- | :--- |
| Host | `localhost` |
| Port | `5434` |
| Database | `unit_cash_machine` |
| Username | `unit_app` |
| Schema | `cash_machine`, created by Flyway |

### 3. Configure the backend

In PowerShell:

```powershell
$env:DB_PASSWORD = 'replace-with-your-local-password'
Set-Location backend
```

Use the same password as the database role.

Docker Compose reads the root `.env` file; Spring Boot does not automatically load it. When running through IntelliJ IDEA, configure `DB_PASSWORD` in the application's Run Configuration.

<details>
<summary><strong>Optional database connection overrides</strong></summary>

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5434/unit_cash_machine'
$env:DB_USERNAME = 'unit_app'
```

Use these variables when connecting to a different local database. The database role must have permission to apply the Flyway migrations.

</details>

### 4. Run with simulation controls

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=simulation,demo"
```

The `simulation` profile enables hardware state changes and simulated
banknote insertion through the API.
The `demo` profile creates missing demo accounts for local testing.

| Role | Account | PIN |
| :--- | :--- | :--- |
| Cashier | Demo Cashier | `1111` |
| Management | Demo Manager | `2222` |
| Maintenance | Demo Maintenance | `3333` |
| CIT | PIN-only login | `4444` |

Demo accounts remain in the database after the `demo` profile is disabled.
Existing accounts are not overwritten.

Sessions expire 15 minutes after login. Refreshing the page preserves a valid session.

The backend listens on **`http://127.0.0.1:8080`**.

To run without hardware mutation and banknote insertion simulation endpoints:

```powershell
.\mvnw.cmd spring-boot:run
```

Both modes use simulated hardware. The profile controls whether its state can be changed through the API.

### 5. Check application health

```powershell
Invoke-RestMethod 'http://localhost:8080/actuator/health'
```

### 6. Run the frontend

Keep the backend running. Open another terminal in the repository root:

```powershell
cd frontend
npx --yes npm@11.6.2 ci
npm start
```

Open **http://127.0.0.1:4200**.

The Angular development server proxies `/api/**` requests to
`http://127.0.0.1:8080`. No database credentials are stored in the frontend.

## 🔌 API demo

| Method | Endpoint | Purpose |
| :--- | :--- | :--- |
| `GET` | `/actuator/health` | Application health |
| `GET` | `/api/machine/status` | Configuration, hardware, readiness and alerts |
| `GET` | `/api/users?role=CASHIER` | Active profiles for the selected role; CIT profiles are not listed |
| `GET` | `/api/auth/csrf` | CSRF token for the current session |
| `POST` | `/api/auth/login` | Profile and PIN authentication |
| `POST` | `/api/auth/cit-login` | PIN-only CIT authentication |
| `GET` | `/api/auth/me` | Current authenticated user |
| `PUT` | `/api/simulation/hardware` | Replace hardware state; requires `simulation`, `MAINTENANCE` access and CSRF |
| `POST` | `/api/auth/logout` | Complete the cashier deposit, if applicable, and end the current session |
| `GET` | `/api/deposits/current` | Current deposit and acceptance availability; requires `CASHIER` access |
| `POST` | `/api/deposits/current/banknotes` | Simulate one banknote; requires `simulation`, `CASHIER` access and CSRF |

### Banknote request

After cashier login, obtain a fresh CSRF token and send the request using
the authenticated session:

```http
POST /api/deposits/current/banknotes
Content-Type: application/json
X-CSRF-TOKEN: <token>
```

```json
{
  "operationId": "f4833abb-9de5-48e6-b956-b906139a635b",
  "currency": "EUR",
  "denomination": 20
}
```

Use a new `operationId` for each new banknote. Retry an uncertain operation
with its original identifier and unchanged contents.

Possible outcomes are `ACCEPTED`, `CURRENCY_REJECTED` and
`DENOMINATION_REJECTED`. The response includes the current deposit;
`totalCents` is expressed in cents.

<details>
<summary><strong>🧪 Try a fault scenario: printer offline</strong></summary>

Send a complete hardware snapshot:

```powershell
$baseUrl = 'http://127.0.0.1:8080'

$csrf = Invoke-RestMethod `
    -Uri "$baseUrl/api/auth/csrf" `
    -SessionVariable unitSession

$loginBody = @{
    userId = '00000000-0000-0000-0000-000000000003'
    role   = 'MAINTENANCE'
    pin    = '3333'
} | ConvertTo-Json

Invoke-RestMethod `
    -Uri "$baseUrl/api/auth/login" `
    -Method Post `
    -WebSession $unitSession `
    -Headers @{ $csrf.headerName = $csrf.token } `
    -ContentType 'application/json' `
    -Body $loginBody

# Login invalidates the previous CSRF token.
$csrf = Invoke-RestMethod `
    -Uri "$baseUrl/api/auth/csrf" `
    -WebSession $unitSession

$hardwareState = @{
    acceptor       = 'READY'
    cashbox        = 'PRESENT'
    door           = 'CLOSED'
    lock           = 'LOCKED'
    extensionBoard = 'ONLINE'
    printer        = 'OFFLINE'
} | ConvertTo-Json

Invoke-RestMethod `
    -Uri "$baseUrl/api/simulation/hardware" `
    -Method Put `
    -WebSession $unitSession `
    -Headers @{ $csrf.headerName = $csrf.token } `
    -ContentType 'application/json' `
    -Body $hardwareState
```

Read the resulting machine status:

```powershell
Invoke-RestMethod "$baseUrl/api/machine/status" |
    ConvertTo-Json -Depth 6
```

Relevant response fields:

```json
{
  "secure": true,
  "depositHardwareReady": true,
  "printingAvailable": false,
  "indicator": "AMBER",
  "alertCount": 1,
  "alerts": [
    {
      "code": "PRINTER_OFFLINE",
      "source": "PRINTER",
      "severity": "WARNING",
      "message": "Not operational",
      "blocksDeposit": false
    }
  ]
}
```

**Result:** the machine reports the printer problem without unnecessarily disabling deposits.

Repeat the request with `printer = 'READY'` to restore printing and clear the warning.

- All six component fields are required.
- Missing fields, invalid enum values and malformed JSON are rejected.
- Invalid requests leave the hardware state unchanged.
- Repeating the same valid request preserves the same state.
- Without the `simulation` profile, the mutation endpoint is unavailable.
- Unusual combinations of component states are allowed for fault testing.

The simulation API models snapshots, not physical transitions or a complete cash collection workflow.

</details>

## 🧪 Tests and code quality

From `backend`, with PostgreSQL running and `DB_PASSWORD` configured:

```powershell
# Apply consistent Java formatting
.\mvnw.cmd spotless:apply

# Check formatting, run tests and generate coverage
.\mvnw.cmd clean verify

# Open the JaCoCo report
Start-Process .\target\site\jacoco\index.html
```

From `frontend`:

```powershell
npm run format:check
npm run build
npm test -- --watch=false
```

To apply formatting:

```powershell
npm run format
```

### What the tests exercise

Tests cover domain rules, application behavior, API validation and frontend
status feedback. New functionality is developed alongside tests for its
expected behavior and failure scenarios.

Backend tests cover hardware readiness, diagnostics, authentication,
session validity, deposit ownership, banknote acceptance and rejection,
duplicate operations, logout completion and stale-deposit reconciliation.

Frontend tests cover authentication, status feedback, CSRF-protected
banknote requests, preservation and retry of unconfirmed operations,
currency rejection and protection against stale responses overwriting
newer totals.

Extended tests using concurrent transactions, database failures and
interrupted sessions remain planned.

Backend coverage reports are generated with JaCoCo.

JaCoCo excludes only the application bootstrap class from its report. A coverage threshold is not yet enforced by the build.

<a id="roadmap"></a>
## 🗺️ Roadmap

- [x] Configure PostgreSQL, Flyway and application persistence
- [x] Implement hardware simulation, readiness rules and diagnostics
- [x] Build the Angular machine interface and live status feedback
- [x] Implement PIN authentication, role-based access and server-side sessions
- [x] Implement EUR deposits, banknote simulation and duplicate-request protection
- [ ] Implement CIT collection and cashbox replacement
- [ ] Implement device and daily reports with virtual printing
- [ ] Implement maintenance commands and device lifecycle simulation
- [ ] Add frontend controls for hardware fault simulation
- [ ] Extend audit history and test concurrency, interruptions and recovery
- [ ] Isolate integration test databases and add continuous integration

<details>
<summary><strong>Observed workflow requirements</strong></summary>

- Cashiers select a profile and authenticate with a PIN.
- CIT operators authenticate with a unique PIN without selecting a profile.
- The machine accepts one EUR banknote at a time.
- Accepted banknotes cannot be returned to the user.
- Logging out preserves the deposit without automatically printing a receipt.
- Daily totals start at midnight in `Europe/Belgrade`.
- Per-user daily reports require selecting a user.
- Confirmed cashbox replacement resets the device total while preserving daily totals.

Authentication and deposit rules are implemented. Reporting and cashbox
replacement requirements guide the upcoming workflows.

</details>

## 📌 Scope

This is an independent educational and portfolio project based on observed machine behavior.

It does not connect to real hardware, implement a vendor protocol or process real money. The backend implements authentication and authorization and is configured for local use on the loopback interface.

## 📄 License

Distributed under the **MIT License**. See [LICENSE](LICENSE) for details.