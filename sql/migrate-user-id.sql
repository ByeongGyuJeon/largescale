-- For an existing test table created with user_id BIGINT.
-- Existing numeric IDs become numeric strings; reload source CSV for prefixed IDs.
USE largescale;
ALTER TABLE events MODIFY COLUMN user_id VARCHAR(100) NOT NULL;
