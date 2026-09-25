## What

<!-- One or two sentences: what does this PR change? -->

## Why

<!-- The rubric item, workflow or bug this serves. -->

## Service / owner

- Service: <!-- account-service | driver-vehicle-service | ride-service | fare-payment-service -->
- Owner:

## Linked workflow

<!-- Which of the required workflows does this touch? e.g. "04 Ride happy path" -->

## Tests added

<!-- List the test classes/cases. Include at least one negative or boundary case. -->

## Evidence

<!-- Swagger screenshot, Postman run, or CI link where relevant. -->

## Checklist

- [ ] No secrets, passwords or connection strings committed
- [ ] `./mvnw -f <service>/pom.xml verify` passes locally
- [ ] Unit tests cover the happy path **and** at least one failure/boundary case
- [ ] OpenAPI annotations added or updated for new/changed endpoints
- [ ] README / `docs/` updated if behaviour or setup changed
- [ ] No change to another service or a shared contract without group agreement
- [ ] Commits follow Conventional Commits and are small and focused
