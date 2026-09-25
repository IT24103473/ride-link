# ADR 0003: Local HS256 token verification with a shared secret

**Status:** Accepted, with a known limitation
**Date:** 2026-09-25

## Context

All four services must authorise requests. The options were a shared symmetric secret
(HS256), asymmetric signing with a published key set (RS256 + JWKS), or calling the Account
service to introspect every token.

## Decision

The Account service is the only issuer. It signs HS256 with a secret from the environment,
and every service verifies **locally** with the same secret.

## Reasoning

Local verification means no service depends on Account being reachable in order to
authorise a request. Driver search keeps working during an Account outage, and there is no
network hop per request.

Introspection was rejected for the opposite reason: it would make Account a hard dependency
of every single request in the system, and by far its busiest caller.

HS256 rather than RS256 was chosen for the deadline: it needs no key-pair management and no
JWKS endpoint. That is a trade-off, not a claim that it is the better design.

## Consequences

**Known limitation, stated in the report:** the secret is symmetric, so any service holding
it can mint tokens as well as verify them. The Driver service could, in principle, issue
itself an admin token. Nothing in the code does this, but nothing structurally prevents it.

**The improvement** is RS256: Account keeps the private key and exposes a JWKS endpoint, and
the other three hold only the public key, able to verify but unable to sign.

A second limitation: a token stays valid until it expires, so a suspended user keeps access
for up to the TTL. Mitigated by a short TTL and, for drivers, the `accountActive` flag the
Driver service maintains from events.
