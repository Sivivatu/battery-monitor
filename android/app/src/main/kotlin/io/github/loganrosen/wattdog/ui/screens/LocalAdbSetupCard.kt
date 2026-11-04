package io.github.loganrosen.wattdog.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.loganrosen.wattdog.setup.AdbSetupState
import io.github.loganrosen.wattdog.setup.PairingRequest
import io.github.loganrosen.wattdog.ui.components.SectionCard

@Composable
fun LocalAdbSetupCard(
    state: AdbSetupState,
    onEnable: (pairingPort: String, code: String, connectionPort: String) -> Unit,
    onCancel: () -> Unit,
    onVerify: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var pairingPort by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var connectionPort by remember { mutableStateOf("") }
    var settingsError by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { code = "" } }
    SectionCard("On-device setup (recommended)", modifier) {
        LocalAdbInstructions()
        OutlinedButton(
            onClick = { settingsError = openDeveloperSettings(context) },
            enabled = !state.running,
        ) { Text("Open developer settings") }
        settingsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        PairingFields(
            connectionPort = connectionPort,
            onConnectionPortChange = { connectionPort = it },
            pairingPort = pairingPort,
            onPairingPortChange = { pairingPort = it },
            code = code,
            onCodeChange = { code = it },
            enabled = !state.running,
        )
        LocalAdbControls(
            state = state,
            canPair = PairingRequest.portError(pairingPort) == null && PairingRequest.portError(connectionPort) == null &&
                code.length == PAIRING_CODE_LENGTH,
            onEnable = {
                val submittedCode = code
                code = ""
                onEnable(pairingPort, submittedCode, connectionPort)
            },
            onCancel = {
                code = ""
                onCancel()
            },
            onVerify = onVerify,
        )
    }
}

@Composable
private fun LocalAdbControls(state: AdbSetupState, canPair: Boolean, onEnable: () -> Unit, onCancel: () -> Unit, onVerify: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.running) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (state.running) {
            OutlinedButton(onClick = onCancel) { Text("Cancel setup") }
        } else {
            Button(onClick = onEnable, enabled = canPair) { Text("Pair and enable collection") }
        }
        if (state.verified) {
            Text(
                "ADB is disconnected. Turn Wireless debugging off in Settings, then verify collection again. " +
                    "The granted permissions normally survive reboots and app updates.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        OutlinedButton(onClick = onVerify, enabled = !state.running) { Text("Verify direct collection") }
    }
}

@Composable
private fun LocalAdbInstructions() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "No computer or Shizuku installation is needed. Connect this phone to Wi-Fi for setup.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "1. Enable Developer options in Android Settings (tap Build number seven times in About phone).\n" +
                "2. Open Developer options → Wireless debugging and turn it on. Note the connection port " +
                "after the colon in IP address & port.\n" +
                "3. Choose Pair device with pairing code. Use split screen to keep that dialog open while " +
                "entering its pairing port and six-digit code here. The two ports are different.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PairingFields(
    connectionPort: String,
    onConnectionPortChange: (String) -> Unit,
    pairingPort: String,
    onPairingPortChange: (String) -> Unit,
    code: String,
    onCodeChange: (String) -> Unit,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PortField(connectionPort, onConnectionPortChange, "Connection port (main debugging page)", enabled)
        PortField(pairingPort, onPairingPortChange, "Pairing port (pairing dialog)", enabled)
        OutlinedTextField(
            value = code,
            onValueChange = { onCodeChange(it.filter { char -> char in '0'..'9' }.take(PAIRING_CODE_LENGTH)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Six-digit pairing code") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            enabled = enabled,
        )
    }
}

@Composable
private fun PortField(value: String, onValueChange: (String) -> Unit, label: String, enabled: Boolean) {
    val error = if (value.isEmpty()) null else PairingRequest.portError(value)
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(PORT_INPUT_LIMIT)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        isError = error != null,
        supportingText = { Text(error ?: "Port number only, for example 37743. Valid range: 1–65535.") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        enabled = enabled,
    )
}

private fun openDeveloperSettings(context: Context): String? = try {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
    null
} catch (_: ActivityNotFoundException) {
    "Open Android Settings manually, then search for Developer options."
} catch (_: SecurityException) {
    "Android could not open Developer options. Open it manually in Settings."
}

private const val PAIRING_CODE_LENGTH = 6
private const val PORT_INPUT_LIMIT = 64
