# RideLink event catalogue

This document is the **source of truth** for every RabbitMQ message in RideLink.
A machine-readable version lives in [`asyncapi.yaml`](asyncapi.yaml).

> **Changing anything on this page requires group agreement.** Every service
> defines its own copy of these event records (there is no shared module), so a
> unilateral change silently breaks another member's consumer.

---

## 1. Transport

| Item | Value |
|---|---|
| Exchange | `ridelink.events` |
| Exchange type | `topic`, durable |
| Message format | JSON (`Jackson2JsonMessageConverter`) |
| Dead lettering | every queue `q` has `q.dlq`, bound via `x-dead-letter-exchange` |
| Redelivery | at most 3 attempts, then the message is dead-lettered |

Queues are durable and bound to the exchange by routing key. A queue that fails
a message three times sends it to its DLQ rather than looping forever; the DLQ
is inspected by hand in the RabbitMQ management UI at
<http://localhost:15672>.

## 2. Envelope

Every message on the exchange uses the same envelope. Only `payload` varies.

```json
{
  "eventId": "9d0c2f6e-1a4b-4f2e-9f01-6c1c2a7b1d33",
  "eventType": "ride.completed",
  "version": 1,
  "occurredAt": "2026-09-28T10:15:00Z",
  "correlationId": "3f2c1b9a-77de-4c1f-8d55-2a1c0b4e9f10",
  "payload": { }
}
```

| Field | Type | Notes |
|---|---|---|
| `eventId` | UUID | Unique per publication. **Consumers store this to stay idempotent.** |
| `eventType` | string | Equals the routing key. |
| `version` | int | Schema version, currently `1`. Additive changes keep the version. |
| `occurredAt` | ISO-8601 instant, UTC | When the fact happened, not when it was published. |
| `correlationId` | UUID | Carried from the originating HTTP request's `X-Correlation-Id`. |
| `payload` | object | Event-specific, documented below. |

`correlationId` is also set as an AMQP message header so it can be read without
deserialising the body.

## 3. Idempotency rule (mandatory for every consumer)

RabbitMQ guarantees *at-least-once* delivery, so a consumer will eventually see
the same event twice. Every consuming service therefore:

1. Keeps a `processed_event` table with `event_id` as the primary key.
2. Inserts the `eventId` inside the same transaction as the business change.
3. Treats a duplicate-key violation as "already handled" and acknowledges.

Where a natural business key exists it is also protected by a unique index
(for example `payment(ride_id, type)`), so a duplicate cannot create a second
charge even if the `processed_event` check is bypassed.

---

## 4. Events

### 4.1 `account.driver.registered`

| | |
|---|---|
| Producer | Account Service |
| Consumers | `driver.account-events` |
| Purpose | Let the Driver service create a profile shell for a newly registered driver. |

```json
{
  "accountId": "uuid",
  "fullName": "Nimal Perera",
  "email": "nimal@ridelink.test",
  "phone": "+94771234567"
}
```

Consumer behaviour: create a `DriverProfile` with `verificationStatus = PENDING`,
`availability = OFFLINE`, `accountActive = true`. **Skip silently if a profile
with that `driverId` already exists.**

---

### 4.2 `account.status.changed`

| | |
|---|---|
| Producer | Account Service |
| Consumers | `driver.account-events` |
| Purpose | Propagate suspension/reactivation so a suspended driver stops being matched. |

```json
{
  "accountId": "uuid",
  "role": "DRIVER",
  "oldStatus": "ACTIVE",
  "newStatus": "SUSPENDED"
}
```

Consumer behaviour: set `accountActive = (newStatus == ACTIVE)`. If the driver
was `AVAILABLE` and is no longer active, force `availability = OFFLINE`. Events
for non-DRIVER accounts are ignored.

---

### 4.3 `ride.completed`

| | |
|---|---|
| Producer | Ride Service |
| Consumers | `driver.ride-events`, `payment.ride-events` |
| Purpose | Release the driver and trigger final fare calculation. |

```json
{
  "rideId": "uuid",
  "passengerId": "uuid",
  "driverId": "uuid",
  "vehicleType": "CAR",
  "pickup":      { "name": "Colombo Fort", "lat": 6.9344, "lng": 79.8428 },
  "destination": { "name": "Negombo",      "lat": 7.2083, "lng": 79.8358 },
  "estimatedDistanceKm": 41.6,
  "actualDistanceKm": 43.2,
  "startedAt": "2026-09-28T09:30:00Z",
  "completedAt": "2026-09-28T10:15:00Z",
  "fareEstimateId": "uuid",
  "paymentMethod": "CARD"
}
```

`actualDistanceKm` is optional (absent when the driver did not report one).

Consumer behaviour:
- **Driver service:** release the driver (`BUSY -> AVAILABLE`, clear
  `currentRideId`) and increment `completedTrips`.
- **Fare & Payment:** compute the final fare and create a `PENDING` payment.

---

### 4.4 `ride.cancelled`

| | |
|---|---|
| Producer | Ride Service |
| Consumers | `driver.ride-events`, `payment.ride-events` |
| Purpose | Release a reserved driver and decide whether a cancellation fee applies. |

```json
{
  "rideId": "uuid",
  "passengerId": "uuid",
  "driverId": "uuid",
  "previousStatus": "ACCEPTED",
  "cancelledBy": "PASSENGER",
  "cancelledAt": "2026-09-28T09:20:00Z",
  "vehicleType": "CAR",
  "paymentMethod": "CARD"
}
```

`driverId` is absent when the ride was cancelled while still `REQUESTED`.
`previousStatus` is the status the ride held immediately before cancellation and
is what the cancellation-fee rule keys off.

Consumer behaviour:
- **Driver service:** release the driver **only if** `currentRideId == rideId`.
- **Fare & Payment:** charge LKR 100.00 only when
  `cancelledBy == PASSENGER && previousStatus == ACCEPTED`; otherwise create
  nothing.

---

### 4.5 `payment.succeeded`

| | |
|---|---|
| Producer | Fare & Payment Service |
| Consumers | `ride.payment-events` |
| Purpose | Let the Ride service show payment state without querying Payment. |

```json
{
  "paymentId": "uuid",
  "rideId": "uuid",
  "amount": 4485.00,
  "currency": "LKR",
  "receiptNumber": "RL-2026-000123",
  "paidAt": "2026-09-28T10:20:00Z"
}
```

Consumer behaviour: set the ride's read-model `paymentStatus = PAID`. Ignore if
the ride is unknown or already `PAID`.

---

### 4.6 `payment.failed`

| | |
|---|---|
| Producer | Fare & Payment Service |
| Consumers | `ride.payment-events` |
| Purpose | Reflect a declined payment on the ride. |

```json
{
  "paymentId": "uuid",
  "rideId": "uuid",
  "amount": 4485.00,
  "currency": "LKR",
  "failureReason": "CARD_DECLINED"
}
```

Consumer behaviour: set `paymentStatus = FAILED`. A later `payment.succeeded`
for the same ride overwrites it, because a failed payment may be retried.

---

## 5. Queue / binding summary

| Queue | Owner service | Bound routing keys |
|---|---|---|
| `driver.account-events` | Driver & Vehicle | `account.driver.registered`, `account.status.changed` |
| `driver.ride-events` | Driver & Vehicle | `ride.completed`, `ride.cancelled` |
| `payment.ride-events` | Fare & Payment | `ride.completed`, `ride.cancelled` |
| `ride.payment-events` | Ride Management | `payment.succeeded`, `payment.failed` |

Each queue above has a matching `<queue>.dlq`.

## 6. Known limitation

Events are published **after** the database transaction commits
(`@TransactionalEventListener(AFTER_COMMIT)`). There is no transactional
outbox, so a crash in the window between commit and publish loses the event.
This is accepted for the assignment; the outbox pattern is the documented
improvement (see the report's Limitations section).
