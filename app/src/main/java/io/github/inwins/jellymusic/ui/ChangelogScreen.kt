package io.github.inwins.jellymusic.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.inwins.jellymusic.AppState
import io.github.inwins.jellymusic.R

@Composable
fun ChangelogScreen(state: AppState) {
    val context = LocalContext.current
    val content = remember {
        runCatching {
            context.resources.openRawResource(R.raw.changelog)
                .bufferedReader()
                .use { it.readText() }
        }.getOrDefault("")
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.changelog), onBack = { state.back() })
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                content,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}