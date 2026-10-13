package com.mahirlabib.youtube

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun AdminGateScreen(onSuccess: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var configured by remember { mutableStateOf(LocalAdminStore.isConfigured(context)) }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var message by remember { mutableStateOf(if (configured) "Enter PIN" else "Create a 6–12 digit PIN") }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(if (configured) "Admin Login" else "Set Up Admin", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(12) }, modifier = Modifier.fillMaxWidth(), label = { Text("PIN") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        if (!configured) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = confirm, onValueChange = { confirm = it.filter(Char::isDigit).take(12) }, modifier = Modifier.fillMaxWidth(), label = { Text("Confirm PIN") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        }
        Text(message)
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            if (configured) {
                if (LocalAdminStore.verifyPin(context, pin)) onSuccess() else message = "Incorrect PIN"
            } else if (!pin.matches(Regex("^\\d{6,12}$"))) {
                message = "PIN must contain 6–12 digits"
            } else if (pin != confirm) {
                message = "PINs do not match"
            } else {
                runCatching { LocalAdminStore.setPin(context, pin, confirm) }
                    .onSuccess { configured = true; onSuccess() }
                    .onFailure { message = it.message ?: "Could not save PIN" }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(if (configured) "Login" else "Save PIN") }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}
