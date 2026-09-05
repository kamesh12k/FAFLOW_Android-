package com.governence.faflow.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.governence.faflow.R
import com.governence.faflow.auth.ui.AuthUiState
import com.governence.faflow.auth.ui.AuthViewModel
import com.governence.faflow.core.network.FaflowApiClient
import com.governence.faflow.ui.components.FacultyFlowBrandHeader
import com.governence.faflow.ui.components.FaflowPillButton
import com.governence.faflow.ui.components.FaflowStatusBadge
import com.governence.faflow.ui.components.FaflowSurface
import com.governence.faflow.ui.theme.PrimaryBlue
import com.governence.faflow.ui.theme.SecondaryTeal
import com.governence.faflow.ui.theme.StatusError
import com.governence.faflow.ui.theme.StatusSuccess
import kotlinx.coroutines.delay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val uiState by authViewModel.uiState.collectAsState()
    val isDark = isSystemInDarkTheme()

    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isInputFocused by remember { mutableStateOf(false) }
    var showServerConfigDialog by remember { mutableStateOf(false) }
    var serverUrlInput by remember { mutableStateOf(FaflowApiClient.baseUrl) }

    // Adaptive height based on soft keyboard state
    val isImeOpen = WindowInsets.isImeVisible
    val lottieHeight by animateDpAsState(
        targetValue = if (isImeOpen) 70.dp else 150.dp,
        animationSpec = tween(durationMillis = 260),
        label = "lottieHeight"
    )

    // Local Lottie asset loading (zero network latency, 100% offline reliability)
    val compositionResult = rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.lottie_login)
    )
    val composition by compositionResult

    // Dynamic animation playback state
    val isAuthenticating = uiState is AuthUiState.Loading
    val isAuthenticated = uiState is AuthUiState.Authenticated

    val animationSpeed = when {
        isAuthenticated -> 2.0f
        isAuthenticating -> 1.5f
        isInputFocused -> 1.1f
        else -> 0.85f
    }

    val lottieProgress by animateLottieCompositionAsState(
        composition = composition,
        iterations = if (isAuthenticated) 1 else LottieConstants.IterateForever,
        speed = animationSpeed,
        isPlaying = true
    )

    // Graceful login success transition delay for smooth UX
    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Authenticated) {
            delay(400)
            onLoginSuccess()
        }
    }

    val backgroundBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0F172A),
                Color(0xFF090D16),
                Color(0xFF020617)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFEEF2FF), // Subtle soft indigo atmospheric tint
                Color(0xFFF1F5F9), // Slate 100
                Color(0xFFF8FAFC)  // Slate 50
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        // Subtle ambient atmospheric aura behind brand header
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .size(340.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            PrimaryBlue.copy(alpha = if (isDark) 0.22f else 0.12f),
                            SecondaryTeal.copy(alpha = if (isDark) 0.08f else 0.04f),
                            Color.Transparent
                        )
                    )
                )
        )

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(modifier = Modifier.height(if (isImeOpen) 16.dp else 36.dp))

                // 1. FAFLOW BRAND LOGO (Official Vector Symbol & Wordmark)
                FacultyFlowBrandHeader(
                    markSize = if (isImeOpen) 36.dp else 50.dp
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 2. WELCOME / GREETING & INSTITUTIONAL BADGE
                FaflowStatusBadge(
                    text = "FAFLOW UNIFIED PORTAL",
                    statusColor = PrimaryBlue
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Institutional Academic & Mobility Ecosystem",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(if (isImeOpen) 12.dp else 22.dp))

                // 3. ELEVATED LOGIN CARD CONTAINER
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = if (isDark) 10.dp else 8.dp,
                            shape = RoundedCornerShape(24.dp),
                            spotColor = PrimaryBlue.copy(alpha = 0.15f)
                        ),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (isDark) Color(0xFF131B2E) else Color(0xFFFFFFFF)
                    ),
                    elevation = CardDefaults.elevatedCardElevation(
                        defaultElevation = 6.dp
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // FRAMED LOTTIE ILLUSTRATION HERO
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(lottieHeight)
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    if (isDark) {
                                        Color(0xFF1E293B).copy(alpha = 0.6f)
                                    } else {
                                        Color(0xFFF8FAFC)
                                    }
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0),
                                    shape = RoundedCornerShape(18.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (composition != null) {
                                LottieAnimation(
                                    composition = composition,
                                    progress = { lottieProgress },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    color = PrimaryBlue,
                                    strokeWidth = 2.dp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

            // ERROR / SUCCESS NOTIFICATION BANNER
            AnimatedVisibility(
                visible = uiState is AuthUiState.Error,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                if (uiState is AuthUiState.Error) {
                    FaflowSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        backgroundColor = StatusError.copy(alpha = 0.12f),
                        borderColor = StatusError.copy(alpha = 0.35f)
                    ) {
                        Text(
                            text = (uiState as AuthUiState.Error).message,
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusError,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isAuthenticated,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut()
            ) {
                FaflowSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    backgroundColor = StatusSuccess.copy(alpha = 0.12f),
                    borderColor = StatusSuccess.copy(alpha = 0.35f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = StatusSuccess,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Authentication Verified. Welcome!",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusSuccess,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 4. LOGIN FORM
            // Identifier Input (Email or Username)
            OutlinedTextField(
                value = identifier,
                onValueChange = {
                    identifier = it
                    authViewModel.clearError()
                },
                label = { Text("Institutional Email / Username") },
                placeholder = { Text("rekha.devi@college.edu or admin") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.AlternateEmail,
                        contentDescription = "Identifier Icon",
                        tint = if (isInputFocused) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isInputFocused = it.isFocused }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Password Input
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    authViewModel.clearError()
                },
                label = { Text("Password") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Password Icon",
                        tint = if (isInputFocused) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (identifier.isNotBlank() && password.isNotBlank()) {
                            authViewModel.login(identifier, password)
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isInputFocused = it.isFocused }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 5. PRIMARY ACTION
            if (isAuthenticating) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = PrimaryBlue,
                        strokeWidth = 3.dp
                    )
                }
            } else {
                FaflowPillButton(
                    text = "Sign In",
                    onClick = {
                        focusManager.clearFocus()
                        if (identifier.isBlank() || password.isBlank()) {
                            Toast.makeText(context, "Please enter both credentials", Toast.LENGTH_SHORT).show()
                        } else {
                            authViewModel.login(identifier, password)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    isPrimary = true
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 6. QUICK DEMO PRESETS
            FaflowSurface(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = if (isDark) Color(0xFF1E293B).copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                borderColor = if (isDark) Color(0xFF334155) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = SecondaryTeal,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Quick Demo Access",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Admin Demo
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isDark) Color(0xFF0F172A) else MaterialTheme.colorScheme.surface)
                                .border(
                                    width = 1.dp,
                                    color = if (isDark) Color(0xFF334155) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    identifier = "admin"
                                    password = "admin"
                                    authViewModel.clearError()
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Admin",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlue
                            )
                        }

                        // Faculty Demo
                        Box(
                            modifier = Modifier
                                .weight(1.1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isDark) Color(0xFF0F172A) else MaterialTheme.colorScheme.surface)
                                .border(
                                    width = 1.dp,
                                    color = if (isDark) Color(0xFF334155) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    identifier = "rekha.devi@college.edu"
                                    password = "Password123"
                                    authViewModel.clearError()
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Faculty",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlue
                            )
                        }

                        // HOD Demo
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isDark) Color(0xFF0F172A) else MaterialTheme.colorScheme.surface)
                                .border(
                                    width = 1.dp,
                                    color = if (isDark) Color(0xFF334155) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    identifier = "hod_ece"
                                    password = "Password123"
                                    authViewModel.clearError()
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "HOD",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlue
                            )
                        }
                    }
                }
            }
        } // End of Column inside ElevatedCard
    } // End of ElevatedCard

    Spacer(modifier = Modifier.height(18.dp))

    // 7. ACTIVE SERVER ENDPOINT INDICATOR & QUICK SWITCHER
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (isDark) Color(0xFF131B2E) else Color.White)
            .border(
                width = 1.dp,
                color = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0),
                shape = CircleShape
            )
            .clickable {
                serverUrlInput = FaflowApiClient.baseUrl
                showServerConfigDialog = true
            }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(0xFF10B981))
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = "Server: ${FaflowApiClient.baseUrl}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }

    Spacer(modifier = Modifier.height(28.dp))
} // End of Scrollable Column
} // End of Scaffold
} // End of Root Box

    // Backend Server URL Configuration Modal
    if (showServerConfigDialog) {
        AlertDialog(
            onDismissRequest = { showServerConfigDialog = false },
            title = {
                Text(
                    text = "Backend Server URL",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Configure your FAFLOW FastAPI backend endpoint. Default for Android Emulator is 10.0.2.2:8000.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = serverUrlInput,
                        onValueChange = { serverUrlInput = it },
                        label = { Text("Server URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = { serverUrlInput = FaflowApiClient.DEFAULT_EMULATOR_URL }) {
                            Text("10.0.2.2")
                        }
                        TextButton(onClick = { serverUrlInput = FaflowApiClient.DEFAULT_EMULATOR_LOOPBACK_URL }) {
                            Text("127.0.0.1")
                        }
                        TextButton(onClick = { serverUrlInput = FaflowApiClient.DEFAULT_LAN_URL }) {
                            Text("Wi-Fi LAN")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        FaflowApiClient.setAndPersistBaseUrl(context, serverUrlInput)
                        Toast.makeText(context, "Server updated to: $serverUrlInput", Toast.LENGTH_SHORT).show()
                        showServerConfigDialog = false
                    }
                ) {
                    Text("Save & Apply", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerConfigDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
