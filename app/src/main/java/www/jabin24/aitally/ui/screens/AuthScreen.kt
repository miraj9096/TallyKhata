package www.jabin24.aitally.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import www.jabin24.aitally.R
import www.jabin24.aitally.ui.theme.TallyCashIn
import www.jabin24.aitally.ui.theme.TallyPrimary

import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Refresh

@Composable
fun AuthScreen(
    isLoading: Boolean,
    errorMessage: String?,
    verificationStatusMessage: String? = null,
    lastGeneratedCode: String? = null,
    onLogin: (email: String, pass: String) -> Unit,
    onRegister: (name: String, shop: String, email: String, pass: String, phone: String) -> Unit,
    onSendVerificationCode: (email: String) -> Unit = {},
    onVerifyEmailCode: (email: String, code: String) -> Unit = { _, _ -> },
    onOfflineDemoAccess: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Login, 1: Register, 2: Verify Code
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var shopName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var inputOtpCode by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // App Brand Header
            Surface(
                shape = CircleShape,
                color = TallyPrimary,
                modifier = Modifier.size(80.dp),
                shadowElevation = 6.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_tally_logo_1793452377274),
                        contentDescription = "TallyKhata Logo",
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "TallyKhata • টালিখাতা",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                ),
                color = TallyPrimary
            )
            Text(
                text = "Smart Credit Ledger & SMS Payment Reminders",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Security Badge
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = TallyCashIn
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "AES-256 E2EE • Offline Sync • jabin24.com",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Auth Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = TallyPrimary
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Sign In", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                            modifier = Modifier.testTag("tab_login")
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Register", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                            modifier = Modifier.testTag("tab_register")
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = {
                                selectedTab = 2
                                if (email.isNotBlank()) onSendVerificationCode(email)
                            },
                            text = { Text("Verify Code", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                            modifier = Modifier.testTag("tab_verify_code")
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    if (errorMessage != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    if (!verificationStatusMessage.isNullOrBlank()) {
                        Surface(
                            color = if (verificationStatusMessage.contains("✅") || verificationStatusMessage.contains("🎉")) {
                                TallyCashIn.copy(alpha = 0.12f)
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Text(
                                text = verificationStatusMessage,
                                color = if (verificationStatusMessage.contains("✅") || verificationStatusMessage.contains("🎉")) {
                                    TallyCashIn
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                },
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    if (selectedTab == 1) {
                        // Register Fields
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Your Full Name *") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_name_input")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = shopName,
                            onValueChange = { shopName = it },
                            label = { Text("Store / Business Name (দোকানের নাম)") },
                            leadingIcon = { Icon(Icons.Default.Store, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_shop_input")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Mobile Number (মোবাইল নম্বর) *") },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_phone_input")
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Email Field
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address *") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_email_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (selectedTab != 2) {
                        // Password Field for Login and Register
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password *") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_password_input")
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = {
                                if (selectedTab == 0) {
                                    onLogin(email, password)
                                } else {
                                    onRegister(name, shopName, email, password, phone)
                                }
                            },
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = TallyPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("auth_submit_button")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = if (selectedTab == 0) "Sign In to TallyKhata" else "Create Account & Verify Code",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    } else {
                        // Tab 2: Email Verification by Code
                        if (!lastGeneratedCode.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = TallyCashIn.copy(alpha = 0.1f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { inputOtpCode = lastGeneratedCode }
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Generated Code: $lastGeneratedCode",
                                        fontWeight = FontWeight.Bold,
                                        color = TallyCashIn,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "Auto-fill",
                                        fontWeight = FontWeight.SemiBold,
                                        color = TallyPrimary,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        OutlinedTextField(
                            value = inputOtpCode,
                            onValueChange = { input ->
                                val digits = input.filter { it.isDigit() }.take(6)
                                inputOtpCode = digits
                            },
                            label = { Text("6-Digit Verification Code (যাচাই কোড) *") },
                            placeholder = { Text("e.g. 849201") },
                            leadingIcon = { Icon(Icons.Default.MarkEmailRead, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("verify_code_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { onSendVerificationCode(email) },
                                enabled = !isLoading && email.isNotBlank()
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Resend Code", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = { onVerifyEmailCode(email, inputOtpCode) },
                            enabled = !isLoading && inputOtpCode.length == 6,
                            colors = ButtonDefaults.buttonColors(containerColor = TallyPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("verify_code_submit_button")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = "Verify Email • ইমেইল যাচাই করুন",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Offline Demo Access
            OutlinedButton(
                onClick = onOfflineDemoAccess,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("offline_demo_button")
            ) {
                Icon(
                    Icons.Default.WifiOff,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start in Offline Mode / অফলাইন ব্যবহার")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Package: www.jabin24.aitally • Sync: jabin24.com/api/tally_sync.php",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}
