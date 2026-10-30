-- One PostgreSQL server for local runs, one database and one user per service (topic 10.4).
-- Development passwords only: real ones come from a secrets manager (Module 12).
CREATE USER transfers WITH PASSWORD 'transfers';
CREATE DATABASE transfers OWNER transfers;
CREATE USER notifications WITH PASSWORD 'notifications';
CREATE DATABASE notifications OWNER notifications;
CREATE USER interest WITH PASSWORD 'interest';
CREATE DATABASE interest OWNER interest;
