CREATE TABLE user_addresses (
                                id                    BIGSERIAL    PRIMARY KEY,
                                user_id               BIGINT       NOT NULL,
                                label                 VARCHAR(50)  NOT NULL,
                                recipient_name        VARCHAR(100) NOT NULL,
                                phone                 VARCHAR(20)  NOT NULL,
                                city                  VARCHAR(100) NOT NULL,
                                street                VARCHAR(150) NOT NULL,
                                building              VARCHAR(20)  NOT NULL,
                                apartment             VARCHAR(20),
                                entrance              VARCHAR(20),
                                floor                 VARCHAR(10),
                                intercom_code         VARCHAR(50),
                                postal_code           VARCHAR(10),
                                delivery_instructions VARCHAR(500),
                                is_default            BOOLEAN      NOT NULL DEFAULT FALSE,
                                created_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
                                updated_at            TIMESTAMP    NOT NULL DEFAULT NOW(),

                                CONSTRAINT uk_user_address_label UNIQUE (user_id, label),
                                CONSTRAINT fk_user_address_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_addresses_user_id ON user_addresses(user_id);