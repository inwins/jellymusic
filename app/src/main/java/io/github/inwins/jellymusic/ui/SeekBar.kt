package io.github.inwins.jellymusic.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.inwins.jellymusic.R
import io.github.inwins.jellymusic.util.formatDuration

/**
 * Slim, Finamp-style seek bar: a thin rounded track with buffered progress,
 * a small thumb that grows while dragging, and a floating time bubble.
 * Elapsed time is shown on the left and remaining time on the right.
 */
@Composable
fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    accent: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (durationMs <= 0L) return
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val positionFraction = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    val fraction = dragFraction ?: positionFraction
    val bufferedFraction = (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f)
    val displayMs = (fraction * durationMs).toLong()
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
    val bufferedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.34f)
    val timeStyle = MaterialTheme.typography.labelMedium.copy(
        fontFeatureSettings = "tnum",
        fontWeight = FontWeight.Medium,
    )
    val surfaceColor = MaterialTheme.colorScheme.surface
    val seekDescription = stringResource(R.string.seek_bar)

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val thumbX = maxWidth * fraction
        val bubbleWidth = 64.dp
        val bubbleX = (thumbX - bubbleWidth / 2)
            .coerceIn(0.dp, (maxWidth - bubbleWidth).coerceAtLeast(0.dp))

        Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(30.dp)) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .pointerInput(durationMs) {
                        detectTapGestures { offset ->
                            val target = (offset.x / size.width).coerceIn(0f, 1f)
                            onSeek((target * durationMs).toLong())
                        }
                    }
                    .pointerInput(durationMs) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                dragFraction =
                                    (offset.x / size.width).coerceIn(0f, 1f)
                            },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                dragFraction =
                                    (change.position.x / size.width).coerceIn(0f, 1f)
                            },
                            onDragEnd = {
                                dragFraction?.let { value ->
                                    onSeek((value * durationMs).toLong())
                                }
                                dragFraction = null
                            },
                            onDragCancel = { dragFraction = null },
                        )
                    }
                    .semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                        contentDescription = seekDescription
                    },
            ) {
                val centerY = size.height / 2f
                val trackHeight = 4.dp.toPx()
                val radius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(0f, centerY - trackHeight / 2f),
                    size = Size(size.width, trackHeight),
                    cornerRadius = radius,
                )
                if (bufferedFraction > 0f) {
                    drawRoundRect(
                        color = bufferedColor,
                        topLeft = Offset(0f, centerY - trackHeight / 2f),
                        size = Size(size.width * bufferedFraction, trackHeight),
                        cornerRadius = radius,
                    )
                }
                if (fraction > 0f) {
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(0f, centerY - trackHeight / 2f),
                        size = Size(size.width * fraction, trackHeight),
                        cornerRadius = radius,
                    )
                }
                val thumbRadius = if (dragFraction != null) 8.dp.toPx() else 6.dp.toPx()
                val x = size.width * fraction
                drawCircle(
                    color = surfaceColor,
                    radius = thumbRadius + 2.dp.toPx(),
                    center = Offset(x, centerY),
                )
                drawCircle(
                    color = accent,
                    radius = thumbRadius,
                    center = Offset(x, centerY),
                )
            }

            if (dragFraction != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .offset(x = bubbleX, y = (-22).dp)
                        .width(bubbleWidth)
                        .height(26.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            formatDuration(displayMs),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
        ) {
            Text(
                formatDuration(displayMs),
                style = timeStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "-" + formatDuration((durationMs - displayMs).coerceAtLeast(0L)),
                style = timeStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        }
    }
}
