-- One PostgreSQL server for local runs, one database and one user per service (topic 10.4).
-- Development passwords only: real ones come from a secrets manager (Module 12).
CREATE USER transfers WITH PASSWORD 'transfers';
CREATE DATABASE transfers OWNER transfers;
CREATE USER notifications WITH PASSWORD 'notifications';
CREATE DATABASE notifications OWNER notifications;
CREATE USER interest WITH PASSWORD 'interest';
CREATE DATABASE interest OWNER interest;
CREATE USER rates WITH PASSWORD 'rates';
CREATE DATABASE rates OWNER rates;
CREATE USER assistant WITH PASSWORD 'assistant';
CREATE DATABASE assistant OWNER assistant;
-- pgvector and the extensions Spring AI's vector store uses; creating extensions needs the superuser (topic 15.2)
\connect assistant
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
