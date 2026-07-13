# Payment Service

Payment processing microservice for the E-Commerce Microservices system — simulates a production-style payment gateway integration with a pluggable gateway abstraction, asynchronous processing, idempotency guarantees, and an immutable transaction ledger. Built as part of an industrial-level Spring Boot microservices portfolio project.

![Java](https://img.shields.io/badge/Java-17-orange) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.15-brightgreen) ![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2024.0.1-blue) ![SQL Server](https://img.shields.io/badge/Database-SQL%20Server-red) ![License](https://img.shields.io/badge/License-MIT-lightgrey)

## 📖 Overview

`payment-service` is one of six microservices in a larger e-commerce system. It owns the **payment domain** — receiving payment initiation requests from `order-service`, processing them asynchronously through a pluggable, gateway-agnostic abstraction, and reporting the final outcome back to `order-service` via a callback. It is deliberately designed so that the *only* thing "simulated" is the actual bank/card network step — every surrounding architectural pattern (idempotency, async processing, audit ledger, gateway abstraction) mirrors how a real payment integration (Stripe, PayPal, Razorpay) would be built in production.

> This service is part of a larger system. See the [main project README](#) for the full architecture and links to all repositories.

### Part of the E-Commerce Microservices Ecosystem

| Service | Responsibility | Port |
|---|---|---|
| eureka-server | Service discovery/registry | 8761 |
| api-gateway | Single entry point, routing | 8080 |
| user-service | Authentication, JWT issuance, user management | 8081 |
| product-service | Product catalog, CRUD, filtering | 8082 |
| order-service | Orders, cart, calls product-service, user-service & payment-service via Feign | 8083 |
| **payment-service** | **Simulated payment gateway flow, async processing, callback to order-service** | **8084** |

## 🏗️ Architecture & Design Decisions

This service follows a strict layered architecture, with two additional layers (`gateway`, `processor`) beyond the standard controller → service → repository flow:
Controller → Service → Repository → Database
↓
Gateway Abstraction (PaymentGatewayService)
↓
Scheduled Processor (async resolution)
↓
Feign Callback → order-service

Key design decisions and the reasoning behind them:

| Decision | Reasoning |
|---|---|
| **Gateway Abstraction — `PaymentGatewayService` interface + `SimulatedGatewayServiceImpl`** | The service layer never depends on a concrete gateway implementation. A real provider (see the commented-out `StripeGatewayServiceImpl` reference implementation) can be plugged in by adding one new class — no changes to `PaymentService`, `PaymentController`, or `PaymentProcessor`. This is the Strategy/Adapter pattern applied to payment processing. |
| **Asynchronous, poll-based processing (`@Scheduled`) instead of synchronous response** | Real payment gateways never resolve instantly — they process and confirm later, typically via a webhook. `POST /v1/payments` returns `INITIATED` immediately; a background `PaymentProcessor` resolves the outcome moments later, mirroring real gateway latency instead of faking an instant result. |
| **Designed to be queue-ready** | `PaymentProcessor` is a standalone component decoupled from the request-driven service layer. Migrating from `@Scheduled` polling to a message broker (RabbitMQ/Kafka) later would only change the trigger mechanism — the processing logic itself is unaffected. |
| **Idempotency key with a database-level unique constraint** | Application-level duplicate checks alone are vulnerable to race conditions under concurrent retries. A DB unique constraint on `idempotencyKey` is the last line of defense — the same pattern Stripe's `PaymentIntent` API uses. |
| **Multiple `Payment` rows allowed per order — no unique constraint on `orderId`** | A card can be declined and legitimately retried with a different method. Uniqueness is enforced at the application level instead: a new payment is blocked only if an existing one for the same order is `INITIATED`, `PROCESSING`, or already `SUCCESS` — `FAILED` attempts never block a retry. |
| **Separate `Payment` (mutable state) and `PaymentTransaction` (append-only ledger)** | `Payment` holds current status; `PaymentTransaction` records every transition (`previousStatus → newStatus`) as an immutable row. This reconstructs a full audit trail for any payment — a standard requirement in financial systems. |
| **Deterministic test triggers alongside a configurable success rate** | `payment.simulation.success-rate` drives random outcomes by default, but any amount ending in `.13` forces a guaranteed decline — mirroring Stripe's test-card pattern (e.g. `4000000000000002`). This makes both success and failure paths reproducible on demand for testing and demos, rather than relying on chance. |
| **`ServiceTokenProvider` for background-context authentication** | `PaymentProcessor` runs on a scheduled thread with no incoming HTTP request to source a JWT from. It generates its own short-lived (5-minute), self-signed service token (`subject: "payment-service"`, `role: "SERVICE"`) to authenticate its Feign callback to `order-service`, rather than bypassing security or leaving the callback endpoint unauthenticated. |
| **Payment status is never client-mutable** | No `PUT`/`PATCH` endpoint exists for payment status. The only writer of `SUCCESS`/`FAILED` is `PaymentProcessor` itself, after consulting the gateway abstraction — preventing any client from forging a payment outcome. |
| **No raw card/PIN data ever collected** | Consistent with PCI-DSS principles: raw payment credentials should never reach an application's own backend. `PaymentInitiateRequest` only carries a payment *method* (`CARD`/`WALLET`), never card numbers or PINs — exactly where a real gateway's client-side tokenization (e.g. Stripe.js) would sit in a production system. |
| **`BigDecimal` for all monetary fields** | Avoids floating-point rounding errors — standard practice for financial data. |
| **DTOs for all API boundaries** | Entities are never exposed directly; request and response shapes are decoupled from the persistence model. |

## 🛠️ Tech Stack

- Java 17
- Spring Boot 3.5.15
- Spring Cloud 2024.0.1 (Eureka Client, OpenFeign)
- Spring Data JPA (Hibernate)
- Spring Security (stateless, JWT-based)
- JJWT 0.12.6
- Spring Validation (`@Valid`)
- Microsoft SQL Server
- Lombok
- Maven

## 📁 Project Structure
payment-service/
├── src/main/java/com/ecommerce/payment_service/
│   ├── PaymentServiceApplication.java
│   ├── entity/          # Payment, PaymentTransaction, PaymentStatus, PaymentMethod
│   ├── dto/              # Request/Response DTOs
│   ├── repository/       # PaymentRepository, PaymentTransactionRepository
│   ├── client/            # OrderClient (Feign callback to order-service)
│   ├── security/          # JwtUtil, JwtAuthFilter, SecurityConfig, ServiceTokenProvider
│   ├── config/            # FeignConfig (service-token propagation interceptor)
│   ├── gateway/            # PaymentGatewayService interface, SimulatedGatewayServiceImpl,
│   │                       # StripeGatewayServiceImpl (commented reference implementation)
│   ├── processor/          # PaymentProcessor (@Scheduled async resolution engine)
│   ├── mapper/             # PaymentMapper (entity ↔ DTO conversion)
│   ├── service/            # PaymentService (business logic)
│   ├── controller/         # PaymentController (REST endpoints)
│   └── exception/          # Custom exceptions + GlobalExceptionHandler
└── src/main/resources/
└── application.yml

## 🔌 API Endpoints

All endpoints require a valid JWT in the `Authorization: Bearer <token>` header — there are no public endpoints in this service. Requests from `order-service` carry the original caller's JWT; the scheduled processor's own callback to `order-service` carries a self-issued service token instead.

| Method | Endpoint | Description | Request Body |
|---|---|---|---|
| POST | `/v1/payments` | Initiate a payment for an order (called by `order-service`). Returns immediately with status `INITIATED` — actual resolution happens asynchronously. | `PaymentInitiateRequest` |
| GET | `/v1/payments/{id}` | Get the current status of a specific payment | — |
| GET | `/v1/payments/order/{orderId}` | Get the full payment attempt history for an order (all attempts, including failed retries) | — |

## 📦 Sample Request/Response

**Initiate Payment** — `POST /v1/payments`

```json
{
  "orderId": 12,
  "userId": 7,
  "amount": 3000.00,
  "currency": "PKR",
  "method": "CARD",
  "idempotencyKey": "order-12-a1b2c3d4"
}
```

**Response — `201 Created`**

```json
{
  "id": 41,
  "orderId": 12,
  "amount": 3000.00,
  "currency": "PKR",
  "method": "CARD",
  "status": "INITIATED",
  "gatewayReferenceId": null,
  "failureReason": null,
  "createdAt": "2026-07-14T10:02:11",
  "updatedAt": "2026-07-14T10:02:11"
}
```

**Get Payment Status** — `GET /v1/payments/41` *(after the scheduled processor resolves it)*

**Response — `200 OK`**

```json
{
  "id": 41,
  "orderId": 12,
  "amount": 3000.00,
  "currency": "PKR",
  "method": "CARD",
  "status": "SUCCESS",
  "gatewayReferenceId": "sim_7f3a9c2e-4b1d-4e2a-9c3f-1a2b3c4d5e6f",
  "failureReason": null,
  "createdAt": "2026-07-14T10:02:11",
  "updatedAt": "2026-07-14T10:02:16"
}
```

**Error Response — `409 Conflict`** *(duplicate active payment for the same order)*

```json
{
  "status": 409,
  "error": "Conflict",
  "message": "An active payment already exists for orderId=12",
  "timestamp": "2026-07-14T10:03:02"
}
```

**Error Response — `400 Bad Request`** *(invalid payment method)*

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid payment method: BITCOIN. Allowed values: CARD, WALLET",
  "timestamp": "2026-07-14T10:03:44"
}
```

## ⚙️ Setup & Installation

### Prerequisites

- Java 17+
- Maven 3.8+
- Microsoft SQL Server running locally (or an accessible instance)
- `eureka-server` running on port 8761
- `order-service` running on port 8083 (required to receive the async status callback)

### 1. Clone the repository

```bash
git clone https://github.com/zainmustafa205/payment-service.git
cd payment-service
```

### 2. Create the database

SQL Server requires manual database creation — JPA/Hibernate only creates tables, not the database itself:

```sql
CREATE DATABASE paymentservice_db;
```

### 3. Set environment variables

```bash
export DB_USERNAME=${DB_USERNAME_PAYMENT_SERVICE}
export DB_PASSWORD=${DB_PASSWORD_PAYMENT_SERVICE}
export JWT_SECRET=${JWT_SECRET}
```

> The `JWT_SECRET` **must be identical** to the one configured in `user-service`, `order-service`, and `product-service` — this service only validates tokens, it never issues them. The secret is a **Base64-encoded** string (generated via `openssl rand -base64 32`); `JwtUtil` decodes it with `Decoders.BASE64.decode(secret)` before building the signing key, matching the decoding strategy used across every other service.

### 4. Run the application

```bash
mvn spring-boot:run
```

The service will start on port 8084 and register itself with Eureka at `http://localhost:8761`.

### 5. Verify registration

Visit `http://localhost:8761` and confirm `PAYMENT-SERVICE` appears in the list of registered instances, alongside `USER-SERVICE`, `PRODUCT-SERVICE`, and `ORDER-SERVICE`.

## 🔒 Configuration Notes

- `spring.jpa.hibernate.ddl-auto` is set to `update` — suitable for development. Use migration tools (Flyway/Liquibase) for production.
- Simulation behavior is fully externalized in `application.yml` — `payment.simulation.success-rate` and `payment.processor.poll-interval-ms` can be tuned without any code change (e.g. setting `success-rate: 0.0` to guarantee failures during a demo).
- This service does **not** issue JWTs for end-user requests — it only validates tokens issued by `user-service`. It does, however, issue its own short-lived **service tokens** (via `ServiceTokenProvider`) purely for authenticating its own outbound callback to `order-service` from a non-HTTP scheduled context.
- The `/v1/payments` endpoint is called internally by `order-service` via Feign (service discovery through Eureka) — it is never called directly by a frontend client, even though a gateway route exists for it.
- `StripeGatewayServiceImpl` is included as a fully commented-out reference implementation, proving that swapping the simulated gateway for a real one requires only a new class implementing `PaymentGatewayService` — see inline comments for activation steps.

## 🗺️ Roadmap

- [ ] Replace `@Scheduled` polling with a RabbitMQ-based event consumer
- [ ] Restrict `/v1/payments` to service-role callers only (currently accepts any authenticated JWT)
- [ ] Add refund flow (`PaymentGatewayService.refund(...)`)
- [ ] Add Flyway migrations for schema versioning
- [ ] Add unit and integration tests

## 📄 License

This project is part of a personal portfolio and is available under the MIT License.

## 🔗 Related Repositories

- [eureka-server](https://github.com/zainmustafa205/eureka-server)
- [api-gateway](https://github.com/zainmustafa205/api-gateway)
- [user-service](https://github.com/zainmustafa205/user-service)
- [product-service](https://github.com/zainmustafa205/product-service)
- [order-service](https://github.com/zainmustafa205/order-service)