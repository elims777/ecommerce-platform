-- Новости: заголовок, обложка в S3, ссылка на видео (эмбед), HTML-текст из редактора.
-- content_html — TEXT: варианта varchar(255) достаточно для заголовка, но не для статьи.
CREATE TABLE news (
    id              BIGSERIAL PRIMARY KEY,
    title           VARCHAR(255) NOT NULL,
    slug            VARCHAR(255) NOT NULL UNIQUE,
    cover_image_url TEXT,
    video_url       TEXT,
    content_html    TEXT         NOT NULL,
    is_published    BOOLEAN      NOT NULL DEFAULT FALSE,
    published_at    TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Выборка последних опубликованных для блока на главной и списка /news
CREATE INDEX idx_news_published ON news (is_published, published_at DESC);