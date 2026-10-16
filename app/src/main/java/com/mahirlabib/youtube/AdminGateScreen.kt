package com.mahirlabib.youtube

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun AdminGateScreen(onSuccess: () -> Unit, onCancel: () -> Unit) {
    BackHandler { onCancel() }
    val context = LocalContext.current
    var configured by remember { mutableStateOf(LocalAdminStore.isConfigured(context)) }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var message by remember { mutableStateOf(if (configured) "Enter your admin PIN" else "Create a 6–12 digit admin PIN") }
    var isError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("admin_gate_screen"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (configured) "Local Admin Login" else "Set Up Local Admin",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (configured) "Manage your curated channels and cache" else "Protect channel management on this device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))

                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        pin = it.filter(Char::isDigit).take(12)
                        isError = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_pin_input"),
                    label = { Text("PIN (6-12 digits)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    isError = isError
                )

                if (!configured) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = {
                            confirm = it.filter(Char::isDigit).take(12)
                            isError = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_confirm_pin_input"),
                        label = { Text("Confirm PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        isError = isError
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = message,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (configured) {
                            if (LocalAdminStore.verifyPin(context, pin)) {
                                onSuccess()
                            } else {
                                message = "Incorrect PIN"
                                isError = true
                            }
                        } else if (!pin.matches(Regex("^\\d{6,12}$"))) {
                            message = "PIN must contain 6–12 digits"
                            isError = true
                        } else if (pin != confirm) {
                            message = "PINs do not match"
                            isError = true
                        } else {
                            runCatching { LocalAdminStore.setPin(context, pin, confirm) }
                                .onSuccess {
                                    configured = true
                                    onSuccess()
                                }
                                .onFailure {
                                    message = it.message ?: "Could not save PIN"
                                    isError = true
                                }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_submit_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0033))
                ) {
                    Text(if (configured) "Login" else "Save Admin PIN")
                }

                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_cancel_button")
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}
