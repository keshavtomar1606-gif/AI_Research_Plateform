-- ResearchAI database schema (MySQL 8).
-- The Spring Boot backend also creates/updates these tables automatically
-- (spring.jpa.hibernate.ddl-auto=update), so running this file is optional.
-- It is safe to run more than once.

CREATE DATABASE IF NOT EXISTS ai_research_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ai_research_platform;

CREATE TABLE IF NOT EXISTS research_project (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    title       VARCHAR(255) NOT NULL,
    description VARCHAR(2000),
    team        VARCHAR(255),
    status      VARCHAR(255),
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS dataset (
    id           BIGINT NOT NULL AUTO_INCREMENT,
    name         VARCHAR(255) NOT NULL,
    description  VARCHAR(2000),
    owner        VARCHAR(255),
    file_name    VARCHAR(255),
    status       VARCHAR(255),
    csv_content  LONGTEXT,          -- uploaded CSV text (max 2 MB), NULL for metadata-only datasets
    column_names VARCHAR(2000),     -- tab separated header names
    row_count    INT,
    size_bytes   BIGINT,
    created_at   DATETIME(6),
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS training_job (
    id                BIGINT NOT NULL AUTO_INCREMENT,
    job_type          VARCHAR(255),   -- ML_TRAINING (real run) or RECORD (note only)
    model             VARCHAR(255),
    dataset           VARCHAR(255),
    parameters        VARCHAR(255),
    status            VARCHAR(255),   -- Queued, Running, Completed, Failed, Record only
    progress          INT,
    dataset_id        BIGINT,
    target_column     VARCHAR(255),
    feature_columns   VARCHAR(2000),
    test_ratio        DOUBLE,
    random_seed       BIGINT,
    train_rows        INT,
    test_rows         INT,
    dropped_rows      INT,
    mae               DOUBLE,
    rmse              DOUBLE,
    r2                DOUBLE,
    train_r2          DOUBLE,
    baseline_mae      DOUBLE,
    coefficients_json VARCHAR(4000),
    error_message     VARCHAR(1000),
    created_at        DATETIME(6),
    started_at        DATETIME(6),
    finished_at       DATETIME(6),
    PRIMARY KEY (id)
);
