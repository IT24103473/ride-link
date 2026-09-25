# ADR 0004: Ride completion publishes an event instead of calling out

**Status:** Accepted
**Date:** 2026-09-25

## Context

When a driver completes a ride, two things must happen elsewhere: the Driver service
releases the driver, and the Fare service prices the trip and raises a payment.

The Ride service could call both synchronously, or publish one event that both consume.

## Decision

Publish `ride.completed` to a topic exchange. Both services bind their own queue to it.

## Reasoning

The driver is standing at the roadside waiting for the request to return. Making that
request depend on two other services means it fails if either is slow or restarting, so a
driver could not finish their trip because the payment service was redeploying. Nobody is
actually waiting on either reaction, so there is nothing to gain by blocking on them.

A topic exchange also means adding a third consumer later, such as notifications or
analytics, requires no change to the Ride service at all.

The same reasoning does **not** apply to the fare estimate or the driver reservation, which
stay synchronous: the passenger genuinely cannot proceed without the price, and a
reservation must resolve consistently so that exactly one of two simultaneous requests wins.

## Consequences

- The final fare appears a moment after completion. `GET /payments/rides/{id}` returns
  `404 PAYMENT_NOT_READY` until then, a distinct code so clients poll rather than give up.
- Every consumer must be idempotent, because delivery is at-least-once. Each records
  processed event ids and protects its business keys with unique indexes.
- **Events are published after the transaction commits, with no outbox.** A crash in that
  window would lose the event. The transactional outbox pattern is the documented
  improvement.
