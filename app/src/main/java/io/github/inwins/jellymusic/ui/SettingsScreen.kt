package io.github.inwins.jellymusic.ui

import android.content.Intent
import android.os.Build
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextField
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
import io.github.inwins.jellymusic.AppState
import io.github.inwins.jellymusic.BuildConfig
import io.github.inwins.jellymusic.R
import io.github.inwins.jellymusic.data.AudioAnalysisScheduler
import io.github.inwins.jellymusic.data.AutoDownloadScheduler
import io.github.inwins.jellymusic.data.ThemeMode
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
    var editingLocalAddress by remember { mutableStateOf(false) }
    var localAddressInput by remember { mutableStateOf("") }
    var showEqualizer by remember { mutableStateOf(false) }
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            localAddressInput = state.localServerUrl.orEmpty()
                            editingLocalAddress = true
                        }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.local_address),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            stringResource(
                                R.string.current_connection,
                                stringResource(
                                    if (state.connectionIsLocal.value) {
                                        R.string.connection_local
                                    } else {
                                        R.string.connection_remote
                                    },
                                ),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        state.localServerUrl ?: stringResource(R.string.not_set),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            item {
                SettingSwitchRow(
                    title = stringResource(R.string.prefer_local_address),
                    checked = state.settings.preferLocalAddress,
                    onCheckedChange = { state.setPreferLocalAddress(it) },
                )
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
                            stringResource(R.string.offline_mode),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            stringResource(R.string.offline_mode_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.settings.manualOffline,
                        onCheckedChange = { state.setManualOffline(it) },
                    )
                }
            }

            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SettingsSectionTitle(stringResource(R.string.appearance))
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(
                        stringResource(R.string.theme),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            ThemeMode.SYSTEM to R.string.theme_system,
                            ThemeMode.LIGHT to R.string.theme_light,
                            ThemeMode.DARK to R.string.theme_dark,
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = state.settings.themeMode == mode,
                                onClick = { state.settings.themeMode = mode },
                                label = { Text(stringResource(label)) },
                            )
                        }
                    }
                }
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
                            stringResource(R.string.dynamic_color),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                            Text(
                                stringResource(R.string.dynamic_color_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(
                        checked = state.settings.dynamicColor,
                        onCheckedChange = { state.settings.dynamicColor = it },
                        enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    )
                }
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.amoled_dark),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = state.settings.amoledDark,
                        onCheckedChange = { state.settings.amoledDark = it },
                        enabled = state.settings.themeMode != ThemeMode.LIGHT,
                    )
                }
            }

            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SettingsSectionTitle(stringResource(R.string.playback))
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
                            stringResource(R.string.fade_in_out),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            stringResource(R.string.fade_duration, state.settings.fadeMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.settings.fadeEnabled,
                        onCheckedChange = { state.settings.fadeEnabled = it },
                    )
                }
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(200, 300, 500, 1000).forEach { ms ->
                        FilterChip(
                            selected = state.settings.fadeMs == ms,
                            onClick = { state.settings.fadeMs = ms },
                            label = { Text("${ms}ms") },
                            enabled = state.settings.fadeEnabled,
                        )
                    }
                }
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.crossfade),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.settings.crossfadeMs == 0,
                        onClick = { state.settings.crossfadeMs = 0 },
                        label = { Text(stringResource(R.string.crossfade_off)) },
                    )
                    listOf(2000, 4000, 6000).forEach { ms ->
                        FilterChip(
                            selected = state.settings.crossfadeMs == ms,
                            onClick = { state.settings.crossfadeMs = ms },
                            label = { Text(stringResource(R.string.crossfade_seconds, ms / 1000)) },
                        )
                    }
                }
            }
            item {
                SettingSwitchRow(
                    title = stringResource(R.string.gestures),
                    checked = state.settings.gesturesEnabled,
                    onCheckedChange = { state.settings.gesturesEnabled = it },
                )
            }
            item {
                SettingSwitchRow(
                    title = stringResource(R.string.prefetch_next),
                    checked = state.settings.prefetchNext,
                    onCheckedChange = { state.settings.prefetchNext = it },
                )
            }
            item {
                SettingSwitchRow(
                    title = stringResource(R.string.skip_silence),
                    checked = state.settings.skipSilence,
                    onCheckedChange = {
                        state.settings.skipSilence = it
                        state.player.applySkipSilence()
                    },
                )
            }
            item {
                SettingSwitchRow(
                    title = stringResource(R.string.normalize_volume),
                    checked = state.settings.normalizeVolume,
                    onCheckedChange = { state.settings.normalizeVolume = it },
                )
            }
            item {
                SettingSwitchRow(
                    title = stringResource(R.string.report_playback),
                    checked = state.settings.reportPlayback,
                    onCheckedChange = { state.settings.reportPlayback = it },
                )
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
                            stringResource(R.string.remember_position),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            stringResource(R.string.remember_position_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.settings.rememberPosition,
                        onCheckedChange = { state.settings.rememberPosition = it },
                    )
                }
            }

            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SettingsSectionTitle(stringResource(R.string.audio_effects))
            }
            item {
                Text(
                    stringResource(R.string.sound_preset),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = {
                        state.player.equalizer.setBassBoost(false)
                        state.player.equalizer.setVirtualizer(false)
                        state.player.equalizer.setLoudnessBoost(false)
                    }) { Text(stringResource(R.string.preset_off)) }
                    TextButton(onClick = {
                        state.player.equalizer.setBassBoost(true)
                        state.player.equalizer.setBassStrength(800)
                        state.player.equalizer.setVirtualizer(false)
                    }) { Text(stringResource(R.string.preset_bass)) }
                    TextButton(onClick = {
                        state.player.equalizer.setBassBoost(false)
                        state.player.equalizer.setVirtualizer(false)
                        state.player.equalizer.setLoudnessBoost(true)
                        state.player.equalizer.setLoudnessGain(400)
                    }) { Text(stringResource(R.string.preset_vocal)) }
                    TextButton(onClick = {
                        state.player.equalizer.setVirtualizer(true)
                        state.player.equalizer.setVirtualizerStrength(700)
                    }) { Text(stringResource(R.string.preset_surround)) }
                }
            }
            item {
                SettingSwitchRow(
                    title = stringResource(R.string.bass_boost),
                    checked = state.settings.bassBoostEnabled,
                    onCheckedChange = { state.player.equalizer.setBassBoost(it) },
                )
            }
            if (state.settings.bassBoostEnabled) {
                item {
                    Slider(
                        value = state.settings.bassBoostStrength / 1000f,
                        onValueChange = {
                            state.player.equalizer.setBassStrength((it * 1000).toInt())
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            item {
                SettingSwitchRow(
                    title = stringResource(R.string.virtualizer),
                    checked = state.settings.virtualizerEnabled,
                    onCheckedChange = { state.player.equalizer.setVirtualizer(it) },
                )
            }
            if (state.settings.virtualizerEnabled) {
                item {
                    Slider(
                        value = state.settings.virtualizerStrength / 1000f,
                        onValueChange = {
                            state.player.equalizer.setVirtualizerStrength((it * 1000).toInt())
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            item {
                SettingSwitchRow(
                    title = stringResource(R.string.loudness_boost),
                    checked = state.settings.loudnessBoostEnabled,
                    onCheckedChange = { state.player.equalizer.setLoudnessBoost(it) },
                )
            }
            if (state.settings.loudnessBoostEnabled) {
                item {
                    Slider(
                        value = state.settings.loudnessBoostMb / 1500f,
                        onValueChange = {
                            state.player.equalizer.setLoudnessGain((it * 1500).toInt())
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { showEqualizer = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.equalizer),
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
                SettingsSectionTitle(stringResource(R.string.auto_download))
            }
            item {
                SettingSwitchRow(
                    title = stringResource(R.string.auto_download_favorites),
                    checked = state.settings.autoDownloadFavorites,
                    onCheckedChange = { state.settings.autoDownloadFavorites = it },
                )
            }
            item {
                SettingSwitchRow(
                    title = stringResource(R.string.auto_download_playlists),
                    checked = state.settings.autoDownloadPlaylists,
                    onCheckedChange = { state.settings.autoDownloadPlaylists = it },
                )
            }
            item {
                SettingSwitchRow(
                    title = stringResource(R.string.auto_download_latest),
                    checked = state.settings.autoDownloadLatest,
                    onCheckedChange = { state.settings.autoDownloadLatest = it },
                )
            }
            item {
                SettingSwitchRow(
                    title = stringResource(R.string.auto_download_wifi_only),
                    checked = state.settings.autoDownloadWifiOnly,
                    onCheckedChange = { state.settings.autoDownloadWifiOnly = it },
                )
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(
                        stringResource(R.string.auto_download_max, state.settings.autoDownloadMaxMb),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1024, 2048, 4096, 8192).forEach { mb ->
                            FilterChip(
                                selected = state.settings.autoDownloadMaxMb == mb,
                                onClick = { state.settings.autoDownloadMaxMb = mb },
                                label = { Text(if (mb >= 1024) "${mb / 1024}G" else "${mb}M") },
                            )
                        }
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
                        onClick = {
                            AutoDownloadScheduler.runNow(context)
                            Toast.makeText(
                                context,
                                context.getString(R.string.auto_download_started),
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                    ) {
                        Text(stringResource(R.string.auto_download_now))
                    }
                }
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
                                if (ok) {
                                    context.getString(R.string.sync_done)
                                } else {
                                    state.localPlaylists.lastError
                                        ?: context.getString(R.string.sync_failed)
                                },
                                Toast.LENGTH_LONG,
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

            if (state.localPlaylists.lastError != null) {
                item {
                    Text(
                        state.localPlaylists.lastError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
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

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(
                        onClick = {
                            state.api?.let { api ->
                                state.downloads.downloadMissingLyrics(api) { written, total ->
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.download_lyrics_result,
                                            written,
                                            total,
                                        ),
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                        },
                        enabled = !state.isOffline &&
                            state.downloads.downloadedItems().isNotEmpty() &&
                            !state.downloads.isMigrating,
                    ) {
                        Text(stringResource(R.string.download_lyrics))
                    }
                    TextButton(
                        onClick = {
                            state.downloads.renameDownloads { renamed, failed ->
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.rename_downloads_result,
                                        renamed,
                                        failed,
                                    ),
                                    Toast.LENGTH_LONG,
                                ).show()
                                tick++
                            }
                        },
                        enabled = state.downloads.downloadedItems().isNotEmpty() &&
                            !state.downloads.isMigrating,
                    ) {
                        Text(stringResource(R.string.rename_downloads))
                    }
                    TextButton(
                        onClick = {
                            val removed = state.downloads.verifyDownloads()
                            Toast.makeText(
                                context,
                                if (removed > 0) {
                                    context.getString(R.string.verify_downloads_result, removed)
                                } else {
                                    context.getString(R.string.verify_downloads_ok)
                                },
                                Toast.LENGTH_SHORT,
                            ).show()
                            tick++
                        },
                    ) {
                        Text(stringResource(R.string.verify_downloads))
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
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SettingsSectionTitle(stringResource(R.string.appearance))
            }
            item {
                Text(
                    stringResource(R.string.cover_shape),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.settings.coverShape == 0,
                        onClick = { state.settings.coverShape = 0 },
                        label = { Text(stringResource(R.string.cover_square)) },
                    )
                    FilterChip(
                        selected = state.settings.coverShape == 1,
                        onClick = { state.settings.coverShape = 1 },
                        label = { Text(stringResource(R.string.cover_circle)) },
                    )
                }
            }
            item {
                val context = LocalContext.current
                val backgroundLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        runCatching {
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                            )
                        }
                        state.settings.customBackgroundUri = uri.toString()
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { backgroundLauncher.launch(arrayOf("image/*")) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.custom_background),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.settings.customBackgroundUri != null) {
                        TextButton(onClick = { state.settings.customBackgroundUri = null }) {
                            Text(stringResource(R.string.clear_custom))
                        }
                    }
                }
            }
            item { SettingsSectionTitle(stringResource(R.string.servers)) }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    val name = state.serverName.value.orEmpty()
                    val version = state.serverVersion.value.orEmpty()
                    Text(
                        stringResource(R.string.server_version) + ": " +
                            listOf(name, version).filter { it.isNotBlank() }.joinToString(" "),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    state.serverOs.value?.takeIf { it.isNotBlank() }?.let { os ->
                        Text(
                            os,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.client_version) + ": " + BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.savedServers().forEachIndexed { index, server ->
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { state.switchServer(server) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                server.userName.ifBlank { server.url },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                server.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (index == 0) {
                            Text(
                                stringResource(R.string.active_server),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { state.logout() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.add_server),
                        style = MaterialTheme.typography.bodyLarge,
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
                        .clickable { state.navigate(Screen.Theory) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.theory_basics),
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { state.navigate(Screen.VocalTraining) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.vocal_training),
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { state.navigate(Screen.EarTraining) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.ear_training),
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { state.navigate(Screen.ChordLibrary) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.chord_library),
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { state.navigate(Screen.ScaleVisualizer) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.scale_visualizer),
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { state.navigate(Screen.DrumMachine) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.drum_machine),
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { state.navigate(Screen.Metronome) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.metronome),
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { state.navigate(Screen.Tuner) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.tuner),
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { state.navigate(Screen.Practice) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.practice),
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { state.navigate(Screen.RunningRadio) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.running_radio),
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { state.navigate(Screen.ListeningStats) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.listening_stats),
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { state.navigate(Screen.SmartPlaylists) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.smart_playlists),
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { state.navigate(Screen.FeatureLibrary) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.feature_library),
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            AudioAnalysisScheduler.enqueue(context)
                            Toast.makeText(
                                context,
                                context.getString(R.string.analyze_library),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.analyze_library),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
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

    if (showEqualizer) {
        EqualizerDialog(state) { showEqualizer = false }
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

    if (editingLocalAddress) {
        AlertDialog(
            onDismissRequest = { editingLocalAddress = false },
            title = { Text(stringResource(R.string.local_address)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = localAddressInput,
                        onValueChange = { localAddressInput = it },
                        singleLine = true,
                        placeholder = { Text("http://192.168.1.100:8096") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.local_address_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    state.setLocalServerUrl(localAddressInput.trim().ifBlank { null })
                    editingLocalAddress = false
                }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingLocalAddress = false }) {
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

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun EqualizerDialog(state: AppState, onDismiss: () -> Unit) {
    val equalizer = state.player.equalizer
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.equalizer)) },
        text = {
            if (!equalizer.available) {
                Text(stringResource(R.string.equalizer_unavailable))
            } else {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.equalizer_enable),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = equalizer.enabled,
                            onCheckedChange = { equalizer.changeEnabled(it) },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.equalizer_preset),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(equalizer.presetNames) { index, name ->
                            FilterChip(
                                selected = equalizer.currentPreset == index,
                                onClick = { equalizer.applyPreset(index) },
                                label = { Text(name) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    equalizer.bandLevels.forEachIndexed { index, level ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                formatFrequency(equalizer.centerFreqs.getOrElse(index) { 0 }),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(58.dp),
                            )
                            Slider(
                                value = level.toFloat(),
                                onValueChange = { equalizer.setBandLevel(index, it.toInt()) },
                                valueRange = equalizer.minLevel.toFloat()..equalizer.maxLevel.toFloat(),
                                enabled = equalizer.enabled,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        dismissButton = {
            if (equalizer.available) {
                TextButton(onClick = { equalizer.applyPreset(0) }) {
                    Text(stringResource(R.string.equalizer_reset))
                }
            }
        },
    )
}

private fun formatFrequency(hz: Int): String =
    if (hz >= 1000) "%.1fk".format(hz / 1000.0) else "${hz}Hz"
