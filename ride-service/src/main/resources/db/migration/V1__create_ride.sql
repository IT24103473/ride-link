-- Ride Management Service schema.
--
-- Portability: CHAR(36) UUIDs, VARCHAR enums, DECIMAL money and DATETIME(6) timestamps,
-- so the same DDL runs on MySQL 8 and on H2 in MySQL mode.

CREATE TABLE ride (
    id                    CHAR(36)      NOT NULL,
    passenger_id          CHAR(36)      NOT NULL,
    -- Null until a driver is reserved; cleared again if they reject.
    driver_id             CHAR(36)      NULL,
    -- TUK | CAR | VAN
    vehicle_type          VARCHAR(10)   NOT NULL,
    -- COLOMBO | NEGOMBO | KANDY | GALLE | TRINCOMALEE
    service_area          VARCHAR(20)   NOT NULL,
    pickup_name           VARCHAR(100)  NOT NULL,
    pickup_lat            DOUBLE        NOT NULL,
    pickup_lng            DOUBLE        NOT NULL,
    destination_name      VARCHAR(100)  NOT NULL,
    destination_lat       DOUBLE        NOT NULL,
    destination_lng       DOUBLE        NOT NULL,
    -- REQUESTED | ASSIGNED | ACCEPTED | IN_PROGRESS | COMPLETED | CANCELLED
    status                VARCHAR(20)   NOT NULL,
    -- CASH | CARD
    payment_method        VARCHAR(10)   NOT NULL,

    -- Fare snapshot taken at request time. Copied rather than referenced, so the price a
    -- passenger was quoted cannot change underneath them if tariffs move.
    fare_estimate_id      CHAR(36)      NULL,
    estimated_fare        DECIMAL(10,2) NULL,
    estimated_distance_km DECIMAL(8,2)  NULL,
    estimated_duration_min INT          NULL,
    currency              VARCHAR(3)    NULL,

    requested_at          DATETIME(6)   NOT NULL,
    assigned_at           DATETIME(6)   NULL,
    accepted_at           DATETIME(6)   NULL,
    started_at            DATETIME(6)   NULL,
    completed_at          DATETIME(6)   NULL,
    cancelled_at          DATETIME(6)   NULL,
    -- PASSENGER | DRIVER | ADMIN
    cancelled_by          VARCHAR(20)   NULL,
    cancellation_reason   VARCHAR(255)  NULL,

    -- Read-only copy maintained from payment events; Fare & Payment owns the truth.
    -- NOT_DUE | PENDING | PAID | FAILED
    payment_status        VARCHAR(20)   NOT NULL,
    version               BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_ride PRIMARY KEY (id)
);

-- Backs the one-active-ride-per-passenger check and the passenger's ride list.
CREATE INDEX idx_ride_passenger_status ON ride (passenger_id, status);
-- Backs a driver's list of their assigned rides.
CREATE INDEX idx_ride_driver_status ON ride (driver_id, status);

CREATE TABLE ride_status_history (
    id          CHAR(36)     NOT NULL,
    ride_id     CHAR(36)     NOT NULL,
    -- Null on the row recording the ride's creation.
    from_status VARCHAR(20)  NULL,
    to_status   VARCHAR(20)  NOT NULL,
    -- Account id that caused the change, or SYSTEM for an event-driven one.
    changed_by  VARCHAR(40)  NOT NULL,
    changed_at  DATETIME(6)  NOT NULL,
    note        VARCHAR(255) NULL,
    CONSTRAINT pk_ride_status_history PRIMARY KEY (id),
    CONSTRAINT fk_history_ride FOREIGN KEY (ride_id) REFERENCES ride (id)
);

CREATE INDEX idx_history_ride ON ride_status_history (ride_id, changed_at);

-- Consumer idempotency; see docs/contracts/events.md.
CREATE TABLE processed_event (
    event_id     CHAR(36)    NOT NULL,
    event_type   VARCHAR(60) NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_processed_event PRIMARY KEY (event_id)
);
