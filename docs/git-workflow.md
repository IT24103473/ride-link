# Git workflow

This project is assessed individually as well as collectively: each member is marked on
their own service **and on their own Git history**. The workflow below exists so that
history is a fair record of who did what.

---

## 1. Branches

| Branch | Purpose | Protected |
|---|---|---|
| `main` | Released, demo-ready. Tagged `v1.0.0` at the deadline | Yes |
| `develop` | Integration branch; everything lands here first | Yes |
| `feature/<service>/<desc>` | One feature | No |
| `fix/<service>/<desc>` | One bug fix | No |
| `docs/<desc>` | Documentation only | No |
| `chore/<desc>` | Shared scaffolding, CI, build config | No |

Examples:

```
feature/ride/assignment-endpoint
feature/driver/haversine-matching
fix/payment/duplicate-charge-on-redelivery
docs/architecture-diagrams
chore/ci-matrix-build
```

The service name in the branch makes it obvious at a glance whose work a branch is, which
matters when four people are pushing at once.

---

## 2. Flow

```
feature branch  ──PR──►  develop  ──release PR──►  main  ──tag──►  v1.0.0
                   ▲
            review by another member
```

1. Branch from `develop`.
2. Commit in small steps as you work.
3. Open a PR into `develop`.
4. **At least one other member reviews it.** Reviews are marked evidence, so they must
   contain real comments — not just an approval click.
5. Merge **without squashing**. Individual commits are preserved on purpose: they are the
   evidence of each member's contribution, and squashing would erase it.
6. At the end, one release PR from `develop` into `main`, then tag `v1.0.0`.

---

## 3. Commits

[Conventional Commits](https://www.conventionalcommits.org/), with the service as the
scope:

```
feat(ride): add assignment endpoint with retry over candidates
fix(driver): make release idempotent
test(fare): cover minimum-fare boundary
docs(architecture): add data ownership table
chore(ci): run the matrix build on pull requests
refactor(account): extract token issuing into TokenIssuer
```

| Type | Use for |
|---|---|
| `feat` | New behaviour |
| `fix` | A bug fix |
| `test` | Tests only |
| `docs` | Documentation only |
| `refactor` | Restructuring with no behaviour change |
| `chore` | Build, CI, tooling |

### What makes a good commit here

- **One logical change.** One endpoint, one rule, one test class.
- **It builds.** Every commit should compile and pass its tests, so `git bisect` is usable
  and a reviewer can read the history in order.
- **The body says *why*.** The diff already shows what changed. The body should explain the
  reasoning someone would otherwise have to reconstruct — especially a non-obvious
  trade-off, because that is exactly what you will be asked about in the viva.

A commit you cannot defend line by line is a commit you should not have made.

---

## 4. Pull requests

Use the template in `.github/pull_request_template.md`. A PR should state:

- **What** changed and **why**.
- Which workflow or rubric item it serves.
- The tests added — including at least one negative or boundary case.
- Evidence where relevant: a Swagger screenshot, a Postman run, the CI result.

### Review expectations

A review is not a rubber stamp. Look for, and comment on:

- Business logic in a controller instead of the service layer.
- A JPA entity returned directly from a controller.
- Missing negative-path tests.
- Anything that reads another service's database.
- Hard-coded secrets, tariffs or URLs.
- A change to a shared contract (`docs/contracts/`) without group agreement.

---

## 5. Rules that are not negotiable

- **Never commit `.env`**, or any real password, token or connection string. `.gitignore`
  covers it; do not force-add it.
- **Never commit `target/`**, IDE folders, logs or build output.
- **Never rewrite history on `main` or `develop`.** No force pushes to shared branches.
- **Never change another member's service** without agreeing it first and saying so in the
  PR description.
- **Never change a shared contract unilaterally.** `docs/contracts/events.md` and the
  endpoint specifications are agreements between four people. Changing one silently breaks
  someone else's consumer. Raise it, agree it, then change it.

---

## 6. Branch protection

Both `main` and `develop` require:

- The CI check to pass.
- One approving review.
- No force pushes.

Configure this under **Settings → Branches** in GitHub, and screenshot it for the report —
it is I3 rubric evidence.

---

## 7. Release

When the build on `develop` is demo-ready:

```bash
git checkout develop
git pull
# open a release PR: develop -> main, reviewed as usual

git checkout main
git pull
git tag -a v1.0.0 -m "RideLink v1.0.0 - IT3130 submission"
git push origin v1.0.0
```

Then verify the tag from a clean clone before submitting: every member should be able to
follow the README from scratch and reach a working demo.
