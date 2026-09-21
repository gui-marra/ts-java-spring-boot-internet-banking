-- banking_core_fund_transfer_service.fund_transfer definition

CREATE TABLE `fund_transfer` (
    `id`                    bigint        NOT NULL AUTO_INCREMENT,
    `transaction_reference` varchar(255)  DEFAULT NULL,
    `from_account`          varchar(255)  DEFAULT NULL,
    `to_account`            varchar(255)  DEFAULT NULL,
    `amount`                decimal(38, 2) DEFAULT NULL,
    `status`                enum ('PENDING','PROCESSING','SUCCESS','FAILED') DEFAULT NULL,
    `created_date`          datetime(6)   DEFAULT NULL,
    `created_by`            varchar(255)  DEFAULT NULL,
    `modified_date`         datetime(6)   DEFAULT NULL,
    `modified_by`           varchar(255)  DEFAULT NULL,
    `version`               bigint        NOT NULL,
    PRIMARY KEY (`id`)
);
