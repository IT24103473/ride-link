# Sequence diagrams

---

## 1. The full journey: request to payment

```mermaid
sequenceDiagram
  actor P as Passenger (Postman)
  participant R as Ride Service
  participant F as Fare & Payment
  participant A as Account
  participant D as Driver & Vehicle
  participant MQ as RabbitMQ

  P->>R: POST /rides (JWT)
  R->>F: POST /fares/estimates (forwards the passenger's JWT)
  F-->>R: 201 estimate
  Note over R: The estimate is snapshotted onto the ride,<br/>fixing the price for the life of the trip
  R-->>P: 201 ride REQUESTED

  P->>R: POST /rides/{id}/assignment
  R->>A: POST /auth/service-token (cached until 60s before expiry)
  A-->>R: SERVICE token
  R->>D: GET /internal/drivers/available
  D-->>R: ranked candidates (nearest first)
  R->>D: POST /internal/drivers/{d}/reserve
  D-->>R: 204 (or 409 -> try the next candidate)
  R-->>P: 200 ASSIGNED

  Note over R: driver accepts -> starts -> completes

  R->>MQ: ride.completed
  MQ-->>D: release the driver, credit the trip
  MQ-->>F: compute the final fare, create a PENDING payment

  P->>F: GET /payments/rides/{rideId}
  F-->>P: 404 PAYMENT_NOT_READY (event still in flight)
  P->>F: GET /payments/rides/{rideId} (retry)
  F-->>P: 200 fare + PENDING payment

  P->>F: POST /payments/{id}/pay
  F->>MQ: payment.succeeded
  MQ-->>R: ride.paymentStatus = PAID
  F-->>P: 200 paid, receipt RL-2026-000123
```

The single 404 in the middle is the visible face of eventual consistency. It carries the
distinct code `PAYMENT_NOT_READY` rather than a plain `NOT_FOUND` precisely so a client can
tell "not yet" from "no such thing" and poll accordingly.

---

## 2. Assignment, including the race

This is the only place in RideLink where two requests genuinely compete for the same row.

```mermaid
sequenceDiagram
  actor P1 as Passenger 1
  actor P2 as Passenger 2
  participant R as Ride Service
  participant D as Driver & Vehicle

  P1->>R: POST /rides/{a}/assignment
  P2->>R: POST /rides/{b}/assignment

  R->>D: GET /internal/drivers/available (for ride a)
  D-->>R: [driver X, driver Y]
  R->>D: GET /internal/drivers/available (for ride b)
  D-->>R: [driver X, driver Y]
  Note over R,D: Both searches see driver X as available:<br/>searching does not reserve anyone

  R->>D: POST /internal/drivers/X/reserve (ride a)
  activate D
  Note over D: AVAILABLE -> BUSY under an optimistic lock
  D-->>R: 204 reserved
  deactivate D

  R->>D: POST /internal/drivers/X/reserve (ride b)
  activate D
  Note over D: X is no longer AVAILABLE;<br/>the version check fails
  D-->>R: 409 DRIVER_NOT_AVAILABLE
  deactivate D

  Note over R: A 409 is an expected outcome, not an error:<br/>move on to the next candidate
  R->>D: POST /internal/drivers/Y/reserve (ride b)
  D-->>R: 204 reserved

  R-->>P1: 200 ASSIGNED to X
  R-->>P2: 200 ASSIGNED to Y
```

Both passengers get a driver. Without the version check, both rides would have been
assigned driver X and one passenger would have been left waiting for a car that never came.

---

## 3. No driver available

```mermaid
sequenceDiagram
  actor P as Passenger
  participant R as Ride Service
  participant D as Driver & Vehicle

  P->>R: POST /rides/{id}/assignment
  R->>D: GET /internal/drivers/available (VAN in GALLE)
  D-->>R: [] (nobody eligible)
  R-->>P: 409 NO_DRIVER_AVAILABLE

  Note over R: The ride stays REQUESTED, not CANCELLED

  P->>R: POST /rides/{id}/assignment (retry a moment later)
  R->>D: GET /internal/drivers/available
  D-->>R: [driver Z]
  R->>D: POST /internal/drivers/Z/reserve
  D-->>R: 204
  R-->>P: 200 ASSIGNED
```

Leaving the ride `REQUESTED` is a deliberate choice: the passenger still wants a ride, so
cancelling on their behalf would throw away a valid booking and force them to start again.

---

## 4. Driver rejection

```mermaid
sequenceDiagram
  actor Dr as Driver
  participant R as Ride Service
  participant D as Driver & Vehicle

  Note over R: Ride is ASSIGNED to this driver

  Dr->>R: POST /rides/{id}/reject
  R->>R: ASSIGNED -> REQUESTED, clear driverId
  R->>D: POST /internal/drivers/{d}/release
  D-->>R: 204
  R-->>Dr: 200 ride is REQUESTED again

  Note over R,D: If the release call fails it is logged, not propagated:<br/>the rejection has already succeeded, and a briefly<br/>stale driver is better than a failed request
```

---

## 5. Cancellation and the fee rule

```mermaid
sequenceDiagram
  actor P as Passenger
  participant R as Ride Service
  participant MQ as RabbitMQ
  participant D as Driver & Vehicle
  participant F as Fare & Payment

  Note over R: Ride is ACCEPTED - a driver has committed<br/>and is travelling to the pickup

  P->>R: POST /rides/{id}/cancel
  R->>R: Capture previousStatus = ACCEPTED, then transition to CANCELLED
  R->>MQ: ride.cancelled (previousStatus=ACCEPTED, cancelledBy=PASSENGER)
  R-->>P: 200 CANCELLED

  MQ-->>D: release the driver (no trip credited)
  MQ-->>F: PASSENGER + ACCEPTED -> charge LKR 100.00

  Note over F: Any other combination creates nothing at all,<br/>so the ride's payment lookup correctly stays 404
```

`previousStatus` has to travel on the event because by the time it is published the ride is
already `CANCELLED` — the Fare service could not otherwise tell a late cancellation from an
early one.

---

## 6. Suspension propagating to dispatch

```mermaid
sequenceDiagram
  actor Ad as Admin
  participant A as Account
  participant MQ as RabbitMQ
  participant D as Driver & Vehicle

  Ad->>A: PATCH /accounts/{id}/status {SUSPENDED}
  A->>A: ACTIVE -> SUSPENDED
  A->>MQ: account.status.changed
  A-->>Ad: 200

  MQ-->>D: set accountActive = false
  Note over D: A driver who was AVAILABLE is forced OFFLINE,<br/>so they stop appearing in searches immediately.<br/>A driver mid-ride stays BUSY - the passenger<br/>in the vehicle is not stranded.
```

Matching never calls the Account service, so without this event a suspended driver would
keep taking rides until someone noticed.
