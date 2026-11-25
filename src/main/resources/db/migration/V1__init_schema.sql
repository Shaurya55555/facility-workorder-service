CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN', 'MANAGER', 'TECHNICIAN')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE assets (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    asset_type VARCHAR(40)  NOT NULL,
    location   VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE work_orders (
    id           BIGSERIAL PRIMARY KEY,
    title        VARCHAR(160) NOT NULL,
    description  TEXT,
    asset_id     BIGINT       NOT NULL REFERENCES assets (id),
    priority     VARCHAR(10)  NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    status       VARCHAR(15)  NOT NULL CHECK (status IN ('OPEN', 'ASSIGNED', 'IN_PROGRESS', 'DONE')),
    created_by   BIGINT       NOT NULL REFERENCES users (id),
    assigned_to  BIGINT       REFERENCES users (id),
    version      BIGINT       NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    -- invariant: an order is OPEN exactly when nobody is assigned
    CONSTRAINT chk_open_iff_unassigned CHECK ((status = 'OPEN') = (assigned_to IS NULL))
);

CREATE INDEX idx_work_orders_status      ON work_orders (status);
CREATE INDEX idx_work_orders_asset_id    ON work_orders (asset_id);
CREATE INDEX idx_work_orders_priority    ON work_orders (priority);
CREATE INDEX idx_work_orders_assigned_to ON work_orders (assigned_to);
