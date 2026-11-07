package io.github.inwins.jellymusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import io.github.inwins.jellymusic.jellyfin.QuickConnectState
import io.github.inwins.jellymusic.AppState
import io.github.inwins.jellymusic.R
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(state: AppState) {
    var scheme by rememberSaveable { mutableStateOf("http") }
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("8096") }
    val serverUrl = buildServerUrl(scheme, host, port)
    val portValid = port.trim().isEmpty() ||
        port.trim().toIntOrNull()?.let { it in 1..65535 } == true
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }
    var quickConnect by remember { mutableStateOf<QuickConnectState?>(null) }
    val scope = rememberCoroutineScope()
    val loginFailedMessage = stringResource(R.string.login_failed)

    LaunchedEffect(quickConnect?.secret) {
        val secret = quickConnect?.secret ?: return@LaunchedEffect
        while (quickConnect != null) {
            delay(2000)
            val auth = state.pollQuickConnect(secret)
            if (auth != null) {
                state.completeQuickConnect(auth)
                quickConnect = null
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(40.dp))
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(32.dp))

        if (state.sessionExpired.value) {
            Text(
                stringResource(R.string.session_expired),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = scheme == "http",
                onClick = {
                    if (scheme != "http") {
                        if (port == "8920" || port.isBlank()) port = "8096"
                        scheme = "http"
                    }
                },
                label = { Text("HTTP") },
                enabled = !loading,
            )
            FilterChip(
                selected = scheme == "https",
                onClick = {
                    if (scheme != "https") {
                        if (port == "8096" || port.isBlank()) port = "8920"
                        scheme = "https"
                    }
                },
                label = { Text("HTTPS") },
                enabled = !loading,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text(stringResource(R.string.server_address)) },
                placeholder = { Text(stringResource(R.string.server_address_hint)) },
                singleLine = true,
                enabled = !loading,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = port,
                onValueChange = { value ->
                    if (value.all { it.isDigit() }) port = value
                },
                label = { Text(stringResource(R.string.port)) },
                singleLine = true,
                enabled = !loading,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                ),
                isError = !portValid,
                modifier = Modifier.width(108.dp),
            )
        }
        if (!portValid) {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.invalid_port),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text(stringResource(R.string.username)) },
            singleLine = true,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.password)) },
            singleLine = true,
            enabled = !loading,
            visualTransformation = if (passwordVisible) {
                androidx.compose.ui.text.input.VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        if (passwordVisible) {
                            Icons.Default.VisibilityOff
                        } else {
                            Icons.Default.Visibility
                        },
                        contentDescription = stringResource(
                            if (passwordVisible) R.string.hide_password else R.string.show_password,
                        ),
                    )
                }
            },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                error ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (loading) return@Button
                loading = true
                error = null
                scope.launch {
                    val result = state.login(serverUrl, username, password)
                    if (result.isFailure) {
                        error = result.exceptionOrNull()?.message ?: loginFailedMessage
                        loading = false
                    }
                }
            },
            enabled = !loading && host.isNotBlank() && portValid &&
                username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(stringResource(R.string.login), fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        TextButton(
            onClick = {
                if (serverUrl.isBlank()) {
                    error = loginFailedMessage
                    return@TextButton
                }
                error = null
                scope.launch {
                    state.startQuickConnect(serverUrl)
                        .onSuccess { quickConnect = it }
                        .onFailure { error = it.message ?: loginFailedMessage }
                }
            },
            enabled = !loading && host.isNotBlank() && portValid,
        ) {
            Text(stringResource(R.string.quick_connect_start))
        }
        Spacer(Modifier.height(32.dp))
    }

    quickConnect?.let { qc ->
        AlertDialog(
            onDismissRequest = { quickConnect = null },
            title = { Text(stringResource(R.string.quick_connect)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.quick_connect_hint))
                    Spacer(Modifier.height(12.dp))
                    Text(qc.code, fontSize = 28.sp, color = MaterialTheme.colorScheme.primary)
                }
            },
            confirmButton = {
                TextButton(onClick = { quickConnect = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
private fun buildServerUrl(scheme: String, host: String, port: String): String {
    val raw = host.trim().trimEnd('/')
    if (raw.isEmpty()) return ""
    val lower = raw.lowercase()
    if (lower.startsWith("http://") || lower.startsWith("https://")) return raw
    val portPart = port.trim().takeIf { it.isNotEmpty() }?.let { ":$it" } ?: ""
    return "$scheme://$raw$portPart"
}
