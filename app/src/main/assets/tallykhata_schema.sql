-- ==============================================================
-- TallyKhata Database Schema (MySQL 5.7+ / 8.0 / MariaDB)
-- Sync API: https://jabin24.com/api/tally_sync.php
-- Package: www.jabin24.aitally
-- Security: AES-256 E2EE Enabled
-- ==============================================================

CREATE DATABASE IF NOT EXISTS `jabin24_tallykhata` 
  DEFAULT CHARACTER SET utf8mb4 
  COLLATE utf8mb4_unicode_ci;

USE `jabin24_tallykhata`;

-- 1. Users & Store Accounts Table
CREATE TABLE IF NOT EXISTS `users` (
    `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    `user_uid` VARCHAR(64) NOT NULL UNIQUE,
    `name` VARCHAR(120) NOT NULL,
    `shop_name` VARCHAR(150) NOT NULL,
    `email` VARCHAR(150) NOT NULL UNIQUE,
    `password_hash` VARCHAR(255) NOT NULL,
    `phone` VARCHAR(30) NOT NULL,
    `auth_token` VARCHAR(128) NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_users_email` (`email`),
    INDEX `idx_users_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Khata Customers Table (Track Debtors & Creditors)
CREATE TABLE IF NOT EXISTS `customers` (
    `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    `customer_uid` VARCHAR(64) NOT NULL UNIQUE,
    `user_uid` VARCHAR(64) NOT NULL,
    `name` VARCHAR(150) NOT NULL,
    `phone` VARCHAR(30) NOT NULL,
    `address` TEXT NULL,
    `encrypted_note` TEXT NULL COMMENT 'AES-256-GCM Encrypted sensitive note',
    `total_due` DECIMAL(14,2) NOT NULL DEFAULT 0.00 COMMENT 'Positive = Customer Owes Store (Baki)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_cust_user` (`user_uid`),
    INDEX `idx_cust_phone` (`phone`),
    CONSTRAINT `fk_cust_user` FOREIGN KEY (`user_uid`) REFERENCES `users` (`user_uid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Credit / Debit Transactions Table
CREATE TABLE IF NOT EXISTS `transactions` (
    `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    `transaction_uid` VARCHAR(64) NOT NULL UNIQUE,
    `customer_uid` VARCHAR(64) NOT NULL,
    `user_uid` VARCHAR(64) NOT NULL,
    `type` ENUM('CREDIT_GIVEN', 'DEBIT_RECEIVED') NOT NULL COMMENT 'CREDIT_GIVEN=Baki, DEBIT_RECEIVED=Taka Pawa',
    `amount` DECIMAL(14,2) NOT NULL,
    `encrypted_details` TEXT NULL COMMENT 'AES-256-GCM Encrypted item memo/description',
    `date_millis` BIGINT NOT NULL,
    `due_date_millis` BIGINT NULL,
    `receipt_number` VARCHAR(64) NULL,
    `sync_status` VARCHAR(20) NOT NULL DEFAULT 'SYNCED',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_tx_customer` (`customer_uid`),
    INDEX `idx_tx_user` (`user_uid`),
    INDEX `idx_tx_date` (`date_millis`),
    CONSTRAINT `fk_tx_customer` FOREIGN KEY (`customer_uid`) REFERENCES `customers` (`customer_uid`) ON DELETE CASCADE,
    CONSTRAINT `fk_tx_user` FOREIGN KEY (`user_uid`) REFERENCES `users` (`user_uid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Sync Queue & Activity Audit Log Table
CREATE TABLE IF NOT EXISTS `sync_logs` (
    `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    `user_uid` VARCHAR(64) NOT NULL,
    `entity_type` VARCHAR(30) NOT NULL,
    `entity_uid` VARCHAR(64) NOT NULL,
    `action` ENUM('INSERT', 'UPDATE', 'DELETE') NOT NULL,
    `ip_address` VARCHAR(45) NULL,
    `synced_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_sync_user` (`user_uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Useful Analytical View: Customer Ledger Summary
CREATE OR REPLACE VIEW `view_customer_balances` AS
SELECT 
    c.customer_uid,
    c.user_uid,
    c.name AS customer_name,
    c.phone AS customer_phone,
    COALESCE(SUM(CASE WHEN t.type = 'CREDIT_GIVEN' THEN t.amount ELSE 0 END), 0) AS total_credit_given,
    COALESCE(SUM(CASE WHEN t.type = 'DEBIT_RECEIVED' THEN t.amount ELSE 0 END), 0) AS total_payment_received,
    (COALESCE(SUM(CASE WHEN t.type = 'CREDIT_GIVEN' THEN t.amount ELSE 0 END), 0) - 
     COALESCE(SUM(CASE WHEN t.type = 'DEBIT_RECEIVED' THEN t.amount ELSE 0 END), 0)) AS calculated_due
FROM `customers` c
LEFT JOIN `transactions` t ON c.customer_uid = t.customer_uid
GROUP BY c.customer_uid, c.user_uid, c.name, c.phone;
