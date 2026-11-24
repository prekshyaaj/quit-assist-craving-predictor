package com.example.feature.tools.impl

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- Static motivational slides (same for every user) ----------

private data class Slide(val icon: ImageVector, val title: String, val body: String)

private val slides = listOf(
    Slide(
        Icons.Outlined.Timer,
        "Your body starts healing fast",
        "20 minutes after your last cigarette, your heart rate and blood pressure drop. " +
            "Within 12 hours, the carbon monoxide in your blood falls back to normal.",
    ),
    Slide(
        Icons.Outlined.TrendingUp,
        "It keeps getting better",
        "Within weeks your circulation and lung function improve. After 1 year, " +
            "your risk of heart disease is about half that of a smoker.",
    ),
    Slide(
        Icons.Outlined.Spa,
        "Every craving passes",
        "A craving usually fades within a few minutes. Breathe, drink water, walk. " +
            "Each one you get through makes the next one easier.",
    ),
    Slide(
        Icons.Outlined.Savings,
        "Your money, your choice",
        "The money that went into cigarettes adds up quickly. " +
            "Put it towards something you really want.",
    ),
    Slide(
        Icons.Outlined.Favorite,
        "Do it for you, and for them",
        "Quitting protects the people around you from second-hand smoke. " +
            "A slip is not a failure. Keep going, you've already started.",
    ),
)

/**
 * "Why I Quit?": swipeable motivation slides + the user's own reasons
 * (a note, a photo, or a voice recording), stored on the phone only.
 */
@Composable
fun WhyIQuitScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember { ReasonStore(context) }
    var reasons by remember { mutableStateOf(store.load()) }

    fun add(reason: Reason) {
        reasons = listOf(reason) + reasons
        store.save(reasons)
    }

    // --- dialogs ---
    var showTextDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Reason?>(null) }

    // --- photo picker (no storage permission needed) ---
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            val id = System.currentTimeMillis()
            val path = store.copyImage(uri, id)
            if (path != null) add(Reason(id, ReasonType.IMAGE, path = path))
            else Toast.makeText(context, "Couldn't save that photo", Toast.LENGTH_SHORT).show()
        }
    }

    // --- voice recording ---
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingId by remember { mutableStateOf(0L) }
    var recordSeconds by remember { mutableIntStateOf(0) }

    fun startRecording() {
        val id = System.currentTimeMillis()
        val file = store.newAudioFile(id)
        val r = newRecorder(context)
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            recordingId = id
            recordSeconds = 0
        } catch (_: Exception) {
            r.release()
            file.delete()
            Toast.makeText(
                context,
                "Microphone is busy. Turn off voice listening and try again.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun stopRecording() {
        val r = recorder ?: return
        val file = store.newAudioFile(recordingId)
        val ok = try {
            r.stop(); true
        } catch (_: Exception) {
            false // happens if stopped within ~1 second
        }
        r.release()
        recorder = null
        if (ok && file.exists()) {
            add(Reason(recordingId, ReasonType.AUDIO, path = file.absolutePath))
        } else {
            file.delete()
            Toast.makeText(context, "Recording too short", Toast.LENGTH_SHORT).show()
        }
    }

    val askMic = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startRecording()
        else Toast.makeText(context, "Microphone permission is needed to record", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(recorder) {
        while (recorder != null) {
            delay(1000)
            recordSeconds++
        }
    }

    // --- playback (one at a time) ---
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playingId by remember { mutableStateOf<Long?>(null) }

    fun stopPlayback() {
        player?.release()
        player = null
        playingId = null
    }

    fun play(reason: Reason) {
        stopPlayback()
        try {
            val p = MediaPlayer()
            p.setDataSource(reason.path)
            p.setOnCompletionListener { stopPlayback() }
            p.prepare()
            p.start()
            player = p
            playingId = reason.id
        } catch (_: Exception) {
            Toast.makeText(context, "Couldn't play this recording", Toast.LENGTH_SHORT).show()
        }
    }

    // Release mic / player when leaving the page.
    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            recorder?.let { r ->
                try { r.stop() } catch (_: Exception) {}
                r.release()
            }
        }
    }

    BackHandler {
        if (recorder != null) stopRecording()
        stopPlayback()
        onBack()
    }

    // ---------------- UI ----------------
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                if (recorder != null) stopRecording()
                stopPlayback()
                onBack()
            }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Text("Why I Quit?", style = MaterialTheme.typography.titleLarge)
        }

        SlidesPager()

        Text(
            text = "My reasons",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = "Save what keeps you going: a note, a photo of someone you love, " +
                "or a voice message to your future self.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (recorder != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Recording…  %d:%02d".format(recordSeconds / 60, recordSeconds % 60),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { stopRecording() }) {
                        Icon(Icons.Outlined.Stop, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Stop")
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AddButton("Note", Icons.Outlined.TextFields, Modifier.weight(1f)) {
                    showTextDialog = true
                }
                AddButton("Photo", Icons.Outlined.Image, Modifier.weight(1f)) {
                    pickImage.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }
                AddButton("Voice", Icons.Outlined.Mic, Modifier.weight(1f)) {
                    stopPlayback()
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) startRecording() else askMic.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }

        if (reasons.isEmpty()) {
            Text(
                text = "Nothing saved yet. Add your first reason above.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        reasons.forEach { reason ->
            ReasonCard(
                reason = reason,
                isPlaying = playingId == reason.id,
                onPlay = { if (playingId == reason.id) stopPlayback() else play(reason) },
                onDelete = { pendingDelete = reason },
            )
        }
    }

    if (showTextDialog) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text("Why are you quitting?") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("e.g. I want to play football with my kids") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank(),
                    onClick = {
                        add(Reason(System.currentTimeMillis(), ReasonType.TEXT, text = text.trim()))
                        showTextDialog = false
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false }) { Text("Cancel") }
            },
        )
    }

    pendingDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this reason?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    if (playingId == r.id) stopPlayback()
                    store.deleteFile(r)
                    reasons = reasons.filterNot { it.id == r.id }
                    store.save(reasons)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SlidesPager() {
    val pagerState = rememberPagerState(pageCount = { slides.size })
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val s = slides[page]
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                ) {
                    Icon(
                        imageVector = s.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(40.dp),
                    )
                    Text(
                        text = s.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                    )
                    Text(
                        text = s.body,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 10.dp),
        ) {
            repeat(slides.size) { i ->
                Box(
                    Modifier
                        .size(if (i == pagerState.currentPage) 10.dp else 7.dp)
                        .clip(CircleShape)
                        .background(
                            if (i == pagerState.currentPage) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                        ),
                )
            }
        }
        Text(
            text = "Swipe for more",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun AddButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    FilledTonalButton(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, maxLines = 1)
    }
}

@Composable
private fun ReasonCard(
    reason: Reason,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when (reason.type) {
                        ReasonType.TEXT -> "Note"
                        ReasonType.IMAGE -> "Photo"
                        ReasonType.AUDIO -> "Voice message"
                    } + " · " + formatReasonDate(reason.id),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                }
            }
            Box(Modifier.padding(end = 12.dp)) {
                when (reason.type) {
                    ReasonType.TEXT -> Text(
                        text = reason.text,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    ReasonType.IMAGE -> ReasonImage(reason.path)
                    ReasonType.AUDIO -> FilledTonalButton(onClick = onPlay) {
                        Icon(
                            if (isPlaying) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (isPlaying) "Stop" else "Play")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReasonImage(path: String) {
    val bitmap by produceState<Bitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { loadScaledBitmap(path, maxSide = 1080) }
    }
    val b = bitmap
    if (b != null) {
        Image(
            bitmap = b.asImageBitmap(),
            contentDescription = "My reason",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
    } else {
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface),
        )
    }
}

/** Decodes a downsized bitmap (keeps memory low) and applies the photo's EXIF rotation. */
private fun loadScaledBitmap(path: String, maxSide: Int): Bitmap? {
    if (!File(path).exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) {
        sample *= 2
    }
    val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return null
    val degrees = try {
        when (ExifInterface(path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (_: Exception) {
        0f
    }
    if (degrees == 0f) return bmp
    val m = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
}

@Suppress("DEPRECATION")
private fun newRecorder(context: Context): MediaRecorder =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()

private fun formatReasonDate(ms: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(ms))
