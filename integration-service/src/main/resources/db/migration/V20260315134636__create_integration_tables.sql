-- Таблицы integration-service для обмена с 1С по протоколу CommerceML

-- Сессии обмена: хранение cookie для авторизации 1С
CREATE TABLE exchange_sessions (
                                   id BIGSERIAL PRIMARY KEY,
                                   session_id VARCHAR(64) NOT NULL UNIQUE,
                                   exchange_type VARCHAR(20) NOT NULL,           -- CATALOG, SALE
                                   status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, COMPLETED, EXPIRED
                                   created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   expires_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_exchange_sessions_session_id ON exchange_sessions(session_id);
CREATE INDEX idx_exchange_sessions_status ON exchange_sessions(status, expires_at);

-- Очередь задач на обработку изображений (bounded buffer + crash recovery)
CREATE TABLE image_processing_tasks (
                                        id BIGSERIAL PRIMARY KEY,
                                        product_external_id VARCHAR(50) NOT NULL,
                                        file_path VARCHAR(500) NOT NULL,
                                        original_filename VARCHAR(255) NOT NULL,
                                        status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, PROCESSING, COMPLETED, FAILED
                                        retry_count INTEGER NOT NULL DEFAULT 0,
                                        error_message TEXT,
                                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                        processed_at TIMESTAMP
);

CREATE INDEX idx_image_tasks_status ON image_processing_tasks(status, created_at);
CREATE INDEX idx_image_tasks_product ON image_processing_tasks(product_external_id);

-- Заказы для выгрузки в 1С (Kafka consumer копит, 1С забирает через mode=query)
CREATE TABLE pending_orders (
                                id BIGSERIAL PRIMARY KEY,
                                order_id BIGINT NOT NULL,
                                order_external_id VARCHAR(50),
                                order_data JSONB NOT NULL,                      -- данные заказа для формирования XML
                                exported BOOLEAN NOT NULL DEFAULT FALSE,         -- уже передан в 1С
                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                exported_at TIMESTAMP
);

CREATE INDEX idx_pending_orders_exported ON pending_orders(exported, created_at);
CREATE UNIQUE INDEX idx_pending_orders_order_id ON pending_orders(order_id);

-- Лог импортов для мониторинга и диагностики
CREATE TABLE import_log (
                            id BIGSERIAL PRIMARY KEY,
                            exchange_type VARCHAR(20) NOT NULL,              -- CATALOG, OFFERS, ORDER_STATUS
                            total_received INTEGER NOT NULL DEFAULT 0,
                            created INTEGER NOT NULL DEFAULT 0,
                            updated INTEGER NOT NULL DEFAULT 0,
                            failed INTEGER NOT NULL DEFAULT 0,
                            duration_ms BIGINT,
                            error_message TEXT,
                            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_import_log_type ON import_log(exchange_type, created_at);