# ADR 0005: Fare rules behind an interface, tariffs in configuration

**Status:** Accepted
**Date:** 2026-09-25

## Context

Fares are computed from a base charge, a distance rate, a time rate and a per-vehicle
minimum. The rates could be constants in Java, rows in a table, or configuration.

## Decision

`FareCalculator` is an interface, implemented by `DistanceTimeFareCalculator`. The rates
live in `application.yml` under `ridelink.tariffs` and bind to a typed record.

## Reasoning

Prices change far more often than code. Constants would mean a rebuild, a code review of
business logic and a redeploy every time a rate moved, and would scatter the numbers
through a class where nobody could see the whole rule at once.

The interface exists because pricing is the single most likely thing to change in a
ride-hailing system. Surge pricing, night rates, airport surcharges and promotions are all
new implementations rather than edits to the callers. Because the estimate and final-fare
paths share the abstraction, a new rule applies to both at once and cannot drift between
them.

A database table was rejected as more machinery than this needs: tariffs change rarely and
are not per-tenant. If they became per-city or time-of-day, a table would be the right call.

## Consequences

- `GET /fares/tariffs` publishes the formula and rates from the same configuration the
  calculator reads, so what is documented cannot drift from what is charged.
- A missing tariff throws at startup rather than silently pricing a vehicle class at zero.
- Changing a rate is a configuration change, so it is not covered by the unit tests that
  pin the arithmetic: those assert the formula, not the numbers in production config.
