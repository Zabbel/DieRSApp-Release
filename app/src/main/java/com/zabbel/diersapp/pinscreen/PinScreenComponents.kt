package com.zabbel.diersapp.pinscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay


// Zustand für die Animationsphasen
enum class PinScreenAnimationState {
    START, // Logo mittig (simuliert Splash)
    PIN_ENTRY // Logo oben, PIN-Felder sichtbar
}
class PasswordVisualTransformation(private val mask: Char = '•') :
    VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(
            text = androidx.compose.ui.text.AnnotatedString(mask.toString().repeat(text.text.length)),
            offsetMapping = OffsetMapping.Identity
        )
    }
}

@Composable
fun PinInputRow(
    pinDigits: List<String>,
    focusRequesters: List<FocusRequester>,
    onPinChanged: (index: Int, digit: String) -> Unit,
    enabled: Boolean = true, // Um die Eingabe ggf. zu sperren
    useVisualTransformation: Boolean = true, // Neu: Diesen Parameter hinzufügen
    onImeAction: () -> Unit = {} // Neu: Callback für Enter/Haken
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp), // Etwas mehr Abstand zwischen den Boxen
        verticalAlignment = Alignment.CenterVertically
    ) {
        pinDigits.forEachIndexed { index, digit ->
            PinDigitBox(
                digit = digit,
                modifier = Modifier.focusRequester(focusRequesters[index]),
                onValueChanged = { newValue ->
                    // Erlaube nur eine Ziffer und filtere Nicht-Ziffern
                    val newDigit = newValue.filter { it.isDigit() }.take(1)
                    onPinChanged(index, newDigit)
                },
                enabled = enabled,
                useVisualTransformation = useVisualTransformation,
                imeAction = if (index == pinDigits.lastIndex) {
                    if (digit.isEmpty()) ImeAction.None else ImeAction.Done
                } else {
                    ImeAction.Next
                },
                onImeAction = onImeAction
            )
        }
    }
}

@Composable
fun PinDigitBox(
    digit: String,
    modifier: Modifier = Modifier,
    onValueChanged: (String) -> Unit,
    enabled: Boolean = true,
    useVisualTransformation: Boolean = true, // Standardmäßig auf true setzen
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {}
) {
    // Zustand für die verzögerte Maske
    var showMask by remember(digit) { mutableStateOf(false) }

    LaunchedEffect(digit) {
        if (digit.isNotEmpty() && useVisualTransformation) {
            showMask = false // Zuerst Klartext zeigen
            delay(1000L)     // 1 Sekunde warten (oder 1500L für 1,5 Sek)
            showMask = true  // Dann maskieren
        } else {
            showMask = false // Wenn leer, keine Maske
        }
    }

    BasicTextField(
        value = digit,
        onValueChange = onValueChanged,
        enabled = enabled,
        modifier = modifier
            .size(60.dp)
            .background(
                color = if (enabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.medium
            )
            .border(
                width = 1.dp,
                color = if (digit.isNotEmpty() && enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                shape = MaterialTheme.shapes.medium
            )
            .padding(4.dp),
        textStyle = TextStyle(
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onDone = { onImeAction() },
            onNext = { /* Standardverhalten beibehalten oder Fokus manuell schieben */ }
        ),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                innerTextField()
            }
        },
        singleLine = true,
        // HIER passiert die Magie:
        visualTransformation = if (showMask) {
            PasswordVisualTransformation('●') // Nutze den Punkt
        } else {
            VisualTransformation.None
        }
    )
}
