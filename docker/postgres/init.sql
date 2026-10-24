-- Create the application database and required extensions only.
-- JPA/schema.sql own the application schema; no sample records or passwords.
CREATE DATABASE app_db;
\connect app_db
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
