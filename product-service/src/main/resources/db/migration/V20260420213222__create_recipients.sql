CREATE TABLE recipients (
                            id          BIGSERIAL PRIMARY KEY,
                            user_id     BIGINT       NOT NULL,
                            name        VARCHAR(200) NOT NULL,
                            phone       VARCHAR(20)  NOT NULL,
                            is_default  BOOLEAN      NOT NULL DEFAULT FALSE,
                            created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
                            updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE recipient_addresses (
                                     id            BIGSERIAL    PRIMARY KEY,
                                     recipient_id  BIGINT       NOT NULL REFERENCES recipients(id) ON DELETE CASCADE,
                                     label         VARCHAR(50)  NOT NULL,
                                     city          VARCHAR(100) NOT NULL,
                                     street        VARCHAR(150) NOT NULL,
                                     building      VARCHAR(20)  NOT NULL,
                                     apartment     VARCHAR(20),
                                     postal_code   VARCHAR(10),
                                     is_default    BOOLEAN      NOT NULL DEFAULT FALSE,
                                     created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
                                     updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recipients_user_id ON recipients(user_id);
CREATE INDEX idx_recipient_addresses_recipient_id ON recipient_addresses(recipient_id);