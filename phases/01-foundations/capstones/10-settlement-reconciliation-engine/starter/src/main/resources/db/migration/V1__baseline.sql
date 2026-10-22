-- Baseline migration. Replace with your first real tables (topic 3.4).
-- Never edit a migration after it has run in a shared environment: add V2, V3, ...
CREATE TABLE IF NOT EXISTS app_info (
    key   VARCHAR(50)  PRIMARY KEY,
    value VARCHAR(200) NOT NULL
);
INSERT INTO app_info (key, value) VALUES ('schema', 'baseline');
