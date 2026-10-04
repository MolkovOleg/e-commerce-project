CREATE TABLE user_profiles (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    phone VARCHAR(30),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_profiles_email ON user_profiles(email);

CREATE TABLE user_profile_roles (
    user_id UUID NOT NULL,
    role VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_profile_roles FOREIGN KEY (user_id)
        REFERENCES user_profiles(id) ON DELETE CASCADE
);

CREATE TABLE user_addresses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    country VARCHAR(100) NOT NULL DEFAULT 'Russia',
    city VARCHAR(100) NOT NULL,
    street VARCHAR(255) NOT NULL,
    building VARCHAR(20) NOT NULL,
    apartment VARCHAR(20),
    zip_code VARCHAR(20),
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_user_addresses FOREIGN KEY (user_id)
        REFERENCES user_profiles(id) ON DELETE CASCADE
);

CREATE TABLE processed_events (
    event_id VARCHAR(64) PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);