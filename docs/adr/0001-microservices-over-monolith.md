# ADR 0001: Four microservices rather than a modular monolith

**Status:** Accepted
**Date:** 2026-09-25

## Context

RideLink is built by four people who are each marked individually on the service they own.
The system itself is small: a handful of entities and no meaningful load.

Two shapes were available: one well-modularised application with four internal modules, or
four separately deployable services.

## Decision

Four independently deployable Spring Boot services, split by business capability:
identity, driver supply, the trip, and money.

## Reasoning

The deciding factor is **team structure, not load**. Four people working in one codebase on
one deployment would contend constantly over shared configuration, shared test fixtures and
a shared release. Four services let each member own their build, their schema and their
release cadence, which is also what makes individual assessment possible at all.

The chosen boundaries also keep the most likely changes inside one service. Pricing is the
clearest case: surge rates, night rates and promotions all live entirely in Fare & Payment,
because nothing else knows how a fare is computed.

## Consequences

**Accepted costs:**

- Four applications plus MySQL and RabbitMQ to run for a demo.
- No transaction spans services, so consistency relies on idempotency and events.
- Every interservice call can time out and needs a considered failure path.
- Security config, error handling and the event envelope are duplicated four times.
- Nothing is fully verifiable without all four running, hence the Postman collection.

**Honest assessment:** for this system's actual load, a modular monolith would be cheaper
and simpler. The distribution buys team independence and clear boundaries, and that is the
justification, not throughput.
