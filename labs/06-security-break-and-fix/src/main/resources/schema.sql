CREATE TABLE app_user (
    username      VARCHAR(50) PRIMARY KEY,
    display_name  VARCHAR(100) NOT NULL,
    role          VARCHAR(20) NOT NULL,
    password_hash VARCHAR(200)
);
CREATE TABLE account (
    id       BIGINT PRIMARY KEY,
    owner    VARCHAR(50) NOT NULL REFERENCES app_user (username),
    number   CHAR(10) NOT NULL,
    balance  DECIMAL(19, 2) NOT NULL
);
CREATE TABLE beneficiary (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner    VARCHAR(50) NOT NULL REFERENCES app_user (username),
    name     VARCHAR(100) NOT NULL,
    number   CHAR(10) NOT NULL
);
