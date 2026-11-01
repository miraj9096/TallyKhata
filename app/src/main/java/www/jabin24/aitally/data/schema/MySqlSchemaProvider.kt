package www.jabin24.aitally.data.schema

/**
 * Provides the MySQL database schema, sample seed data, and PHP backend script
 * for jabin24.com/api/tally_sync.php.
 */
object MySqlSchemaProvider {

    val MYSQL_SCHEMA_SQL: String = """
-- ==============================================================
-- TallyKhata Database Schema & Sample Data (MySQL 5.7+ / 8.0)
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

-- 1. Users & Store Accounts Table
CREATE TABLE `users` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
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

-- 3. Credit / Debit Transactions Table
CREATE TABLE `transactions` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `transaction_uid` VARCHAR(64) NOT NULL UNIQUE,
    `customer_uid` VARCHAR(64) NOT NULL,
    `user_uid` VARCHAR(64) NOT NULL,
    `type` ENUM('CREDIT_GIVEN', 'DEBIT_RECEIVED') NOT NULL,
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

-- 5. Analytical View: Customer Ledger Summary
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

-- 6. Sample Seed Data
INSERT INTO `users` (`id`, `user_uid`, `name`, `shop_name`, `email`, `password_hash`, `phone`, `created_at`, `updated_at`) VALUES
(1, 'usr_bismillah_01', 'Bismillah Store', 'Bismillah General Store', 'store@tallykhata.local', '0a4e76816fa8a76d54cf8bf138676bf8624d785a8120d29757657ff138fb0e47', '01700000000', NOW(), NOW()),
(2, 'usr_jabin_demo', 'Jabin Demo Merchant', 'Jabin24 Wholesale Traders', 'merchant@jabin24.com', '0a4e76816fa8a76d54cf8bf138676bf8624d785a8120d29757657ff138fb0e47', '01811223344', NOW(), NOW());

INSERT INTO `customers` (`id`, `customer_uid`, `user_uid`, `name`, `phone`, `address`, `encrypted_note`, `total_due`, `created_at`, `updated_at`) VALUES
(1, 'cust_demo_01', 'usr_bismillah_01', 'Rafiqul Islam', '01712345678', 'Dhanmondi, Dhaka', 'ENC:S1Q4L3NzVnlaVHRBQUFFRY9T5XG/jH2m0W6L3g==', 2450.00, NOW(), NOW()),
(2, 'cust_demo_02', 'usr_bismillah_01', 'Kazi Moniruzzaman', '01898765432', 'Chawkbazar, Chittagong', 'ENC:Zk9xVnlaVHRBQUFFR1Q4TR7F2kM8oP0Q3l1K9A==', 8700.00, NOW(), NOW()),
(3, 'cust_demo_03', 'usr_bismillah_01', 'Anika Rahman', '01911223344', 'Uttara Sector 7, Dhaka', 'ENC:TW54U3NzVnlaVHRBQUFFU82Y6lV1mB5H4x7P2Q==', 1200.00, NOW(), NOW());

INSERT INTO `transactions` (`id`, `transaction_uid`, `customer_uid`, `user_uid`, `type`, `amount`, `encrypted_details`, `date_millis`, `due_date_millis`, `receipt_number`, `sync_status`, `created_at`) VALUES
(1, 'tx_demo_01', 'cust_demo_01', 'usr_bismillah_01', 'CREDIT_GIVEN', 3000.00, 'ENC:Rice, Cooking Oil, Spices', 1730300000000, 1730900000000, 'TK-948102', 'SYNCED', NOW()),
(2, 'tx_demo_02', 'cust_demo_01', 'usr_bismillah_01', 'DEBIT_RECEIVED', 550.00, 'ENC:Cash payment by customer', 1730380000000, NULL, 'TK-948103', 'SYNCED', NOW()),
(3, 'tx_demo_03', 'cust_demo_02', 'usr_bismillah_01', 'CREDIT_GIVEN', 8700.00, 'ENC:Invoice #402 Wholesale goods', 1730120000000, 1730720000000, 'TK-948104', 'SYNCED', NOW()),
(4, 'tx_demo_04', 'cust_demo_03', 'usr_bismillah_01', 'CREDIT_GIVEN', 1200.00, 'ENC:Stationery & Printing Paper', 1730240000000, 1730840000000, 'TK-948105', 'SYNCED', NOW());
""".trimIndent()

    val SAMPLE_PHP_API_CODE: String = """
<?php
/**
 * TallyKhata Cloud Synchronization API Backend
 * Target Endpoint: https://jabin24.com/api/tally_sync.php
 * Package: www.jabin24.aitally
 */
error_reporting(0);
ini_set('display_errors', '0');

header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization, X-Requested-With');

if (${'$'}_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

${'$'}db_host = getenv('DB_HOST') ?: 'localhost';
${'$'}db_name = getenv('DB_NAME') ?: 'jabin24_tallykhata';
${'$'}db_user = getenv('DB_USER') ?: 'jabin24_tallyuser';
${'$'}db_pass = getenv('DB_PASS') ?: 'SecureTally2026!Pass';
${'$'}db_port = getenv('DB_PORT') ?: '3306';

try {
    ${'$'}dsn = "mysql:host={${'$'}db_host};port={${'$'}db_port};dbname={${'$'}db_name};charset=utf8mb4";
    ${'$'}pdo = new PDO(${'$'}dsn, ${'$'}db_user, ${'$'}db_pass, [
        PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES   => false,
    ]);
} catch (PDOException ${'$'}e) {
    http_response_code(500);
    echo json_encode(['status' => 'error', 'message' => 'Database connection failed: ' . ${'$'}e->getMessage()]);
    exit;
}

${'$'}rawInput = file_get_contents('php://input');
${'$'}requestData = json_decode(${'$'}rawInput, true) ?: array_merge(${'$'}_GET, ${'$'}_POST);
${'$'}action = ${'$'}requestData['action'] ?? ${'$'}_GET['action'] ?? '';

switch (${'$'}action) {
    case 'register':
        ${'$'}userUid      = trim(${'$'}requestData['user_uid'] ?? '');
        ${'$'}name         = trim(${'$'}requestData['name'] ?? '');
        ${'$'}shopName     = trim(${'$'}requestData['shop_name'] ?? '');
        ${'$'}email        = strtolower(trim(${'$'}requestData['email'] ?? ''));
        ${'$'}passwordHash = trim(${'$'}requestData['password_hash'] ?? '');
        ${'$'}phone        = trim(${'$'}requestData['phone'] ?? '');

        if (empty(${'$'}email) || empty(${'$'}passwordHash)) {
            http_response_code(400);
            echo json_encode(['status' => 'error', 'message' => 'Email and password are required.']);
            exit;
        }

        if (empty(${'$'}userUid)) {
            ${'$'}userUid = 'usr_' . bin2hex(random_bytes(8));
        }

        ${'$'}stmt = ${'$'}pdo->prepare("
            INSERT INTO users (user_uid, name, shop_name, email, password_hash, phone, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
            ON DUPLICATE KEY UPDATE name=VALUES(name), shop_name=VALUES(shop_name), phone=VALUES(phone), updated_at=NOW()
        ");
        ${'$'}stmt->execute([${'$'}userUid, ${'$'}name, ${'$'}shopName, ${'$'}email, ${'$'}passwordHash, ${'$'}phone]);

        echo json_encode(['status' => 'success', 'message' => 'User registered successfully', 'user_uid' => ${'$'}userUid]);
        break;

    case 'login':
        ${'$'}email        = strtolower(trim(${'$'}requestData['email'] ?? ''));
        ${'$'}passwordHash = trim(${'$'}requestData['password_hash'] ?? '');

        ${'$'}stmt = ${'$'}pdo->prepare("SELECT user_uid, name, shop_name, email, phone, password_hash FROM users WHERE email = ? LIMIT 1");
        ${'$'}stmt->execute([${'$'}email]);
        ${'$'}user = ${'$'}stmt->fetch();

        if (${'$'}user && (${'$'}user['password_hash'] === ${'$'}passwordHash)) {
            unset(${'$'}user['password_hash']);
            echo json_encode(['status' => 'success', 'user' => ${'$'}user]);
        } else {
            http_response_code(401);
            echo json_encode(['status' => 'error', 'message' => 'Invalid email or password']);
        }
        break;

    case 'sync':
        ${'$'}userUid      = trim(${'$'}requestData['user_uid'] ?? '');
        ${'$'}customers    = ${'$'}requestData['customers'] ?? [];
        ${'$'}transactions = ${'$'}requestData['transactions'] ?? [];

        ${'$'}pdo->beginTransaction();

        ${'$'}custStmt = ${'$'}pdo->prepare("
            INSERT INTO customers (customer_uid, user_uid, name, phone, address, encrypted_note, total_due, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
            ON DUPLICATE KEY UPDATE name=VALUES(name), phone=VALUES(phone), address=VALUES(address), total_due=VALUES(total_due), encrypted_note=VALUES(encrypted_note), updated_at=NOW()
        ");
        foreach (${'$'}customers as ${'$'}c) {
            ${'$'}custStmt->execute([${'$'}c['customer_uid'], ${'$'}userUid, ${'$'}c['name'], ${'$'}c['phone'], ${'$'}c['address'] ?? '', ${'$'}c['encrypted_note'] ?? '', ${'$'}c['total_due'] ?? 0]);
        }

        ${'$'}txStmt = ${'$'}pdo->prepare("
            INSERT INTO transactions (transaction_uid, customer_uid, user_uid, type, amount, encrypted_details, date_millis, due_date_millis, receipt_number, sync_status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'SYNCED')
            ON DUPLICATE KEY UPDATE amount=VALUES(amount), encrypted_details=VALUES(encrypted_details), sync_status='SYNCED'
        ");
        foreach (${'$'}transactions as ${'$'}t) {
            ${'$'}txStmt->execute([${'$'}t['transaction_uid'], ${'$'}t['customer_uid'], ${'$'}userUid, ${'$'}t['type'], ${'$'}t['amount'], ${'$'}t['encrypted_details'] ?? '', ${'$'}t['date_millis'], ${'$'}t['due_date_millis'] ?? null, ${'$'}t['receipt_number'] ?? '']);
        }

        ${'$'}pdo->commit();
        echo json_encode([
            'status'       => 'success',
            'items_synced' => count(${'$'}customers) + count(${'$'}transactions),
            'synced_at'    => round(microtime(true) * 1000)
        ]);
        break;

    case 'pull':
        ${'$'}userUid = trim(${'$'}requestData['user_uid'] ?? ${'$'}_GET['user_uid'] ?? '');
        ${'$'}cStmt = ${'$'}pdo->prepare("SELECT * FROM customers WHERE user_uid = ?");
        ${'$'}cStmt->execute([${'$'}userUid]);
        ${'$'}customers = ${'$'}cStmt->fetchAll();

        ${'$'}tStmt = ${'$'}pdo->prepare("SELECT * FROM transactions WHERE user_uid = ?");
        ${'$'}tStmt->execute([${'$'}userUid]);
        ${'$'}transactions = ${'$'}tStmt->fetchAll();

        echo json_encode(['status' => 'success', 'customers' => ${'$'}customers, 'transactions' => ${'$'}transactions]);
        break;

    default:
        echo json_encode([
            'status'   => 'ok',
            'service'  => 'TallyKhata Cloud Sync Engine',
            'endpoint' => 'https://jabin24.com/api/tally_sync.php'
        ]);
        break;
}
""".trimIndent()
}
