CREATE TABLE order_documents (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id             UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    type                 VARCHAR(30) NOT NULL,
    original_file_name   VARCHAR(255) NOT NULL,
    file_key             VARCHAR(512) NOT NULL,
    content_type         VARCHAR(100),
    size_bytes           BIGINT NOT NULL,
    uploaded_at          TIMESTAMP NOT NULL,
    uploaded_by_user_id  BIGINT
);

CREATE INDEX idx_order_documents_order_id ON order_documents(order_id);
