USE largescale;
SELECT COUNT(*) AS row_count, MIN(id) AS min_id, MAX(id) AS max_id, SUM(value) AS value_sum FROM events;
SELECT id, user_id, event, value, payload FROM events ORDER BY id LIMIT 10;
