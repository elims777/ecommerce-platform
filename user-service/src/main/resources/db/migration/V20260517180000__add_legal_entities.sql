-- legal_entities: independent entity with own credentials
CREATE TABLE legal_entities (
    id                  BIGSERIAL PRIMARY KEY,
    inn                 VARCHAR(12)  NOT NULL UNIQUE,
    ogrn                VARCHAR(15)  NOT NULL,
    full_name           VARCHAR(255) NOT NULL,
    director            VARCHAR(255) NOT NULL,
    phone               VARCHAR(20)  NOT NULL,
    email               VARCHAR(255) NOT NULL UNIQUE,
    password            VARCHAR(255) NOT NULL,
    -- legal address (embedded, always one)
    legal_city          VARCHAR(150) NOT NULL,
    legal_street        VARCHAR(150) NOT NULL,
    legal_building      VARCHAR(20)  NOT NULL,
    legal_postal_code   VARCHAR(10),
    -- verification
    verification_status VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
        CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED')),
    verified_at         TIMESTAMP,
    verified_by         VARCHAR(255),
    -- email confirmation token
    email_confirm_token VARCHAR(36) UNIQUE,
    email_verified      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

-- user_legal_entities: many-to-many link between physical users and legal entities
CREATE TABLE user_legal_entities (
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    legal_entity_id BIGINT NOT NULL REFERENCES legal_entities(id) ON DELETE CASCADE,
    link_status     VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (link_status IN ('PENDING', 'CONFIRMED')),
    link_token      VARCHAR(36) UNIQUE,
    linked_at       TIMESTAMP,
    PRIMARY KEY (user_id, legal_entity_id)
);

-- legal_entity_bank_accounts
CREATE TABLE legal_entity_bank_accounts (
    id                    BIGSERIAL PRIMARY KEY,
    legal_entity_id       BIGINT NOT NULL REFERENCES legal_entities(id) ON DELETE CASCADE,
    bank_name             VARCHAR(255) NOT NULL,
    bik                   VARCHAR(9)   NOT NULL,
    correspondent_account VARCHAR(20)  NOT NULL,
    settlement_account    VARCHAR(20)  NOT NULL,
    is_primary            BOOLEAN NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMP NOT NULL DEFAULT NOW()
);

-- legal_entity_addresses: multiple actual (shipping) addresses per legal entity
CREATE TABLE legal_entity_addresses (
    id              BIGSERIAL PRIMARY KEY,
    legal_entity_id BIGINT NOT NULL REFERENCES legal_entities(id) ON DELETE CASCADE,
    city            VARCHAR(150) NOT NULL,
    street          VARCHAR(150) NOT NULL,
    building        VARCHAR(20)  NOT NULL,
    apartment       VARCHAR(20),
    postal_code     VARCHAR(10),
    is_primary      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
