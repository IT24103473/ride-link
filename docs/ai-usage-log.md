# AI usage log

Required by CLAUDE.md section 0.5 and used as the AI-use appendix in the report.

One line per working session. Every member logs their own use.

| Date | Member | Service | What the tool was used for |
|---|---|---|---|
| 2026-09-25 | _TBD_ | all four + shared scaffolding | Claude Code (Opus 5) used to generate the initial implementation: aggregator build, docker-compose and MySQL bootstrap, CI workflow, the four services (entities, Flyway migrations, security, REST clients, RabbitMQ publishers and consumers, controllers), the unit and slice tests, the Postman collection and the documentation under `docs/`. Output was reviewed and built locally; several defects it introduced were found by the tests and fixed (H2 reserved-word collisions on `year`, an over-strict path rule that blocked admin access to the internal driver search, and a `@Validated` placement that turned parameter validation into a 500 instead of a 400). |

---

## What to record

Be specific about the task, not the tool. "Used Claude Code" says nothing; "used Claude
Code to draft the Haversine ranking tests, then corrected the tie-break assertion" is a
usable record.

Worth logging:

- Generating or drafting code, tests, configuration or documentation.
- Explaining an error, a stack trace or an unfamiliar API.
- Reviewing or refactoring existing code.
- Anything the tool got wrong that you had to correct — this is the most useful entry of
  all, because it shows where your own judgement was applied.

---

## Declaration for the report

> AI assistance (Claude Code) was used during development, as recorded above. All generated
> code was reviewed by the owning member before being committed, builds and passes its
> tests, and every member is able to explain the design decisions in their own service.
> Where the tool produced incorrect output, the defect and its correction are noted in the
> log.
