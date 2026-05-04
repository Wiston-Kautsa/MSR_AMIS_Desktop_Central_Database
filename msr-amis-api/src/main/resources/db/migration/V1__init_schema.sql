CREATE TABLE departments (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(200) NOT NULL,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    department VARCHAR(100),
    phone VARCHAR(50),
    email VARCHAR(150) NOT NULL UNIQUE,
    reset_code VARCHAR(20),
    reset_expiry TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE equipment (
    id BIGSERIAL PRIMARY KEY,
    asset_code VARCHAR(60) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    category VARCHAR(100) NOT NULL,
    serial_number VARCHAR(100) NOT NULL UNIQUE,
    item_condition VARCHAR(100),
    source VARCHAR(200),
    entry_date DATE NOT NULL DEFAULT CURRENT_DATE,
    status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE assignments (
    id BIGSERIAL PRIMARY KEY,
    person VARCHAR(200),
    department VARCHAR(100),
    equipment_type VARCHAR(100),
    reason TEXT,
    quantity INTEGER,
    date_created DATE NOT NULL DEFAULT CURRENT_DATE
);

CREATE TABLE distribution (
    id BIGSERIAL PRIMARY KEY,
    asset_code VARCHAR(60),
    assignment_id BIGINT,
    assigned_to VARCHAR(200),
    phone VARCHAR(50),
    nid VARCHAR(50),
    outstanding_remarks TEXT,
    assigned_at DATE NOT NULL DEFAULT CURRENT_DATE,
    returned BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE returns (
    id BIGSERIAL PRIMARY KEY,
    asset_code VARCHAR(60) NOT NULL,
    returned_by VARCHAR(200) NOT NULL,
    phone VARCHAR(50),
    nid VARCHAR(50),
    item_condition VARCHAR(100),
    remarks TEXT,
    return_date DATE NOT NULL DEFAULT CURRENT_DATE
);

CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    action VARCHAR(100) NOT NULL,
    entity VARCHAR(100),
    entity_id VARCHAR(100),
    performed_by VARCHAR(150),
    details TEXT,
    action_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_equipment_asset_code ON equipment(asset_code);
CREATE INDEX idx_equipment_category ON equipment(category);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_distribution_asset_code ON distribution(asset_code);
CREATE INDEX idx_returns_asset_code ON returns(asset_code);
