CREATE DATABASE IF NOT EXISTS largescale CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE largescale;
-- Initial type assumptions; adapt these to the real CSV before production data loading.
CREATE TABLE IF NOT EXISTS events (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    event VARCHAR(100) NOT NULL,
    value DECIMAL(20,6) NULL,
    payload TEXT NULL
) ENGINE=InnoDB;
