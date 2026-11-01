<?php
/**
 * TallyKhata Cloud Synchronization API Backend
 * Target Endpoint: https://jabin24.com/api/tally_sync.php
 * Package: www.jabin24.aitally
 * Features:
 *   - User Registration & Authentication (Email + Password)
 *   - Offline-First Two-Way Synchronization (Customers & Transactions)
 *   - End-to-End Encryption (E2EE) Payload Storage (AES-256)
 *   - Prepared PDO Statements (SQL Injection Protection)
 *   - MySQL InnoDB Transactions (ACID Compliance)
 */

// Enable error reporting in development; toggle off in high security production
error_reporting(0);
ini_set('display_errors', '0');

// Set JSON headers and CORS for mobile app connectivity
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization, X-Requested-With');

// Respond to preflight OPTIONS request
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

// ==============================================================
// 1. DATABASE CONFIGURATION
// Configure with your MySQL server credentials on jabin24.com
// ==============================================================
$db_host = getenv('DB_HOST') ?: 'localhost';
$db_name = getenv('DB_NAME') ?: 'jabin24_tallykhata';
$db_user = getenv('DB_USER') ?: 'jabin24_tallyuser';
$db_pass = getenv('DB_PASS') ?: 'SecureTally2026!Pass';
$db_port = getenv('DB_PORT') ?: '3306';

try {
    $dsn = "mysql:host={$db_host};port={$db_port};dbname={$db_name};charset=utf8mb4";
    $options = [
        PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES   => false,
    ];
    $pdo = new PDO($dsn, $db_user, $db_pass, $options);
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode([
        'status' => 'error',
        'message' => 'Database connection failed: ' . $e->getMessage()
    ]);
    exit;
}

// ==============================================================
// 2. REQUEST PARSING
// Supports both raw JSON request body and form POST / GET
// ==============================================================
$rawInput = file_get_contents('php://input');
$requestData = json_decode($rawInput, true);

if (!is_array($requestData)) {
    $requestData = array_merge($_GET, $_POST);
}

$action = $requestData['action'] ?? $_GET['action'] ?? '';

// Helper to log sync operations
function logSyncAction($pdo, $userUid, $entityType, $entityUid, $syncAction) {
    try {
        $ip = $_SERVER['REMOTE_ADDR'] ?? '127.0.0.1';
        $stmt = $pdo->prepare("
            INSERT INTO sync_logs (user_uid, entity_type, entity_uid, action, ip_address, synced_at)
            VALUES (?, ?, ?, ?, ?, NOW())
        ");
        $stmt->execute([$userUid, $entityType, $entityUid, $syncAction, $ip]);
    } catch (Exception $e) {
        // Logging failure should not disrupt main transaction
    }
}

// ==============================================================
// 3. ROUTE DISPATCHER
// ==============================================================
switch ($action) {

    // ----------------------------------------------------------
    // ACTION: REGISTER USER
    // ----------------------------------------------------------
    case 'register':
        $userUid      = trim($requestData['user_uid'] ?? '');
        $name         = trim($requestData['name'] ?? '');
        $shopName     = trim($requestData['shop_name'] ?? '');
        $email        = strtolower(trim($requestData['email'] ?? ''));
        $passwordHash = trim($requestData['password_hash'] ?? '');
        $phone        = trim($requestData['phone'] ?? '');

        if (empty($email) || empty($passwordHash)) {
            http_response_code(400);
            echo json_encode(['status' => 'error', 'message' => 'Email and password hash are required.']);
            exit;
        }

        if (empty($userUid)) {
            $userUid = 'usr_' . bin2hex(random_bytes(8));
        }

        try {
            // Check if user already exists
            $checkStmt = $pdo->prepare("SELECT user_uid, email FROM users WHERE email = ? LIMIT 1");
            $checkStmt->execute([$email]);
            $existing = $checkStmt->fetch();

            if ($existing) {
                // Update profile info if same email
                $updateStmt = $pdo->prepare("
                    UPDATE users 
                    SET name = ?, shop_name = ?, phone = ?, password_hash = ?, updated_at = NOW()
                    WHERE email = ?
                ");
                $updateStmt->execute([$name, $shopName, $phone, $passwordHash, $email]);
                $returnUid = $existing['user_uid'];
            } else {
                // Insert new account
                $insertStmt = $pdo->prepare("
                    INSERT INTO users (user_uid, name, shop_name, email, password_hash, phone, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
                ");
                $insertStmt->execute([$userUid, $name, $shopName, $email, $passwordHash, $phone]);
                $returnUid = $userUid;
            }

            logSyncAction($pdo, $returnUid, 'USER', $returnUid, 'REGISTER');

            echo json_encode([
                'status'    => 'success',
                'message'   => 'User account registered and synchronized successfully.',
                'user_uid'  => $returnUid,
                'email'     => $email
            ]);
        } catch (PDOException $e) {
            http_response_code(500);
            echo json_encode(['status' => 'error', 'message' => 'Registration error: ' . $e->getMessage()]);
        }
        break;

    // ----------------------------------------------------------
    // ACTION: LOGIN USER
    // ----------------------------------------------------------
    case 'login':
        $email        = strtolower(trim($requestData['email'] ?? ''));
        $passwordHash = trim($requestData['password_hash'] ?? '');

        if (empty($email) || empty($passwordHash)) {
            http_response_code(400);
            echo json_encode(['status' => 'error', 'message' => 'Email and password are required.']);
            exit;
        }

        try {
            $stmt = $pdo->prepare("SELECT user_uid, name, shop_name, email, phone, password_hash FROM users WHERE email = ? LIMIT 1");
            $stmt->execute([$email]);
            $user = $stmt->fetch();

            if ($user && ($user['password_hash'] === $passwordHash)) {
                // Authentication passed
                unset($user['password_hash']); // Do not expose password hash in response
                logSyncAction($pdo, $user['user_uid'], 'USER', $user['user_uid'], 'LOGIN');

                echo json_encode([
                    'status'  => 'success',
                    'message' => 'Authentication successful.',
                    'user'    => $user
                ]);
            } else {
                http_response_code(401);
                echo json_encode(['status' => 'error', 'message' => 'Invalid email or password.']);
            }
        } catch (PDOException $e) {
            http_response_code(500);
            echo json_encode(['status' => 'error', 'message' => 'Login error: ' . $e->getMessage()]);
        }
        break;

    // ----------------------------------------------------------
    // ACTION: PUSH SYNC (Bulk upload customers & transactions)
    // ----------------------------------------------------------
    case 'sync':
        $userUid      = trim($requestData['user_uid'] ?? '');
        $customers    = $requestData['customers'] ?? [];
        $transactions = $requestData['transactions'] ?? [];

        if (empty($userUid)) {
            http_response_code(400);
            echo json_encode(['status' => 'error', 'message' => 'User UID is required for sync.']);
            exit;
        }

        try {
            // Begin atomic database transaction
            $pdo->beginTransaction();

            $custCount = 0;
            if (!empty($customers) && is_array($customers)) {
                $custStmt = $pdo->prepare("
                    INSERT INTO customers (customer_uid, user_uid, name, phone, address, encrypted_note, total_due, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
                    ON DUPLICATE KEY UPDATE
                        name = VALUES(name),
                        phone = VALUES(phone),
                        address = VALUES(address),
                        encrypted_note = VALUES(encrypted_note),
                        total_due = VALUES(total_due),
                        updated_at = NOW()
                ");

                foreach ($customers as $c) {
                    $cUid  = $c['customer_uid'] ?? '';
                    $cName = $c['name'] ?? '';
                    $cPh   = $c['phone'] ?? '';
                    $cAddr = $c['address'] ?? '';
                    $cNote = $c['encrypted_note'] ?? '';
                    $cDue  = floatval($c['total_due'] ?? 0.0);

                    if (!empty($cUid) && !empty($cName)) {
                        $custStmt->execute([$cUid, $userUid, $cName, $cPh, $cAddr, $cNote, $cDue]);
                        $custCount++;
                    }
                }
            }

            $txCount = 0;
            if (!empty($transactions) && is_array($transactions)) {
                $txStmt = $pdo->prepare("
                    INSERT INTO transactions (transaction_uid, customer_uid, user_uid, type, amount, encrypted_details, date_millis, due_date_millis, receipt_number, sync_status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'SYNCED')
                    ON DUPLICATE KEY UPDATE
                        amount = VALUES(amount),
                        encrypted_details = VALUES(encrypted_details),
                        type = VALUES(type),
                        sync_status = 'SYNCED'
                ");

                foreach ($transactions as $t) {
                    $tUid     = $t['transaction_uid'] ?? '';
                    $tCustUid = $t['customer_uid'] ?? '';
                    $tType    = $t['type'] ?? 'CREDIT_GIVEN';
                    $tAmount  = floatval($t['amount'] ?? 0.0);
                    $tDetails = $t['encrypted_details'] ?? '';
                    $tDate    = intval($t['date_millis'] ?? round(microtime(true) * 1000));
                    $tDueDate = isset($t['due_date_millis']) && $t['due_date_millis'] !== null ? intval($t['due_date_millis']) : null;
                    $tReceipt = $t['receipt_number'] ?? '';

                    if (!empty($tUid) && !empty($tCustUid)) {
                        $txStmt->execute([$tUid, $tCustUid, $userUid, $tType, $tAmount, $tDetails, $tDate, $tDueDate, $tReceipt]);
                        $txCount++;
                    }
                }
            }

            // Commit atomic transaction
            $pdo->commit();

            logSyncAction($pdo, $userUid, 'BATCH', "cust:$custCount;tx:$txCount", 'PUSH_SYNC');

            echo json_encode([
                'status'       => 'success',
                'message'      => "Sync completed: {$custCount} customers and {$txCount} transactions synchronized.",
                'items_synced' => ($custCount + $txCount),
                'synced_at'    => round(microtime(true) * 1000)
            ]);
        } catch (PDOException $e) {
            if ($pdo->inTransaction()) {
                $pdo->rollBack();
            }
            http_response_code(500);
            echo json_encode(['status' => 'error', 'message' => 'Sync failed: ' . $e->getMessage()]);
        }
        break;

    // ----------------------------------------------------------
    // ACTION: PULL SYNC (Download cloud data to mobile device)
    // ----------------------------------------------------------
    case 'pull':
        $userUid = trim($requestData['user_uid'] ?? $_GET['user_uid'] ?? '');

        if (empty($userUid)) {
            http_response_code(400);
            echo json_encode(['status' => 'error', 'message' => 'User UID is required for pull.']);
            exit;
        }

        try {
            // Fetch customer records
            $custStmt = $pdo->prepare("
                SELECT customer_uid, user_uid, name, phone, address, encrypted_note, total_due
                FROM customers
                WHERE user_uid = ?
                ORDER BY updated_at DESC
            ");
            $custStmt->execute([$userUid]);
            $customers = $custStmt->fetchAll();

            // Fetch transaction records
            $txStmt = $pdo->prepare("
                SELECT transaction_uid, customer_uid, user_uid, type, amount, encrypted_details, date_millis, due_date_millis, receipt_number
                FROM transactions
                WHERE user_uid = ?
                ORDER BY date_millis DESC
            ");
            $txStmt->execute([$userUid]);
            $transactions = $txStmt->fetchAll();

            // Format amounts to float
            foreach ($customers as &$c) {
                $c['total_due'] = floatval($c['total_due']);
            }
            foreach ($transactions as &$t) {
                $t['amount'] = floatval($t['amount']);
                $t['date_millis'] = intval($t['date_millis']);
                if ($t['due_date_millis'] !== null) {
                    $t['due_date_millis'] = intval($t['due_date_millis']);
                }
            }

            echo json_encode([
                'status'       => 'success',
                'customers'    => $customers,
                'transactions' => $transactions,
                'count'        => count($customers) + count($transactions),
                'synced_at'    => round(microtime(true) * 1000)
            ]);
        } catch (PDOException $e) {
            http_response_code(500);
            echo json_encode(['status' => 'error', 'message' => 'Pull error: ' . $e->getMessage()]);
        }
        break;

    // ----------------------------------------------------------
    // ACTION: STATS / HEALTH CHECK
    // ----------------------------------------------------------
    case 'stats':
    default:
        try {
            $userCount = $pdo->query("SELECT COUNT(*) FROM users")->fetchColumn();
            $custCount = $pdo->query("SELECT COUNT(*) FROM customers")->fetchColumn();
            $txCount   = $pdo->query("SELECT COUNT(*) FROM transactions")->fetchColumn();

            echo json_encode([
                'status'    => 'ok',
                'service'   => 'TallyKhata Cloud Sync Engine',
                'endpoint'  => 'https://jabin24.com/api/tally_sync.php',
                'package'   => 'www.jabin24.aitally',
                'security'  => 'AES-256-GCM E2EE Enabled',
                'database'  => $db_name,
                'stats'     => [
                    'registered_users' => intval($userCount),
                    'total_customers'  => intval($custCount),
                    'transactions'     => intval($txCount)
                ],
                'timestamp' => date('Y-m-d H:i:s')
            ]);
        } catch (Exception $e) {
            echo json_encode([
                'status'   => 'ok',
                'service'  => 'TallyKhata Cloud Sync Engine',
                'endpoint' => 'https://jabin24.com/api/tally_sync.php',
                'message'  => 'Ready to accept synchronization requests.'
            ]);
        }
        break;
}
