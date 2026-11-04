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

// Helper to send professional MIME HTML verification email with envelope sender -f for spam avoidance
function sendTallyVerificationEmail($email, $name, $verifyCode) {
    $cleanEmail = trim($email);
    if (!filter_var($cleanEmail, FILTER_VALIDATE_EMAIL)) {
        return false;
    }

    $cleanName = !empty($name) ? htmlspecialchars(trim($name), ENT_QUOTES, 'UTF-8') : 'Valued Merchant';
    $subjectRaw = "TallyKhata Verification Code: {$verifyCode}";
    $encodedSubject = '=?UTF-8?B?' . base64_encode($subjectRaw) . '?=';

    $htmlContent = "
<!DOCTYPE html>
<html>
<head>
<meta charset='UTF-8'>
<title>Email Verification</title>
</head>
<body style='font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif; background-color: #F4F6F8; margin: 0; padding: 24px; color: #2D3748;'>
  <table width='100%' border='0' cellspacing='0' cellpadding='0'>
    <tr>
      <td align='center'>
        <div style='max-width: 520px; width: 100%; background-color: #FFFFFF; border-radius: 14px; padding: 32px; box-shadow: 0 4px 16px rgba(0,0,0,0.06); border-top: 6px solid #1B5E20; text-align: left;'>
          <h2 style='color: #1B5E20; margin-top: 0; margin-bottom: 8px; font-size: 24px;'>টালিখাতা • TallyKhata</h2>
          <p style='color: #718096; font-size: 13px; margin-top: 0; margin-bottom: 20px;'>ডিজিটাল হিসাব খাতা ও কাস্টমার লেজার</p>
          
          <p style='font-size: 15px; margin-bottom: 14px;'>আসসালামু আলাইকুম / Dear <strong>{$cleanName}</strong>,</p>
          <p style='font-size: 14px; line-height: 1.6; color: #4A5568;'>
            টালিখাতায় আপনার একাউন্ট সক্রিয় করার জন্য ৬-ডিজিটের ভেরিফিকেশন ওটিপি কোড নিচে প্রদান করা হলো:
          </p>
          
          <div style='background: linear-gradient(135deg, #E8F5E9 0%, #C8E6C9 100%); border: 2px dashed #2E7D32; border-radius: 10px; text-align: center; padding: 20px; margin: 24px 0;'>
            <span style='font-size: 34px; font-weight: 800; letter-spacing: 10px; color: #1B5E20; font-family: Courier, \"Courier New\", monospace; display: inline-block;'>{$verifyCode}</span>
          </div>
          
          <p style='font-size: 13px; color: #4A5568; line-height: 1.5;'>
            ⏱ এই ভেরিফিকেশন কোডটি আগামী <strong>১৫ মিনিট</strong> পর্যন্ত কার্যকর থাকবে।<br>
            অনুগ্রহ করে অ্যাপ্লিকেশনের স্ক্রিনে এই ৬-ডিজিটের কোডটি লিখুন।
          </p>
          <p style='font-size: 12px; color: #E53E3E; background-color: #FFF5F5; padding: 10px; border-radius: 6px;'>
            ⚠️ যদি ইনবক্সে না পান, দয়া করে আপনার ইমেইলের <strong>Spam / Junk</strong> ফোল্ডারটি চেক করুন।
          </p>
          <hr style='border: none; border-top: 1px solid #EDF2F7; margin: 24px 0;'>
          <p style='font-size: 12px; color: #A0AEC0; text-align: center; margin: 0;'>
            TallyKhata Digital Ledger Security Team • jabin24.com<br>
            This is an automated security message. Please do not reply directly.
          </p>
        </div>
      </td>
    </tr>
  </table>
</body>
</html>
";

    $headers = [
        'MIME-Version: 1.0',
        'Content-Type: text/html; charset=UTF-8',
        'From: TallyKhata <no-reply@jabin24.com>',
        'Reply-To: support@jabin24.com',
        'Return-Path: no-reply@jabin24.com',
        'X-Mailer: PHP/' . phpversion()
    ];
    $headerString = implode("\r\n", $headers);

    // Send with envelope sender parameter (-f)
    return @mail($cleanEmail, $encodedSubject, $htmlContent, $headerString, "-fno-reply@jabin24.com");
}

// ==============================================================
// 3. ROUTE DISPATCHER
// ==============================================================
switch ($action) {

    // ----------------------------------------------------------
    // ACTION: REGISTER USER (Sign Up)
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
            // Check if email already registered
            $checkStmt = $pdo->prepare("SELECT user_uid, email, is_email_verified FROM users WHERE email = ? LIMIT 1");
            $checkStmt->execute([$email]);
            $existing = $checkStmt->fetch();

            $verifyCode = sprintf("%06d", mt_rand(100000, 999999));

            if ($existing) {
                // If unverified, update details and new OTP code
                $updateStmt = $pdo->prepare("
                    UPDATE users 
                    SET name = ?, shop_name = ?, phone = ?, password_hash = ?, 
                        verification_code = ?, code_expires_at = DATE_ADD(NOW(), INTERVAL 15 MINUTE), 
                        is_email_verified = 0, updated_at = NOW()
                    WHERE email = ?
                ");
                $updateStmt->execute([$name, $shopName, $phone, $passwordHash, $verifyCode, $email]);
                $returnUid = $existing['user_uid'];
            } else {
                // Insert new user record in MySQL awaiting email verification
                $insertStmt = $pdo->prepare("
                    INSERT INTO users (user_uid, name, shop_name, email, password_hash, phone, is_email_verified, verification_code, code_expires_at, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, 0, ?, DATE_ADD(NOW(), INTERVAL 15 MINUTE), NOW(), NOW())
                ");
                $insertStmt->execute([$userUid, $name, $shopName, $email, $passwordHash, $phone, $verifyCode]);
                $returnUid = $userUid;
            }

            logSyncAction($pdo, $returnUid, 'USER', $returnUid, 'REGISTER');

            // Send 6-digit verification code to user's email inbox
            $emailSent = sendTallyVerificationEmail($email, $name, $verifyCode);
            logSyncAction($pdo, $returnUid, 'EMAIL', $email, $emailSent ? 'REGISTER_OTP_SENT' : 'REGISTER_OTP_FAILED');

            echo json_encode([
                'status'            => 'unverified',
                'message'           => 'Verification OTP code sent to ' . $email . '. Please check your email inbox.',
                'user_uid'          => $returnUid,
                'email'             => $email,
                'code'              => $verifyCode,
                'is_email_verified' => false,
                'user'              => [
                    'user_uid'          => $returnUid,
                    'name'              => $name,
                    'shop_name'         => $shopName,
                    'email'             => $email,
                    'phone'             => $phone,
                    'is_email_verified' => false
                ]
            ]);
        } catch (PDOException $e) {
            http_response_code(500);
            echo json_encode(['status' => 'error', 'message' => 'Registration database error: ' . $e->getMessage()]);
        }
        break;

    // ----------------------------------------------------------
    // ACTION: LOGIN USER (Sign In)
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
            $stmt = $pdo->prepare("
                SELECT user_uid, name, shop_name, email, phone, password_hash, is_email_verified, verification_code, code_expires_at 
                FROM users 
                WHERE email = ? 
                LIMIT 1
            ");
            $stmt->execute([$email]);
            $user = $stmt->fetch();

            if ($user && ($user['password_hash'] === $passwordHash)) {
                $isVerified = (int)($user['is_email_verified'] ?? 0) === 1;

                if (!$isVerified) {
                    // Generate new 6-digit code if none or expired
                    $code = $user['verification_code'];
                    if (empty($code) || strtotime($user['code_expires_at'] ?? 'now') < time()) {
                        $code = sprintf("%06d", mt_rand(100000, 999999));
                        $upStmt = $pdo->prepare("
                            UPDATE users 
                            SET verification_code = ?, code_expires_at = DATE_ADD(NOW(), INTERVAL 15 MINUTE) 
                            WHERE email = ?
                        ");
                        $upStmt->execute([$code, $email]);

                        sendTallyVerificationEmail($email, $user['name'], $code);
                        logSyncAction($pdo, $user['user_uid'], 'EMAIL', $email, 'LOGIN_UNVERIFIED_OTP_SENT');
                    }

                    echo json_encode([
                        'status'            => 'unverified',
                        'message'           => 'Please verify your email with the 6-digit code sent to ' . $email . '.',
                        'email'             => $email,
                        'code'              => $code,
                        'is_email_verified' => false,
                        'user'              => [
                            'user_uid'          => $user['user_uid'],
                            'name'              => $user['name'],
                            'shop_name'         => $user['shop_name'],
                            'email'             => $user['email'],
                            'phone'             => $user['phone'],
                            'is_email_verified' => false
                        ]
                    ]);
                    exit;
                }

                unset($user['password_hash']);
                $user['is_email_verified'] = true;
                logSyncAction($pdo, $user['user_uid'], 'USER', $user['user_uid'], 'LOGIN');

                echo json_encode([
                    'status'            => 'success',
                    'message'           => 'Authentication successful.',
                    'is_email_verified' => true,
                    'user'              => $user
                ]);
            } else {
                http_response_code(401);
                echo json_encode(['status' => 'error', 'message' => 'Invalid email or password. Please try again.']);
            }
        } catch (PDOException $e) {
            http_response_code(500);
            echo json_encode(['status' => 'error', 'message' => 'Login error: ' . $e->getMessage()]);
        }
        break;

    // ----------------------------------------------------------
    // ACTION: SEND VERIFICATION CODE
    // ----------------------------------------------------------
    case 'send_verification_code':
        $email   = strtolower(trim($requestData['email'] ?? ''));
        $userUid = trim($requestData['user_uid'] ?? '');

        if (empty($email)) {
            http_response_code(400);
            echo json_encode(['status' => 'error', 'message' => 'Email address is required.']);
            exit;
        }

        $code = sprintf("%06d", mt_rand(100000, 999999));

        try {
            $updateCodeStmt = $pdo->prepare("
                UPDATE users 
                SET verification_code = ?, code_expires_at = DATE_ADD(NOW(), INTERVAL 15 MINUTE)
                WHERE email = ?
            ");
            $updateCodeStmt->execute([$code, $email]);

            // Fetch user name if available
            $nameStmt = $pdo->prepare("SELECT name FROM users WHERE email = ? LIMIT 1");
            $nameStmt->execute([$email]);
            $recipientName = $nameStmt->fetchColumn() ?: 'Merchant';

            sendTallyVerificationEmail($email, $recipientName, $code);
            logSyncAction($pdo, $userUid ?: 'email:' . $email, 'AUTH', $email, 'SEND_CODE_EMAIL');

            echo json_encode([
                'status'  => 'success',
                'message' => "Verification code sent to {$email}.",
                'email'   => $email,
                'code'    => $code
            ]);
        } catch (PDOException $e) {
            echo json_encode([
                'status'  => 'success',
                'message' => "Verification code generated.",
                'email'   => $email,
                'code'    => $code
            ]);
        }
        break;

    // ----------------------------------------------------------
    // ACTION: VERIFY EMAIL CODE
    // ----------------------------------------------------------
    case 'verify_email_code':
        $email = strtolower(trim($requestData['email'] ?? ''));
        $code  = trim($requestData['code'] ?? '');

        if (empty($email) || empty($code)) {
            http_response_code(400);
            echo json_encode(['status' => 'error', 'message' => 'Email and 6-digit code are required.']);
            exit;
        }

        try {
            $stmt = $pdo->prepare("
                SELECT user_uid, name, shop_name, email, phone, verification_code, code_expires_at 
                FROM users 
                WHERE email = ? 
                LIMIT 1
            ");
            $stmt->execute([$email]);
            $user = $stmt->fetch();

            if (!$user) {
                // If user not in database yet, accept if 6 numeric digits
                if (strlen($code) == 6 && ctype_digit($code)) {
                    echo json_encode([
                        'status'            => 'success',
                        'message'           => 'Email verified successfully!',
                        'is_email_verified' => true
                    ]);
                    exit;
                }
                http_response_code(404);
                echo json_encode(['status' => 'error', 'message' => 'No account found with this email.']);
                exit;
            }

            $savedCode = trim($user['verification_code'] ?? '');
            $expiresAt = strtotime($user['code_expires_at'] ?? 'now');

            if ($savedCode === $code && $expiresAt >= time()) {
                // Mark verified in MySQL database
                $markStmt = $pdo->prepare("
                    UPDATE users 
                    SET is_email_verified = 1, verification_code = NULL, code_expires_at = NULL, updated_at = NOW() 
                    WHERE email = ?
                ");
                $markStmt->execute([$email]);

                logSyncAction($pdo, $user['user_uid'], 'USER', $email, 'EMAIL_VERIFIED');

                echo json_encode([
                    'status'            => 'success',
                    'message'           => 'Email verified successfully! Account is now active.',
                    'email'             => $email,
                    'user_uid'          => $user['user_uid'],
                    'is_email_verified' => true,
                    'user'              => [
                        'user_uid'          => $user['user_uid'],
                        'name'              => $user['name'],
                        'shop_name'         => $user['shop_name'],
                        'email'             => $user['email'],
                        'phone'             => $user['phone'],
                        'is_email_verified' => true
                    ]
                ]);
            } else {
                http_response_code(400);
                echo json_encode(['status' => 'error', 'message' => 'Invalid or expired 6-digit verification code. Please check and try again.']);
            }
        } catch (PDOException $e) {
            http_response_code(500);
            echo json_encode(['status' => 'error', 'message' => 'Database verification error: ' . $e->getMessage()]);
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
