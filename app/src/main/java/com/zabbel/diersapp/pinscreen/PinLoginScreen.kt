package com.zabbel.diersapp.pinscreen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zabbel.diersapp.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import androidx.activity.compose.LocalActivity
import androidx.fragment.app.FragmentActivity
import com.zabbel.diersapp.util.BiometricHelper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.biometric.BiometricPrompt

@Composable
fun PinLoginScreen(
    onPinCorrect: () -> Unit,
    onPinIncorrect: () -> Unit,
    onNavigateToSetup: () -> Unit,
    isReadyToAnimate: Boolean,
    viewModel: PinLoginViewModel = hiltViewModel()
) {
    val context = LocalActivity.current as FragmentActivity
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf(List(4) { "" }) }
    val focusRequestersPin = remember { List(4) { FocusRequester() } }
    var showError by remember { mutableStateOf(false) }

    val logoScale = remember { Animatable(1.0f) }
    val verticalBias = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(0f) }

    val showBiometricOption by viewModel.showBiometricOption.collectAsStateWithLifecycle()
    val isPinVisible by viewModel.isPinVisible.collectAsStateWithLifecycle()
    val biometricLoginSuccess by viewModel.biometricLoginSuccess.collectAsStateWithLifecycle()
    val attempts by viewModel.attempts.collectAsStateWithLifecycle()
    val canNavigateForward by viewModel.canNavigateForward.collectAsStateWithLifecycle()
    var showResetPinDialog by remember { mutableStateOf(false) }

    // Start-Animation
    LaunchedEffect(isReadyToAnimate) {
        if (isReadyToAnimate) {
            launch {
                logoScale.animateTo(
                    targetValue = 1.0f,
                    animationSpec = keyframes {
                        durationMillis = 4000
                        1.0f at 0
                        1.5f at 1500 using FastOutSlowInEasing
                        1.0f at 4000 using FastOutSlowInEasing
                    }
                )
            }
            launch {
                verticalBias.animateTo(
                    targetValue = -0.8f,
                    animationSpec = keyframes {
                        durationMillis = 4000
                        0f at 2000 using FastOutSlowInEasing
                        -0.8f at 4000
                    }
                )
            }
            launch {
                delay(3200.milliseconds)
                contentAlpha.animateTo(1f, tween(800))
                if (isPinVisible) focusRequestersPin[0].requestFocus()
            }
        }
    }

    // Automatischer Start der Biometrie
    LaunchedEffect(showBiometricOption, isReadyToAnimate) {
        if (isReadyToAnimate && showBiometricOption) {
            delay(3500.milliseconds)
            val cipher = viewModel.getDecryptCipher()
            if (cipher != null) {
                BiometricHelper.showBiometricPrompt(
                    activity = context,
                    cryptoObject = BiometricPrompt.CryptoObject(cipher),
                    onSuccess = { result -> viewModel.onBiometricSuccess(result) },
                    onError = { _, _ -> 
                        viewModel.showPinInput()
                    }
                )
            }
        }
    }

    // Navigation nach Erfolg
    LaunchedEffect(canNavigateForward) {
        if (canNavigateForward) {
            onPinCorrect()
            viewModel.resetNavigation()
        }
    }

    LaunchedEffect(biometricLoginSuccess) {
        if (biometricLoginSuccess) {
            if (attempts >= 3) {
                showResetPinDialog = true
            } else {
                onPinCorrect()
                viewModel.resetBiometricSuccess()
            }
        }
    }

    // Dialog: PIN vergessen (nach Bio-Login)
    if (showResetPinDialog) {
        AlertDialog(
            onDismissRequest = { 
                showResetPinDialog = false
                onPinCorrect() 
                viewModel.resetBiometricSuccess()
            },
            title = { Text("PIN vergessen?") },
            text = { Text("Du hast dich per Biometrie angemeldet, aber zuvor mehrmals den falschen PIN eingegeben.\n\nMöchtest du deinen PIN jetzt neu setzen?") },
            confirmButton = {
                Button(onClick = {
                    showResetPinDialog = false
                    viewModel.resetBiometricSuccess()
                    onNavigateToSetup()
                }) { Text("Ja, neu setzen") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showResetPinDialog = false
                    onPinCorrect()
                    viewModel.resetBiometricSuccess()
                }) { Text("Nein, weiter") }
            }
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Logo-Box (Zentrierung gesteuert durch verticalBias)
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = BiasAlignment(0f, verticalBias.value)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.rslogo_old),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .size(280.dp)
                        .graphicsLayer {
                            scaleX = logoScale.value
                            scaleY = logoScale.value
                        }
                )
            }

            // PIN Eingabe Bereich
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .imePadding()
                    .alpha(contentAlpha.value)
                    .padding(horizontal = 32.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Felder nur anzeigen, wenn nötig
                AnimatedVisibility(
                    visible = isPinVisible,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    val performLogin = {
                        val enteredPin = pin.joinToString("")
                        if (enteredPin.length == 4) {
                            scope.launch {
                                if (!viewModel.checkPin(enteredPin)) {
                                    showError = true
                                    pin = List(4) { "" }
                                    focusRequestersPin[0].requestFocus()
                                    onPinIncorrect()
                                }
                            }
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PinInputRow(
                            pinDigits = pin,
                            focusRequesters = focusRequestersPin,
                            onPinChanged = { index, digit ->
                                showError = false
                                val newPin = pin.toMutableList()
                                newPin[index] = digit
                                pin = newPin
                                if (digit.isNotEmpty() && index < 3) {
                                    focusRequestersPin[index + 1].requestFocus()
                                }
                            },
                            onImeAction = { performLogin() }
                        )

                        Text(
                            "PIN eingeben",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            modifier = Modifier.padding(top = 12.dp)
                        )

                        if (showError) {
                            Text(
                                "PIN ist falsch.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        Button(
                            onClick = { performLogin() },
                            enabled = pin.joinToString("").length == 4,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Anmelden")
                        }
                    }
                }

                // Manueller Button um PIN Felder einzublenden (wenn Bio-Modus aktiv ist)
                if (!isPinVisible && showBiometricOption) {
                    TextButton(onClick = { viewModel.showPinInput() }) {
                        Text("Stattdessen PIN nutzen", color = Color.Gray)
                    }
                }
            }
        }
    }
}
