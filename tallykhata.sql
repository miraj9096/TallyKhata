-- ==============================================================
-- TallyKhata MySQL Database Schema & Sample Seed Data
-- Target Endpoint: https://jabin24.com/api/tally_sync.php
-- Package: www.jabin24.aitally
-- Database: jabin24_tallykhata
-- Security: AES-256 E2EE Enabled
-- ==============================================================

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS `sync_logs`;
DROP TABLE IF EXISTS `transactions`;
DROP TABLE IF EXISTS `customers`;
DROP TABLE IF EXISTS `users`;
DROP VIEW IF EXISTS `view_customer_balances`;
SET FOREIGN_KEY_CHECKS = 1;

CREATE DATABASE IF NOT EXISTS `jabin24_tallykhata`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `jabin24_tallykhata`;

-- --------------------------------------------------------------
-- 1. Table structure for table `users`
-- --------------------------------------------------------------
CREATE TABLE `users` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `user_uid` VARCHAR(64) NOT NULL UNIQUE,
    `name` VARCHAR(120) NOT NULL,
    `shop_name` VARCHAR(150) NOT NULL,
    `email` VARCHAR(150) NOT NULL UNIQUE,
    `password_hash` VARCHAR(255) NOT NULL COMMENT 'Salted SHA-256 Hash',
    `phone` VARCHAR(30) NOT NULL,
    `is_email_verified` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '0=Unverified, 1=Verified via code',
    `verification_code` VARCHAR(10) NULL COMMENT '6-digit OTP code',
    `code_expires_at` DATETIME NULL COMMENT 'OTP Expiration timestamp',
    `auth_token` VARCHAR(128) NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_users_email` (`email`),
    INDEX `idx_users_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------------
-- 2. Table structure for table `customers`
-- --------------------------------------------------------------
CREATE TABLE `customers` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
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

-- --------------------------------------------------------------
-- 3. Table structure for table `transactions`
-- --------------------------------------------------------------
CREATE TABLE `transactions` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
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

-- --------------------------------------------------------------
-- 4. Table structure for table `sync_logs`
-- --------------------------------------------------------------
CREATE TABLE `sync_logs` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `user_uid` VARCHAR(64) NOT NULL,
    `entity_type` VARCHAR(30) NOT NULL,
    `entity_uid` VARCHAR(64) NOT NULL,
    `action` VARCHAR(50) NOT NULL,
    `ip_address` VARCHAR(45) NULL,
    `synced_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_sync_user` (`user_uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------------
-- 5. Analytical View: Customer Ledger Summary
-- --------------------------------------------------------------
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

-- ==============================================================
-- 6. SAMPLE SEED DATA
-- Default Test Store Account (Email: store@tallykhata.local)
-- Password: password123
-- SHA-256 Hash with salt TallyKhata_E2EE_jabin24_2026_Secure
-- ==============================================================

INSERT INTO `users` (`id`, `user_uid`, `name`, `shop_name`, `email`, `password_hash`, `phone`, `created_at`, `updated_at`) VALUES
(1, 'usr_bismillah_01', 'Bismillah Store', 'Bismillah General Store', 'store@tallykhata.local', '0a4e76816fa8a76d54cf8bf138676bf8624d785a8120d29757657ff138fb0e47', '01700000000', NOW(), NOW()),
(2, 'usr_jabin_demo', 'Jabin Demo Merchant', 'Jabin24 Wholesale Traders', 'merchant@jabin24.com', '0a4e76816fa8a76d54cf8bf138676bf8624d785a8120d29757657ff138fb0e47', '01811223344', NOW(), NOW());

-- Seed Customers
INSERT INTO `customers` (`id`, `customer_uid`, `user_uid`, `name`, `phone`, `address`, `encrypted_note`, `total_due`, `created_at`, `updated_at`) VALUES
(1, 'cust_demo_01', 'usr_bismillah_01', 'Rafiqul Islam', '01712345678', 'Dhanmondi, Dhaka', 'ENC:S1Q4L3NzVnlaVHRBQUFFRY9T5XG/jH2m0W6L3g==', 2450.00, NOW(), NOW()),
(2, 'cust_demo_02', 'usr_bismillah_01', 'Kazi Moniruzzaman', '01898765432', 'Chawkbazar, Chittagong', 'ENC:Zk9xVnlaVHRBQUFFR1Q4TR7F2kM8oP0Q3l1K9A==', 8700.00, NOW(), NOW()),
(3, 'cust_demo_03', 'usr_bismillah_01', 'Anika Rahman', '01911223344', 'Uttara Sector 7, Dhaka', 'ENC:TW54U3NzVnlaVHRBQUFFU82Y6lV1mB5H4x7P2Q==', 1200.00, NOW(), NOW()),
(4, 'cust_demo_04', 'usr_jabin_demo', 'Tariqul Alam', '01611335577', 'Agrabad, Chittagong', 'ENC:Qld6T3NzVnlaVHRBQUFFS64A9jK7lW3X5z0M1C==', 5400.00, NOW(), NOW());

-- Seed Transactions (Credit Given & Debit Received)
INSERT INTO `transactions` (`id`, `transaction_uid`, `customer_uid`, `user_uid`, `type`, `amount`, `encrypted_details`, `date_millis`, `due_date_millis`, `receipt_number`, `sync_status`, `created_at`) VALUES
(1, 'tx_demo_01', 'cust_demo_01', 'usr_bismillah_01', 'CREDIT_GIVEN', 3000.00, 'ENC:Rice, Cooking Oil, Spices (মুদি মালামাল)', 1730300000000, 1730900000000, 'TK-948102', 'SYNCED', NOW()),
(2, 'tx_demo_02', 'cust_demo_01', 'usr_bismillah_01', 'DEBIT_RECEIVED', 550.00, 'ENC:Cash payment by customer (নগদ জমা)', 1730380000000, NULL, 'TK-948103', 'SYNCED', NOW()),
(3, 'tx_demo_03', 'cust_demo_02', 'usr_bismillah_01', 'CREDIT_GIVEN', 8700.00, 'ENC:Invoice #402 Wholesale goods (বাকি ক্রয়)', 1730120000000, 1730720000000, 'TK-948104', 'SYNCED', NOW()),
(4, 'tx_demo_04', 'cust_demo_03', 'usr_bismillah_01', 'CREDIT_GIVEN', 1200.00, 'ENC:Stationery & Printing Paper (স্টেশনারি)', 1730240000000, 1730840000000, 'TK-948105', 'SYNCED', NOW());

-- Seed Initial Sync Log
INSERT INTO `sync_logs` (`id`, `user_uid`, `entity_type`, `entity_uid`, `action`, `ip_address`, `synced_at`) VALUES
(1, 'usr_bismillah_01', 'BATCH', 'cust:3;tx:4', 'INITIAL_IMPORT', '127.0.0.1', NOW());
