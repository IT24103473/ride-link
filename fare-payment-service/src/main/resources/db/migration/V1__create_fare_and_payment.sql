-- Fare & Payment Service schema.
--
-- Portability: CHAR(36) UUIDs, VARCHAR enums, DECIMAL for money and DATETIME(6) for
-- timestamps, so the same DDL runs on MySQL 8 and on H2 in MySQL mode.
--
-- Money is DECIMAL(10,2), never DOUBLE: binary floating point cannot represent values
-- like 0.10 exactly, and the error would compound across a base fare plus two
-- multiplications.

CREATE TABLE fare_estimate (
    id                   CHAR(36)      NOT NULL,
    passenger_id         CHAR(36)      NOT NULL,
    pickup_name          VARCHAR(100)  NOT NULL,
    pickup_lat           DOUBLE        NOT NULL,
    pickup_lng           DOUBLE        NOT NULL,
    destination_name     VARCHAR(100)  NOT NULL,
    destination_lat      DOUBLE        NOT NULL,
    destination_lng      DOUBLE        NOT NULL,
    -- TUK | CAR | VAN
    vehicle_type         VARCHAR(10)   NOT NULL,
    distance_km          DECIMAL(8,2)  NOT NULL,
    duration_min         INT           NOT NULL,
    base_fare            DECIMAL(10,2) NOT NULL,
    distance_fare        DECIMAL(10,2) NOT NULL,
    time_fare            DECIMAL(10,2) NOT NULL,
    minimum_fare         DECIMAL(10,2) NOT NULL,
    minimum_fare_applied BOOLEAN       NOT NULL DEFAULT FALSE,
    total                DECIMAL(10,2) NOT NULL,
    currency             VARCHAR(3)    NOT NULL,
    created_at           DATETIME(6)   NOT NULL,
    -- Quotes expire, so a passenger cannot present an old price after a tariff change.
    expires_at           DATETIME(6)   NOT NULL,
    CONSTRAINT pk_fare_estimate PRIMARY KEY (id)
);

CREATE INDEX idx_fare_estimate_passenger ON fare_estimate (passenger_id, created_at);

CREATE TABLE final_fare (
    id                   CHAR(36)      NOT NULL,
    ride_id              CHAR(36)      NOT NULL,
    -- TRIP | CANCELLATION_FEE
    type                 VARCHAR(20)   NOT NULL,
    vehicle_type         VARCHAR(10)   NOT NULL,
    distance_km          DECIMAL(8,2)  NOT NULL,
    duration_min         INT           NOT NULL,
    base_fare            DECIMAL(10,2) NOT NULL,
    distance_fare        DECIMAL(10,2) NOT NULL,
    time_fare            DECIMAL(10,2) NOT NULL,
    minimum_fare         DECIMAL(10,2) NOT NULL,
    minimum_fare_applied BOOLEAN       NOT NULL DEFAULT FALSE,
    total                DECIMAL(10,2) NOT NULL,
    currency             VARCHAR(3)    NOT NULL,
    created_at           DATETIME(6)   NOT NULL,
    CONSTRAINT pk_final_fare PRIMARY KEY (id),
    -- One fare per ride: a redelivered ride.completed cannot create a second.
    CONSTRAINT uk_final_fare_ride UNIQUE (ride_id)
);

CREATE TABLE payment (
    id             CHAR(36)      NOT NULL,
    ride_id        CHAR(36)      NOT NULL,
    passenger_id   CHAR(36)      NOT NULL,
    -- Null for a cancellation fee raised before a driver was assigned.
    driver_id      CHAR(36)      NULL,
    final_fare_id  CHAR(36)      NOT NULL,
    type           VARCHAR(20)   NOT NULL,
    amount         DECIMAL(10,2) NOT NULL,
    currency       VARCHAR(3)    NOT NULL,
    -- CASH | CARD
    method         VARCHAR(10)   NOT NULL,
    -- PENDING | FAILED | SUCCEEDED
    status         VARCHAR(20)   NOT NULL,
    -- Allocated only on success, so an unpaid ride can never show a receipt number.
    receipt_number VARCHAR(20)   NULL,
    paid_at        DATETIME(6)   NULL,
    failure_reason VARCHAR(30)   NULL,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    version        BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_payment PRIMARY KEY (id),
    -- THE guard against double charging. Enforced by the database rather than by
    -- application logic, so no amount of event redelivery can produce a second charge.
    CONSTRAINT uk_payment_ride_type UNIQUE (ride_id, type),
    CONSTRAINT uk_payment_receipt UNIQUE (receipt_number)
);

CREATE INDEX idx_payment_passenger ON payment (passenger_id, created_at);

CREATE TABLE payment_attempt (
    id             CHAR(36)     NOT NULL,
    payment_id     CHAR(36)     NOT NULL,
    method         VARCHAR(10)  NOT NULL,
    -- SUCCEEDED | FAILED
    outcome        VARCHAR(20)  NOT NULL,
    failure_reason VARCHAR(30)  NULL,
    attempted_at   DATETIME(6)  NOT NULL,
    CONSTRAINT pk_payment_attempt PRIMARY KEY (id),
    CONSTRAINT fk_attempt_payment FOREIGN KEY (payment_id) REFERENCES payment (id)
);

CREATE INDEX idx_attempt_payment ON payment_attempt (payment_id, attempted_at);

-- Receipt numbering. A table rather than a sequence: sequences differ between MySQL and
-- H2, and numbering restarts each year, which a plain sequence cannot express.
CREATE TABLE receipt_counter (
    -- Neither column can take its obvious name: YEAR is reserved in H2 (which the tests
    -- run against) and LAST_VALUE is reserved in MySQL 8 (it is a window function). The
    -- names below are chosen to be legal in both.
    counter_year INT    NOT NULL,
    last_issued  BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_receipt_counter PRIMARY KEY (counter_year)
);

-- Consumer idempotency; see docs/contracts/events.md.
CREATE TABLE processed_event (
    event_id     CHAR(36)    NOT NULL,
    event_type   VARCHAR(60) NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_processed_event PRIMARY KEY (event_id)
);
