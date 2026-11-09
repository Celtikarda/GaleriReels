package com.celtikarda.galerireels.ui

import com.celtikarda.galerireels.R
import com.celtikarda.galerireels.str
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.celtikarda.galerireels.EditQuality
import com.celtikarda.galerireels.EditResult
import com.celtikarda.galerireels.SeekPreview
import com.celtikarda.galerireels.VideoItem
import com.celtikarda.galerireels.exportEdited
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Videodan bir parça kesip (isteğe bağlı küçülterek) yeni dosya olarak kaydetme ekranı.
 * Orijinal video silinmez. Kaydedilince [onSaved], hata olursa ekranda yazar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimDialog(item: VideoItem, onSaved: (Uri) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val duration = item.durationMs.coerceAtLeast(1L)
    // Varsayılan: videonun tamamı; kullanıcı iki ucu çeker
    var range by remember { mutableStateOf(0f..duration.toFloat()) }
    val qualities = remember(item) { EditQuality.entries.filter { it.usefulFor(item) } }
    var quality by remember { mutableStateOf(EditQuality.ORIGINAL) }
    var job by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    val exporting = job != null

    // Başlangıç ve bitiş kareleri (sürüklerken kısa beklemeyle güncellenir)
    val preview = remember(item.uri) { SeekPreview(context, item.uri) }
    DisposableEffect(preview) { onDispose { preview.release() } }
    var startFrame by remember { mutableStateOf<Bitmap?>(null) }
    var endFrame by remember { mutableStateOf<Bitmap?>(null) }
    val startMs = range.start.toLong()
    val endMs = range.endInclusive.toLong()
    LaunchedEffect(startMs / 250) {
        delay(120)
        preview.frameAt(startMs)?.let { startFrame = it }
    }
    LaunchedEffect(endMs / 250) {
        delay(120)
        preview.frameAt((endMs - 100).coerceAtLeast(0L))?.let { endFrame = it }
    }

    fun nudge(start: Boolean, deltaMs: Long) {
        val minLen = 500f
        range = if (start) {
            (range.start + deltaMs).coerceIn(0f, range.endInclusive - minLen)..range.endInclusive
        } else {
            range.start..(range.endInclusive + deltaMs).coerceIn(range.start + minLen, duration.toFloat())
        }
    }

    Dialog(
        onDismissRequest = { if (!exporting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Column(
            Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(appColors.card)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text(str(R.string.trim_compress), color = appColors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(item.name, color = appColors.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FramePreview(str(R.string.start_point), startFrame, formatDuration(startMs), item, Modifier.weight(1f))
                FramePreview(str(R.string.end_point), endFrame, formatDuration(endMs), item, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))

            RangeSlider(
                value = range,
                onValueChange = { if (!exporting && it.endInclusive - it.start >= 500f) range = it },
                valueRange = 0f..duration.toFloat(),
                enabled = !exporting,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                NudgeButtons(enabled = !exporting, onNudge = { nudge(true, it) })
                Spacer(Modifier.weight(1f))
                Text(
                    str(R.string.duration_x, formatDuration(endMs - startMs)),
                    color = appColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                NudgeButtons(enabled = !exporting, onNudge = { nudge(false, it) })
            }
            Spacer(Modifier.height(14.dp))

            Text(str(R.string.quality), color = appColors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                qualities.forEach { q ->
                    FilterChip(
                        selected = quality == q,
                        onClick = { if (!exporting) quality = q },
                        label = { Text(if (q == EditQuality.ORIGINAL) str(R.string.quality_original) else q.title) },
                    )
                }
            }
            Text(
                str(R.string.estimated_size, formatSize(quality.estimateBytes(item, endMs - startMs)), formatSize(item.sizeBytes)),
                color = appColors.textSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                str(R.string.trim_save_note),
                color = appColors.textSecondary,
                fontSize = 12.sp,
            )

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(str(R.string.save_failed, it), color = Color(0xFFED4956), fontSize = 13.sp)
            }

            Spacer(Modifier.height(16.dp))
            if (exporting) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        str(R.string.saving_pct, (progress * 100).toInt()),
                        color = appColors.textPrimary,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        job?.cancel()
                        job = null
                        progress = 0f
                    }) { Text(str(R.string.stop)) }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(str(R.string.dismiss)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        error = null
                        progress = 0f
                        job = scope.launch {
                            val result = exportEdited(context, item, startMs, endMs, quality) { progress = it }
                            job = null
                            when (result) {
                                is EditResult.Saved -> onSaved(result.uri)
                                is EditResult.Failed -> error = result.reason
                            }
                        }
                    }) { Text(str(R.string.save)) }
                }
            }
        }
    }
}

@Composable
private fun FramePreview(label: String, frame: Bitmap?, time: String, item: VideoItem, modifier: Modifier) {
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (frame != null) {
                Image(frame.asImageBitmap(), contentDescription = label, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            } else {
                Text("…", color = Color.White.copy(alpha = 0.5f))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("$label · $time", color = appColors.textPrimary, fontSize = 13.sp)
    }
}

/** İnce ayar: yarım saniye geri / ileri */
@Composable
private fun NudgeButtons(enabled: Boolean, onNudge: (Long) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(
            onClick = { onNudge(-500) },
            enabled = enabled,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
            modifier = Modifier.height(32.dp),
        ) { Text("−½", fontSize = 13.sp) }
        OutlinedButton(
            onClick = { onNudge(500) },
            enabled = enabled,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
            modifier = Modifier.height(32.dp),
        ) { Text("+½", fontSize = 13.sp) }
    }
}
