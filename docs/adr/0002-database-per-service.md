# ADR 0002: A database and a database user per service

**Status:** Accepted
**Date:** 2026-09-25

## Context

The services need each other's data: the Ride service refers to drivers and passengers, the
Fare service prices trips it never sees. The simplest arrangement would be one schema every
service can read.

## Decision

Each service gets its own MySQL database **and its own database user**, granted privileges
only on that database. Cross-service data moves over REST or events, referenced by UUID.

## Reasoning

A shared schema is the failure mode that quietly turns microservices back into a
distributed monolith: one service reads another's table "just this once", and from then on
neither can change its schema independently. Because that coupling is invisible in a code
review, it has to be prevented structurally rather than by agreement.

Granting each user access to one database alone means a service **physically cannot** read
another's tables. Ownership is enforced by MySQL, not promised in a document.

## Consequences

- Some data is duplicated on purpose: the driver's name in the Driver service, the fare
  snapshot on the ride. Each copy has a stated reason and is fed by events; none is
  authoritative.
- A cross-service join is impossible, so reporting would need a separate read model.
- Setup is more involved: four databases and four users, created by `infra/mysql/init.sql`.
- Some data is eventually consistent, which is visible in the API and documented as such.
