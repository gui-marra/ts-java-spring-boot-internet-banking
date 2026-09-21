CREATE TABLE `user` (
    `id`             bigint       NOT NULL AUTO_INCREMENT,
    `created_by`     varchar(255) DEFAULT NULL,
    `created_date`   datetime(6)  DEFAULT NULL,
    `modified_by`    varchar(255) DEFAULT NULL,
    `modified_date`  datetime(6)  DEFAULT NULL,
    `version`        bigint       NOT NULL,
    `auth_id`        varchar(255) DEFAULT NULL,
    `identification` varchar(255) DEFAULT NULL,
    `status`         enum('PENDING','APPROVED','DISABLED','BLACKLIST') DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
