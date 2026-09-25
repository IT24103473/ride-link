-- Driver & Vehicle Service schema.
--
-- Portability: CHAR(36) UUIDs, VARCHAR enums and DATETIME(6) so the same DDL runs on
-- MySQL 8 and on H2 in MySQL mode.

CREATE TABLE driver_profile (
    -- Equals the Account Service's account id, so a driver has one id system-wide.
    driver_id           CHAR(36)     NOT NULL,
    full_name           VARCHAR(100) NOT NULL,
    phone               VARCHAR(20)  NOT NULL,
    licence_number      VARCHAR(30)  NULL,
    licence_expiry      DATE         NULL,
    -- COLOMBO | NEGOMBO | KANDY | GALLE | TRINCOMALEE
    service_area        VARCHAR(20)  NULL,
    -- PENDING | VERIFIED | REJECTED
    verification_status VARCHAR(20)  NOT NULL,
    -- Mirror of the Account status, maintained by account.status.changed.
    account_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    -- OFFLINE | AVAILABLE | BUSY
    availability        VARCHAR(20)  NOT NULL,
    available_since     DATETIME(6)  NULL,
    current_lat         DOUBLE       NULL,
    current_lng         DOUBLE       NULL,
    location_updated_at DATETIME(6)  NULL,
    current_ride_id     CHAR(36)     NULL,
    completed_trips     INT          NOT NULL DEFAULT 0,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_driver_profile PRIMARY KEY (driver_id)
);

-- The driver-search filter leads with these three columns, so this index is what keeps
-- matching fast as the fleet grows.
CREATE INDEX idx_driver_search
    ON driver_profile (availability, service_area, verification_status);

CREATE TABLE vehicle (
    id                  CHAR(36)     NOT NULL,
    driver_id           CHAR(36)     NOT NULL,
    registration_number VARCHAR(20)  NOT NULL,
    -- TUK | CAR | VAN
    type                VARCHAR(10)  NOT NULL,
    make                VARCHAR(40)  NOT NULL,
    model               VARCHAR(40)  NOT NULL,
    colour              VARCHAR(30)  NOT NULL,
    seats               INT          NOT NULL,
    -- Not "year": YEAR is a reserved word in H2, which the tests run against.
    manufacture_year    INT          NOT NULL,
    active              BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_vehicle PRIMARY KEY (id),
    -- One registration plate cannot belong to two drivers.
    CONSTRAINT uk_vehicle_registration UNIQUE (registration_number),
    CONSTRAINT fk_vehicle_driver FOREIGN KEY (driver_id)
        REFERENCES driver_profile (driver_id)
);

CREATE INDEX idx_vehicle_driver_active ON vehicle (driver_id, active);

-- Consumer idempotency: RabbitMQ delivers at least once, so every consumer records the
-- eventId it has handled and treats a duplicate-key insert as "already done".
CREATE TABLE processed_event (
    event_id     CHAR(36)     NOT NULL,
    event_type   VARCHAR(60)  NOT NULL,
    processed_at DATETIME(6)  NOT NULL,
    CONSTRAINT pk_processed_event PRIMARY KEY (event_id)
);
