# Getting started from a fresh clone

Follow this top to bottom on a machine that has never run RideLink. It takes about ten
minutes, most of which is the first Maven build downloading dependencies.

If something goes wrong, jump to [§10 Troubleshooting](#10-troubleshooting) — it is keyed
by the exact symptom you will see.

---

## 1. Install the prerequisites

| Tool | Version | Check with |
|---|---|---|
| **JDK** | 21 (LTS) | `java -version` → must say `21` |
| **Docker Desktop** | any recent | `docker --version` |
| **Git** | any recent | `git --version` |

**You do not need to install Maven.** The repository ships the Maven Wrapper, which
downloads the right version on first use.

> **JDK 21 specifically.** The services are compiled for 21 and use records and pattern
> matching. On JDK 17 the build fails; on 22+ it may work but is untested.

---

## 2. Clone the repository

```bash
git clone https://github.com/IT24103473/ride-link.git
cd ride-link
```

If you use an SSH host alias (as the repo owner does):

```bash
git clone git@github-sliit:IT24103473/ride-link.git
cd ride-link
```

---

## 3. Create your `.env`

This is the step people skip, and **nothing will start without it**. The repository
deliberately contains no `.env` — only a template — because it must never hold real
credentials.

```bash
cp .env.example .env
```

On Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

Now **open `.env` and replace every `change-me`**. The file is git-ignored, so your values
stay on your machine.

### What each value does

| Variable | What it is | Rules |
|---|---|---|
| `JWT_SECRET` | Signs and verifies every token, shared by all four services | **At least 32 bytes.** Services refuse to start if it is shorter |
| `JWT_TTL_MINUTES` | How long a user token lasts | Leave at `60` |
| `ADMIN_EMAIL` | The seeded admin's login | Leave as `admin@ridelink.test` |
| `ADMIN_PASSWORD` | The seeded admin's password | Any 8+ chars with a letter and a digit |
| `MYSQL_ROOT_PASSWORD` | MySQL root, used only to create the four databases | Anything |
| `*_DB_USERNAME` / `*_DB_PASSWORD` | One user per service | Leave the usernames; change the passwords |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | Broker login, also the management UI login | Anything |
| `RIDE_SERVICE_CLIENT_ID` | Client id the Ride service presents to Account | Leave as `ride-service` |
| `RIDE_SERVICE_CLIENT_SECRET` | The matching secret | Anything |

### A working example

Safe for local development only — never use these values anywhere real:

```properties
JWT_SECRET=local-dev-only-secret-at-least-32-bytes-long
JWT_TTL_MINUTES=60
ADMIN_EMAIL=admin@ridelink.test
ADMIN_PASSWORD=ChangeMe123
MYSQL_ROOT_PASSWORD=localroot123
ACCOUNT_DB_USERNAME=account_svc
ACCOUNT_DB_PASSWORD=localacct123
DRIVER_DB_USERNAME=driver_svc
DRIVER_DB_PASSWORD=localdrv123
RIDE_DB_USERNAME=ride_svc
RIDE_DB_PASSWORD=localride123
PAYMENT_DB_USERNAME=payment_svc
PAYMENT_DB_PASSWORD=localpay123
RABBITMQ_USERNAME=ridelink
RABBITMQ_PASSWORD=localmq123
RIDE_SERVICE_CLIENT_ID=ride-service
RIDE_SERVICE_CLIENT_SECRET=localsvc123
```

To generate a proper secret instead of typing one:

```bash
# macOS / Linux / Git Bash
openssl rand -hex 32
```

```powershell
# Windows PowerShell
-join ((1..32) | ForEach-Object { '{0:x2}' -f (Get-Random -Max 256) })
```

### One thing to know now, not later

**MySQL reads `.env` only the first time its container starts.** It creates the four users
then and stores them in a Docker volume. If you later change a database password in
`.env`, MySQL will still expect the old one and the services will fail with *Access
denied*. The fix is in [§10](#10-troubleshooting), and it is simply to wipe the volume.

So: **finish editing `.env` before you run the next step.**

---

## 4. Start MySQL and RabbitMQ

```bash
docker compose up -d
```

Only the infrastructure runs in Docker; the four services run from Maven. On first start
this pulls the images and creates the four databases and four users.

Wait until both report `healthy`:

```bash
docker compose ps
```

```
NAME                STATUS
ridelink-mysql      Up 30 seconds (healthy)
ridelink-rabbitmq   Up 30 seconds (healthy)
```

`(health: starting)` just means it is still coming up — give it another 20 seconds.

### Confirm the databases were created

Optional, but it proves the bootstrap worked and demonstrates the data-ownership rule:

```bash
docker exec ridelink-mysql mysql -uroot -pYOUR_ROOT_PASSWORD -e "SHOW DATABASES;"
```

You should see `ridelink_account`, `ridelink_driver`, `ridelink_ride`, `ridelink_payment`.

And each service user can see only its own database:

```bash
docker exec ridelink-mysql mysql -uaccount_svc -pYOUR_ACCOUNT_PASSWORD -e "USE ridelink_driver;"
# ERROR 1044 (42000): Access denied for user 'account_svc'@'%' to database 'ridelink_driver'
```

That error is the expected, correct result.

---

## 5. Build

From the repository root:

```bash
./mvnw clean package
```

```powershell
# Windows
.\mvnw.cmd clean package
```

This compiles all four services and runs **299 tests**. The tests need neither MySQL nor
RabbitMQ — they use H2 and disable the message listeners — so they pass even if you
skipped step 4.

Expect `BUILD SUCCESS` and four jars:

```
account-service/target/account-service-1.0.0.jar
driver-vehicle-service/target/driver-vehicle-service-1.0.0.jar
ride-service/target/ride-service-1.0.0.jar
fare-payment-service/target/fare-payment-service-1.0.0.jar
```

The first build takes several minutes while dependencies download. Later builds take
about a minute.

> **On Windows, use `mvnw.cmd` from PowerShell or Command Prompt.** The `./mvnw` shell
> script can fail under Git Bash when it tries to download Maven into a Unix-style temp
> path.

---

## 6. Start the four services

Each needs **its own terminal**, and they must start **in this order** — Account issues
the tokens the others verify, and Ride calls the other three.

### Terminal 1 — Account (8081)

```bash
./mvnw -f account-service/pom.xml spring-boot:run
```

### Terminal 2 — Driver & Vehicle (8082)

```bash
./mvnw -f driver-vehicle-service/pom.xml spring-boot:run
```

### Terminal 3 — Fare & Payment (8084)

```bash
./mvnw -f fare-payment-service/pom.xml spring-boot:run
```

### Terminal 4 — Ride (8083)

```bash
./mvnw -f ride-service/pom.xml spring-boot:run
```

On Windows replace `./mvnw` with `.\mvnw.cmd`.

Wait for each to print `Started ...Application in N seconds` before starting the next.

### Faster alternative: run the jars

Once built, this starts far quicker. **Run each from inside its service folder** — the
services read `../.env`, so the working directory matters:

```bash
cd account-service        && java -jar target/account-service-1.0.0.jar
cd driver-vehicle-service && java -jar target/driver-vehicle-service-1.0.0.jar
cd fare-payment-service   && java -jar target/fare-payment-service-1.0.0.jar
cd ride-service           && java -jar target/ride-service-1.0.0.jar
```

---

## 7. Check everything is up

```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
```

All four must return `{"status":"UP"}`.

On Windows PowerShell, `curl` is an alias for `Invoke-WebRequest`; use `curl.exe` instead,
or just open the URLs in a browser.

### Check the messaging topology

```bash
docker exec ridelink-rabbitmq rabbitmqctl list_queues name messages
```

You should see eight queues — four consumer queues and four dead-letter queues:

```
driver.account-events       0
driver.account-events.dlq   0
driver.ride-events          0
driver.ride-events.dlq      0
payment.ride-events         0
payment.ride-events.dlq     0
ride.payment-events         0
ride.payment-events.dlq     0
```

Anything sitting in a `.dlq` means a consumer failed a message three times — worth
investigating before you demo.

---

## 8. Open Swagger UI

| Service | URL |
|---|---|
| Account | <http://localhost:8081/swagger-ui.html> |
| Driver & Vehicle | <http://localhost:8082/swagger-ui.html> |
| Ride | <http://localhost:8083/swagger-ui.html> |
| Fare & Payment | <http://localhost:8084/swagger-ui.html> |

To call a protected endpoint:

1. On the **Account** service, expand `POST /api/v1/auth/login`, click **Try it out**.
2. Send your `ADMIN_EMAIL` and `ADMIN_PASSWORD` from `.env`.
3. Copy the `accessToken` from the response.
4. Click **Authorize** (top right), paste the token, click Authorize.

The same token works on all four services — every service verifies it locally.

---

## 9. Run the full workflow

The Postman collection exercises every workflow and every negative scenario, with
assertions on each request.

### Option A — Postman GUI

Import both files from the `postman/` folder:

- `RideLink.postman_collection.json`
- `RideLink.local.postman_environment.json`

Select the **RideLink Local** environment (top right), then **Run** the collection. Run the
folders in order — later ones depend on ids the earlier ones store.

### Option B — Newman (command line)

```bash
npm install -g newman
newman run postman/RideLink.postman_collection.json \
  -e postman/RideLink.local.postman_environment.json
```

If your `.env` uses a different admin password from `ChangeMe123`, update `adminPassword`
in the environment file — or in Postman's environment editor — first.

> Two requests **poll**. The final fare and the cancellation fee are computed from RabbitMQ
> events, so `GET /payments/rides/{rideId}` returns `404 PAYMENT_NOT_READY` for a moment
> after a ride completes. The collection retries up to ten times. That is eventual
> consistency behaving correctly, not a failure.

---

## 10. Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `JWT_SECRET must be set and at least 32 bytes long` | `.env` is missing, the secret is shorter than 32 bytes, or the service cannot find the file. Each service reads `../.env`, so `java -jar` must be run **from inside that service's folder**. (`mvnw spring-boot:run` is fine from the repo root — the plugin sets the working directory to the module itself.) |
| `Access denied for user 'account_svc'@'%'` | You changed a DB password in `.env` **after** MySQL first started. MySQL kept the original in its volume. Fix: `docker compose down -v` then `docker compose up -d` (this wipes all data) |
| `Unknown database 'ridelink_account'` | The MySQL bootstrap did not run. Same fix: `docker compose down -v && docker compose up -d` |
| `Communications link failure` on startup | MySQL is not up yet. Check `docker compose ps` says `healthy`, then retry |
| `Port 8081 already in use` | An earlier service is still running. `npx kill-port 8081`, or on Windows `Get-Process java \| Stop-Process -Force` |
| Service starts, then `Schema-validation: wrong column type` | Your database was created by an older version of the migrations. `docker compose down -v && docker compose up -d` |
| `404 PAYMENT_NOT_READY` after completing a ride | Expected briefly. If it never resolves, RabbitMQ is down or the Fare service is not running |
| Assignment returns `503 DOWNSTREAM_UNAVAILABLE` | The Driver or Account service is not running. The `detail` field names which one |
| Assignment returns `409 NO_DRIVER_AVAILABLE` | No driver is VERIFIED **and** AVAILABLE **and** in that service area **and** has an active vehicle of that type **and** reported a location in the last 30 minutes. Folder `02` of the Postman collection sets all of this up |
| Driver gets `422 DRIVER_NOT_ELIGIBLE` | Read the `reasons` array in the response — it lists every requirement not yet met |
| `./mvnw` fails to download Maven on Windows | Use `.\mvnw.cmd` from PowerShell instead of Git Bash |
| `npm error UNABLE_TO_VERIFY_LEAF_SIGNATURE` installing newman | Your network intercepts TLS (common on campus or corporate Wi-Fi). Use the Postman GUI instead, or install newman from a different network |

---

## 11. Stopping and resetting

**Stop the services:** `Ctrl+C` in each terminal.

**Stop the infrastructure, keeping the data:**

```bash
docker compose down
```

**Wipe everything and start clean** — use this whenever credentials or migrations change:

```bash
docker compose down -v
docker compose up -d
```

`-v` deletes the volumes, so all accounts, drivers, rides and payments are lost. The admin
is seeded again from `.env` on the next start.

---

## 12. Daily routine, once set up

```bash
docker compose up -d                  # infra
./mvnw -f account-service/pom.xml spring-boot:run        # terminal 1
./mvnw -f driver-vehicle-service/pom.xml spring-boot:run # terminal 2
./mvnw -f fare-payment-service/pom.xml spring-boot:run   # terminal 3
./mvnw -f ride-service/pom.xml spring-boot:run           # terminal 4
```

Run just your own service's tests while working:

```bash
./mvnw -f ride-service/pom.xml test
```

---

## 13. Where to read next

| To understand | Read |
|---|---|
| Why the system is split this way | [`architecture.md`](architecture.md) |
| The state machine, matching and fare rules | [`business-rules.md`](business-rules.md) |
| What every event carries | [`contracts/events.md`](contracts/events.md) |
| How a booking flows end to end | [`sequence-ride-booking.md`](sequence-ride-booking.md) |
| Branching, commits and reviews | [`git-workflow.md`](git-workflow.md) |
| Why a particular decision was made | [`adr/`](adr) |
