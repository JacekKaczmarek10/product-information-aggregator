# Product Information Aggregator

A backend service that combines data from multiple internal services into a single, market-aware response for a B2B e-commerce platform serving agricultural and machinery parts across European markets.

---

## How to Run

### Prerequisites

- Java 25 (Spring Boot 4.0 requires Java 17+, this project targets 25)
- Maven 3.9.11+

### Start the service

```bash
mvn spring-boot:run
```

The service starts on **http://localhost:8080**.

### Run tests

```bash
mvn test
```

### Try it out

```bash
# Full request with customer personalization
curl "http://localhost:8080/api/v1/products/PROD-001?market=nl-NL&customerId=CUST-42" | jq

# Without customer (non-personalized)
curl "http://localhost:8080/api/v1/products/PROD-001?market=pl-PL" | jq

# Different market
curl "http://localhost:8080/api/v1/products/PROD-002?market=de-DE&customerId=CUST-7" | jq
```

Swagger UI is available at: **http://localhost:8080/swagger-ui.html**

---

## API

### `GET /api/v1/products/{productId}`

| Parameter    | Required | Example     | Notes                                          |
|-------------|----------|-------------|------------------------------------------------|
| `productId` | Yes      | `PROD-001`  | Path variable                                  |
| `market`    | Yes      | `nl-NL`     | Query param, format `ll-CC`                    |
| `customerId`| No       | `CUST-42`   | When omitted, returns non-personalized response|

### Response Shape

```json
{
  "productId": "PROD-001",
  "market": "nl-NL",
  "language": "nl",
  "name": "Hydraulisch Pompsysteem",
  "description": "...",
  "category": "Hydraulics",
  "brand": "AgroTech",
  "specifications": { "weight_kg": "4.2", "pressure_bar": "250" },
  "imageUrls": ["https://cdn.example.com/..."],
  "pricing": {
    "currency": "EUR",
    "basePrice": 349.99,
    "discountPercent": 10,
    "finalPrice": 314.99,
    "priceValidUntil": "2026-04-25"
  },
  "availability": {
    "inStock": true,
    "stockLevel": 23,
    "warehouseLocation": "Amsterdam, NL",
    "expectedDelivery": "1-2 business days"
  },
  "personalization": {
    "customerSegment": "DEALER",
    "isPremiumCustomer": false,
    "preferredCategories": ["Hydraulics", "Filters"]
  },
  "dataStatus": {
    "catalogOk": true,
    "pricingAvailable": true,
    "availabilityKnown": true,
    "personalized": true
  }
}
```

When an optional service is unavailable, its field is **omitted** from the response and the corresponding `dataStatus` flag is `false`. Clients must check `dataStatus` before rendering price or stock information.

---

## Key Design Decisions

### 1. Required vs Optional Services — explicit contract at the type level

The core tension is: _which failures should propagate and which should degrade gracefully?_

I modeled this as a two-tier system in `ProductAggregatorService`:

- **Required** — Catalog. If it fails, the entire request fails with `503`. There is no meaningful product page without basic product data.
- **Optional** — Pricing, Availability, Customer. Failures yield `null` in the response with a `false` flag in `dataStatus`. The client knows what it got and what it didn't.

This is intentional: instead of hiding missing data behind a generic `"N/A"` string, the `dataStatus` object gives clients precise information to render appropriate UI (e.g. "Price currently unavailable — contact your dealer").

### 2. Parallel calls with per-service timeouts

All four upstream calls are fired concurrently using `CompletableFuture.supplyAsync()` on a shared `ExecutorService`. Each future has its own `.orTimeout()` deadline matching the expected latency budget of that service:

| Service      | Timeout |
|-------------|---------|
| Catalog     | 150ms   |
| Pricing     | 180ms   |
| Availability| 200ms   |
| Customer    | 160ms   |

Without parallelism, a sequential worst-case call (50+80+100+60ms = 290ms) would be fine. But with realistic jitter and occasional slowdowns, sequential calls would regularly exceed 500ms. Parallel execution means the wall-clock time is bounded by the slowest service, not the sum.

A timed-out optional future is cancelled immediately, freeing the thread without waiting.

### 3. Thread isolation + observability

Upstream calls run on a separate `CachedThreadPool`, not on the HTTP request-handling threads (Tomcat's thread pool). This prevents slow upstream calls from starving the request handler.

The service now also adds:
- `X-Correlation-Id` propagation via request filter (generated when missing)
- Micrometer metrics:
  - `aggregator.upstream.calls{service,status}`
  - `aggregator.upstream.latency{service,status}`

### 4. No customer call when no customerId

The Customer Service is only invoked when `customerId` is provided — not as an afterthought but as a design constraint. Calling it with no ID would be a wasted RPC and meaningless data. The future is immediately resolved as `CompletableFuture.completedFuture(null)`.

### 5. Realistic mocks + market configuration

Mock clients simulate:
- **Latency** — `Thread.sleep(base + random jitter)` per service specification
- **Failures** — `ThreadLocalRandom.nextDouble() < failureRate` per call
- **Deterministic data** — Product names, prices and stock levels are derived deterministically from productId + market, so the same request always returns consistent (if fictional) data — important for testing
- **20+ configured markets** — currencies, FX multipliers, warehouses and delivery windows are read from `application.yml`, not hardcoded in client classes

### 6. Separation of concerns

```
controller/   — HTTP in/out, validation, OpenAPI annotations
service/      — orchestration, timeout management, partial-failure logic
service/upstream/ — mock implementations of external services
model/        — request/response shapes and upstream DTOs
exception/    — domain exceptions + global handler
config/       — executor bean and properties binding
```

Adding a new upstream service means: add a client in `service/upstream/`, add a field to `ProductResponse`, and add one `CompletableFuture` in `ProductAggregatorService`. No existing code changes.

---

## What I Would Do Differently With More Time

**Use Resilience4j instead of the lightweight breaker** — this implementation includes an in-house circuit breaker for optional services (threshold + open window) to keep dependencies minimal. In production I'd replace it with Resilience4j for richer states, events, and operational controls.

**Caching** — Catalog data changes rarely; product specs don't change per request. A short-lived cache (e.g. Redis, Caffeine with a 60s TTL) would dramatically reduce upstream load and latency for popular products.

**Observability depth** — correlation IDs and Micrometer upstream metrics are already in place. Next step would be explicit SLO dashboards/alerts and per-market performance breakdowns.

**Graceful timeout budget** — Instead of per-service timeouts, I'd implement a shared deadline: take the current time, subtract from a total 250ms budget, and assign the remaining budget to whichever futures haven't resolved yet. This avoids the situation where all timeouts fire simultaneously but the total elapsed time exceeds the budget.

**Contract testing** — The mock clients are good for unit tests, but I'd add Pact or Spring Cloud Contract tests to verify that the mock data shapes match what real upstreams would return.

**Request validation** — The product ID currently accepts any non-blank string. I'd add a pattern constraint and, ideally, an early existence check to return `404` before making downstream calls.

---

## Design Question — Option A: Related Products Service

> _"The Assortment team wants to add a 'Related Products' service (200ms latency, 90% reliability). How would your design accommodate this?"_

**Adding it is a one-file change in the current design.**

The pluggable structure means: add `MockRelatedProductsClient`, add a `List<String> relatedProductIds` field (nullable) to `ProductResponse`, and add one `CompletableFuture` in `ProductAggregatorService` — optional, with a `200ms` timeout.

**Should it be required or optional?** Optional — clearly.

A 10% failure rate means roughly 1 in 10 requests would fail the entire product page if it were required. That's unacceptable for a "nice to have" feature. At 90% reliability and 200ms latency it sits in the same tier as Availability.

One nuance: it probably _shouldn't_ block the main response at all. Related products are a secondary UX element that can be loaded asynchronously. A better long-term design would be a separate `/api/v1/products/{id}/related` endpoint, letting the client fetch it in parallel with the main product view — decoupling the latency budgets entirely.

---

## Simulated Scenarios

### "A customer in Poland requests a product. Walk me through exactly what happens."

1. `GET /api/v1/products/PROD-001?market=pl-PL&customerId=CUST-42` arrives at `ProductController`.
2. Request is validated: `market` matches `ll-CC`, `productId` is non-blank.
3. `ProductAggregatorService.aggregate("PROD-001", "pl-PL", "CUST-42")` is called.
4. Four `CompletableFuture`s are created and submitted to the upstream executor simultaneously:
   - Catalog: fetches Polish localized name "Układ pompy hydraulicznej", specs, images — ~50ms
   - Pricing: calculates PLN price (EUR × 4.25), applies customer discount — ~80ms
   - Availability: checks Warsaw warehouse stock level — ~100ms
   - Customer: retrieves segment ("DEALER"), preferences — ~60ms
5. All four complete concurrently. Wall-clock time ≈ 100ms (slowest service).
6. Aggregator builds `ProductResponse` with all four sections populated.
7. `dataStatus`: all four flags `true`.
8. JSON returned to client.

### "The Pricing Service starts timing out. What does the customer see?"

1. Pricing future reaches its 180ms timeout; `TimeoutException` is caught.
2. The future is cancelled. The warning is logged: `PricingService timed out for product=PROD-001`.
3. `pricing` field in the response is `null`.
4. `dataStatus.pricingAvailable = false`.
5. The client receives a `200 OK` response with full catalog and availability data, but no price.
6. The frontend can render: _"Price currently unavailable — please contact your dealer."_
7. If the Pricing Service recovers on the next request, the customer immediately sees normal pricing with no manual intervention needed.
