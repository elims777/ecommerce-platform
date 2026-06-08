CREATE TABLE user_favourites (
    id          BIGSERIAL PRIMARY KEY,
    user_email  VARCHAR(255) NOT NULL,
    product_id  BIGINT       NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_product UNIQUE (user_email, product_id)
);

CREATE INDEX idx_favourites_user_email ON user_favourites(user_email);
CREATE INDEX idx_favourites_product_id ON user_favourites(product_id);
