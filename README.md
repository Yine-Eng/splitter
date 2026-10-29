# Splitter: expense-splitting backend

A REST API for splitting shared expenses inside groups. It tracks who paid, who owes whom, and how debts get settled, with money handled to the exact cent.

Built for a team project in the Software Engineering course at Bluefield State University (Spring 2026). **I designed and built the backend** in this repository. A teammate wrote the first version of JWT sign-up and sign-in, which I later restructured. The frontend lived in a separate team effort and isn't part of this repo.

## What it does

- **Groups & roles:** create groups, add members, promote or demote admins, rename and archive groups. A group always keeps at least one active admin, and the creator can't be demoted. The admin check takes a pessimistic row lock so two concurrent demotions can't leave a group with no admin.
- **Cent-exact expense splitting:** amounts are `BigDecimal` normalized to 2 decimals. When a total doesn't divide evenly, the leftover cents are assigned in a **rotating order per group**, so the same person doesn't always absorb the extra cent.
- **Balances:** per-group net balances computed from expenses plus *confirmed* settlements only.
- **Settlement workflow** (`PENDING → CONFIRMED | REJECTED`): a debtor proposes a payment. Only the recipient can confirm or reject it. The debt is re-validated at confirmation time, so a payment can't over-settle a balance that changed in the meantime.
- **Safe member removal:** a preview endpoint shows outstanding balances, and removal is blocked until the member is settled up.
- **Activity feed:** 10 event types (expense created, settlement confirmed, member removed…) with visibility rules.
- **Reminders & notifications:** debt and settlement reminders. A database unique constraint limits each one to once per day, and in-app notifications have read tracking.
- **Security:** stateless JWT auth (Spring Security), membership checks on every group-scoped endpoint, and archived groups are read-only.

## API overview

| Area | Endpoints |
|---|---|
| Auth | `POST /api/auth/signup`, `POST /api/auth/signin` |
| Groups | `POST /api/groups`, `GET /api/groups`, `/{id}/members`, `/{id}/expenses`, `/{id}/balances`, `/{id}/events` |
| Admin | `PATCH /{id}/rename`, `POST /{id}/archive`, `POST /{id}/members/{userId}/promote` · `demote` · `remove`, `GET …/removal-preview` |
| Expenses | `POST /api/expenses` |
| Settlements | `POST /api/settlements`, `POST /{id}/confirm` · `reject` · `remind`, `GET /pending/incoming` · `outgoing`, `GET /groups/{groupId}` (history) |
| Notifications | `GET /api/notifications`, `POST /{id}/read`, `POST /api/groups/{id}/reminders/debt` |

## Tech

Java 17 · Spring Boot 3.3 · Spring Security · Spring Data JPA / Hibernate · JJWT · H2 (dev/test) · JUnit 5 + Spring Boot Test

## Testing

Integration tests run each workflow over HTTP against a real application context: auth, group access, expenses, balances, settlements, member removal, admin controls, activity feed and reminders. A unit test covers remainder rotation.

```bash
cd backend
./mvnw test
```

## Run locally

```bash
cd backend
./mvnw spring-boot:run   # http://localhost:8080/api/health
```

The dev profile uses an in-memory H2 database and a dev-only JWT secret. Set real values through environment variables before deploying anywhere.
