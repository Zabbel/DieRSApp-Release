package com.zabbel.diersapp.pinscreen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zabbel.diersapp.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun PinSetupScreen(
    onPinSetupComplete: () -> Unit,
    isReadyToAnimate: Boolean,
    viewModel: PinSetupViewModel = hiltViewModel()
) {
    var pin by remember { mutableStateOf(List(4) { "" }) }
    var confirmPin by remember { mutableStateOf(List(4) { "" }) }
    var setupStep by remember { mutableIntStateOf(1) } // 1: Eingabe, 2: Bestätigung
    
    val focusRequestersPin = remember { List(4) { FocusRequester() } }
    val focusRequestersConfirmPin = remember { List(4) { FocusRequester() } }
    val coroutineScope = rememberCoroutineScope()

    val logoScale = remember { Animatable(1.0f) }
    val verticalBias = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(0f) }

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
                        (-0.8f) at 4000
                    }
                )
            }
            launch {
                delay(3200.milliseconds)
                contentAlpha.animateTo(1f, tween(800))
                focusRequestersPin[0].requestFocus()
            }
        }
    }

    LaunchedEffect(viewModel.isSuccess) {
        if (viewModel.isSuccess) {
            onPinSetupComplete()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .imePadding()
                    .alpha(contentAlpha.value)
                    .padding(bottom = 32.dp)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val handleSave = {
                    val enteredPin = pin.joinToString("")
                    val enteredConfirmPin = confirmPin.joinToString("")
                    if (enteredPin.length == 4 && enteredConfirmPin.length == 4) {
                        viewModel.validateAndSave(enteredPin, enteredConfirmPin)
                        if (viewModel.errorMessage != null) {
                            coroutineScope.launch {
                                // Bei Fehler zurück zu Schritt 1 und alles leeren
                                setupStep = 1
                                pin = List(4) { "" }
                                confirmPin = List(4) { "" }
                                delay(300.milliseconds)
                                focusRequestersPin[0].requestFocus()
                            }
                        }
                    }
                }

                AnimatedContent(
                    targetState = setupStep,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> -width } + fadeOut())
                        } else {
                            (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> width } + fadeOut())
                        }.using(SizeTransform(clip = false))
                    },
                    label = "PinStepAnimation"
                ) { step ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (step == 1) {
                            Text(
                                "Bitte neuen PIN festlegen",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            PinInputRow(
                                pinDigits = pin,
                                focusRequesters = focusRequestersPin,
                                onPinChanged = { index, digit ->
                                    viewModel.clearError()
                                    val newPin = pin.toMutableList().apply { set(index, digit) }
                                    pin = newPin
                                    if (digit.isNotEmpty()) {
                                        if (index < 3) focusRequestersPin[index + 1].requestFocus()
                                        else {
                                            // Nach 4 Stellen automatisch zu Schritt 2
                                            coroutineScope.launch {
                                                delay(200.milliseconds)
                                                setupStep = 2
                                                delay(300.milliseconds)
                                                focusRequestersConfirmPin[0].requestFocus()
                                            }
                                        }
                                    }
                                },
                                onImeAction = { /* Nichts tun in Schritt 1 */ }
                            )
                        } else {
                            Text(
                                "PIN bitte bestätigen",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            PinInputRow(
                                pinDigits = confirmPin,
                                focusRequesters = focusRequestersConfirmPin,
                                onPinChanged = { index, digit ->
                                    val newConfirmPin = confirmPin.toMutableList().apply { set(index, digit) }
                                    confirmPin = newConfirmPin
                                    if (digit.isNotEmpty() && index < 3) {
                                        focusRequestersConfirmPin[index + 1].requestFocus()
                                    }
                                },
                                onImeAction = { handleSave() }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                    viewModel.errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (setupStep == 2) {
                        OutlinedButton(
                            onClick = {
                                setupStep = 1
                                confirmPin = List(4) { "" }
                                coroutineScope.launch {
                                    delay(300.milliseconds)
                                    focusRequestersPin[3].requestFocus()
                                }
                            },
                            modifier = Modifier.weight(0.4f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Zurück")
                        }
                    }

                    Button(
                        onClick = {
                            if (setupStep == 1) {
                                setupStep = 2
                                coroutineScope.launch {
                                    delay(300.milliseconds)
                                    focusRequestersConfirmPin[0].requestFocus()
                                }
                            } else {
                                handleSave()
                            }
                        },
                        enabled = if (setupStep == 1) pin.all { it.isNotEmpty() } else confirmPin.all { it.isNotEmpty() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (setupStep == 1) "Weiter" else "PIN speichern")
                    }
                }
            }
        }
    }
}
