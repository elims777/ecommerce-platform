ALTER TABLE import_log
    ADD COLUMN images_processed int NOT NULL DEFAULT 0,
    ADD COLUMN images_failed    int NOT NULL DEFAULT 0;
