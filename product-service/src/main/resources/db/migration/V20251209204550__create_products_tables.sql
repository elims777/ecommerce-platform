CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    parent_id BIGINT REFERENCES categories(id) ON DELETE SET NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_categories_slug ON categories(slug);

CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    short_description VARCHAR(1000),
    price DECIMAL(10, 2) CHECK (price>=0 or price IS NULL),
    stock_quantity INTEGER NOT NULL DEFAULT 0 CHECK (stock_quantity>=0),
    category_id BIGINT REFERENCES categories(id) ON DELETE SET NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    is_featured BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_products_slug ON products(slug);
CREATE INDEX idx_products_category ON products(category_id);

CREATE TABLE product_images (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    file_key VARCHAR(500) NOT NULL,
    file_url VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    width INTEGER,
    height INTEGER,
    is_primary BOOLEAN NOT NULL DEFAULT false,
    display_order INTEGER NOT NULL DEFAULT 0,
    alt_text VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_product_images_product ON product_images(product_id);
CREATE INDEX idx_product_images_primary ON product_images(product_id, is_primary);
CREATE INDEX idx_product_images_order ON product_images(product_id, display_order);


CREATE TABLE product_attributes (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    attribute_name VARCHAR(100) NOT NULL,
    attribute_value VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_product_attributes_product ON product_attributes(product_id);
CREATE INDEX idx_product_attributes_name ON product_attributes(attribute_name);


CREATE TABLE product_videos (
                                id BIGSERIAL PRIMARY KEY,
                                product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
                                video_url VARCHAR(500) NOT NULL,
                                video_type VARCHAR(50),
                                provider VARCHAR(50),
                                thumbnail_url VARCHAR(500),
                                duration_seconds INTEGER,
                                is_primary BOOLEAN NOT NULL DEFAULT false,
                                display_order INTEGER NOT NULL DEFAULT 0,
                                title VARCHAR(255),
                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_product_videos_product ON product_videos(product_id);
CREATE INDEX idx_product_videos_primary ON product_videos(product_id, is_primary);