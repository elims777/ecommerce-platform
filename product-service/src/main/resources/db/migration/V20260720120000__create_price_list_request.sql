CREATE TABLE price_list_request (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    client_type VARCHAR(10) NOT NULL,
    category_ids BIGINT[] NOT NULL,
    status VARCHAR(20) NOT NULL,
    file_key VARCHAR(500),
    row_count INTEGER,
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP
);

CREATE INDEX idx_price_list_request_user_created ON price_list_request (user_id, created_at DESC);
CREATE INDEX idx_price_list_request_status_created ON price_list_request (status, created_at);

-- Не более одного PENDING-запроса на пользователя (защита от гонки при двух быстрых кликах)
CREATE UNIQUE INDEX ux_price_list_one_pending ON price_list_request (user_id) WHERE status = 'PENDING';
