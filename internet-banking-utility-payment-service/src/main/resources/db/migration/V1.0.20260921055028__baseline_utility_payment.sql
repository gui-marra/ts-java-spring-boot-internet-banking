CREATE TABLE utility_payment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account VARCHAR(255),
    amount DECIMAL(38,2),
    provider_id BIGINT,
    reference_number VARCHAR(255),
    transaction_id VARCHAR(255),
    status ENUM ('PENDING','PROCESSING','SUCCESS','FAILED'),
    created_by VARCHAR(255),
    created_date DATETIME(6),
    modified_by VARCHAR(255),
    modified_date DATETIME(6),
    version BIGINT NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB;
