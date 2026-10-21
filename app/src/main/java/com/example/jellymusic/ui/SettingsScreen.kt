package com.example.jellymusic.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.example.jellymusic.AppState
import com.example.jellymusic.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class PendingDirectory(
    val uri: Uri?,
    val label: String?,
)

@Composable
fun SettingsScreen(state: AppState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<PendingDirectory?>(null) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showDirectoryChoice by remember { mutableStateOf(false) }
    var showClearCache by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            tick++
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (_: Exception) {
            }
            val label = runCatching {
                DocumentFile.fromTreeUri(context, uri)?.name
            }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: uri.lastPathSegment
            pending = PendingDirectory(uri, label)
            if (state.downloads.downloadedItems().isEmpty()) {
                state.downloads.changeDirectory(uri, label, migrate = false)
                pending = null
            } else {
                showDirectoryChoice = true
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.settings), onBack = { state.back() })

        LazyColumn(Modifier.fillMaxSize()) {
            item { SettingsSectionTitle(stringResource(R.string.account)) }
            item {
                SettingsValueRow(
                    title = stringResource(R.string.username),
                    value = state.userName.value ?: "-",
                )
            }
            item {
                SettingsValueRow(
                    title = stringResource(R.string.server),
                    value = state.serverUrl ?: "-",
                )
            }

            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SettingsSectionTitle(stringResource(R.string.pending_sync))
            }
            item {
                SettingsValueRow(
                    title = stringResource(R.string.pending_sync),
                    value = stringResource(
                        R.string.pending_sync_count,
                        state.favorites.pendingCount(),
                        state.localPlaylists.pendingCount(),
                    ),
                )
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val ok = state.syncOfflineChanges()
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        if (ok) R.string.sync_done else R.string.sync_failed,
                                    ),
                                    Toast.LENGTH_SHORT,
                                ).show()
                                tick++
                            }
                        },
                        enabled = !state.syncing.value,
                    ) {
                        Text(stringResource(R.string.sync_now))
                    }
                }
            }

            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SettingsSectionTitle(stringResource(R.string.downloads))
            }
            item {
                SettingsValueRow(
                    title = stringResource(R.string.download_directory),
                    value = state.downloads.directoryLabel.value
                        ?: stringResource(R.string.default_directory),
                )
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { picker.launch(null) },
                        enabled = !state.downloads.isMigrating,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.choose_folder))
                    }
                    OutlinedButton(
                        onClick = {
                            pending = PendingDirectory(null, null)
                            if (state.downloads.downloadedItems().isEmpty()) {
                                state.downloads.resetDirectory(migrate = false)
                                pending = null
                            } else {
                                showDirectoryChoice = true
                            }
                        },
                        enabled = !state.downloads.isMigrating,
                    ) {
                        Text(stringResource(R.string.restore_default))
                    }
                }
            }

            val migration = state.downloads.migration.value
            if (migration != null) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        LinearProgressIndicator(
                            progress = if (migration.total > 0) {
                                migration.done.toFloat() / migration.total
                            } else {
                                0f
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.migrating, migration.done, migration.total) +
                                if (migration.failed > 0) {
                                    stringResource(R.string.migrating_failed, migration.failed)
                                } else {
                                    ""
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SettingsSectionTitle(stringResource(R.string.playback_cache))
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.auto_cache_listened),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            stringResource(R.string.cache_auto_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.cache_limit_note, state.streamCache.limitMb),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.streamCache.enabled,
                        onCheckedChange = {
                            state.streamCache.enabled = it
                            tick++
                        },
                    )
                }
            }
            item {
                val usedBytes = remember(tick) { state.streamCache.sizeBytes() }
                val cachedCount = remember(tick) { state.streamCache.count() }
                SettingsValueRow(
                    title = stringResource(R.string.used_space),
                    value = stringResource(
                        R.string.used_space_value,
                        formatCacheSize(usedBytes),
                        cachedCount,
                    ),
                )
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(256 to "256M", 512 to "512M", 1024 to "1G", 2048 to "2G")
                        .forEach { (mb, label) ->
                            FilterChip(
                                selected = state.streamCache.limitMb == mb,
                                onClick = {
                                    state.streamCache.limitMb = mb
                                    tick++
                                },
                                label = { Text(label) },
                            )
                        }
                }
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = { showClearCache = true },
                        enabled = state.streamCache.count() > 0,
                    ) {
                        Text(stringResource(R.string.clear_cache))
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.cache_policy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { state.navigate(Screen.Changelog) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.NewReleases,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.changelog),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { showLogoutConfirm = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.logout),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.logout_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text(stringResource(R.string.logout_confirm_title)) },
            text = { Text(stringResource(R.string.logout_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirm = false
                        state.logout()
                    },
                ) {
                    Text(stringResource(R.string.logout), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showClearCache) {
        AlertDialog(
            onDismissRequest = { showClearCache = false },
            title = { Text(stringResource(R.string.clear_cache_title)) },
            text = { Text(stringResource(R.string.clear_cache_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.streamCache.clear()
                        showClearCache = false
                        tick++
                    },
                ) {
                    Text(stringResource(R.string.clear), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCache = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showDirectoryChoice && pending != null) {
        val change = pending!!
        val count = state.downloads.downloadedItems().size
        AlertDialog(
            onDismissRequest = {
                showDirectoryChoice = false
                pending = null
            },
            title = { Text(stringResource(R.string.switch_download_dir)) },
            text = { Text(stringResource(R.string.switch_download_dir_text, count)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.downloads.changeDirectory(change.uri, change.label, migrate = true)
                        showDirectoryChoice = false
                        pending = null
                    },
                ) {
                    Text(stringResource(R.string.migrate))
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            state.downloads.changeDirectory(change.uri, change.label, migrate = false)
                            showDirectoryChoice = false
                            pending = null
                        },
                    ) {
                        Text(stringResource(R.string.switch_only))
                    }
                    TextButton(
                        onClick = {
                            showDirectoryChoice = false
                            pending = null
                        },
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            },
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun SettingsValueRow(title: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatCacheSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        "%.1f GB".format(mb / 1024.0)
    } else {
        "%.0f MB".format(mb)
    }
}