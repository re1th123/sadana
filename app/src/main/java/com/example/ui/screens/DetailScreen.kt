package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Category
import com.example.model.Exercise
import com.example.model.MediaType
import com.example.model.ExerciseMedia
import com.example.model.ExerciseMode
import com.example.model.getLocalizedInstructions
import com.example.model.getLocalizedName
import com.example.ui.components.LoopingMediaView
import com.example.viewmodel.ExerciseViewModel
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    exerciseId: String,
    viewModel: ExerciseViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exercises by viewModel.exercises.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val profile by viewModel.userProfile.collectAsState()
    val languageCode = remember(profile) { profile.languageCode }
    val exercise = exercises.find { it.id == exerciseId } ?: return

    val scrollState = rememberScrollState()

    // Timer status state
    var isPlaying by remember { mutableStateOf(false) }
    var secondsRemaining by remember(exercise.id) { mutableStateOf(exercise.defaultDurationSeconds) }

    // Favorite state toggle (local aesthetic)
    var isFavorite by remember { mutableStateOf(false) }

    // Dialogue trigger for editing media and deletion
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showingEditMedia by remember { mutableStateOf(false) }
    var editMediaType by remember(exercise.id) { mutableStateOf(exercise.media.mediaType) }
    var editMediaUrl by remember(exercise.id) { mutableStateOf(exercise.media.mediaUri) }
    var editMode by remember(exercise.id) { mutableStateOf(exercise.mode) }

    // Media picker launcher
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            editMediaUrl = it.toString()
        }
    }

    // Pulsing animations for breathing loop indicator
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_breathing")
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Timer logic coroutine
    LaunchedEffect(isPlaying, secondsRemaining) {
        if (isPlaying && secondsRemaining > 0) {
            delay(1000)
            secondsRemaining -= 1
        } else if (secondsRemaining == 0) {
            isPlaying = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Examine Flow",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = 1.sp
                        )
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { isFavorite = !isFavorite },
                        modifier = Modifier.testTag("favorite_button")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Toggle favorite",
                            tint = if (isFavorite) Color(0xFFB08C75) else MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = { showDeleteConfirmDialog = true },
                        modifier = Modifier.testTag("delete_exercise_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete exercise from library",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }

                    if (showDeleteConfirmDialog) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirmDialog = false },
                            title = { Text("Delete Exercise?", fontWeight = FontWeight.Bold) },
                            text = { Text("Are you sure you want to permanently delete '${exercise.name}'? This will automatically remove it from all routines.", fontSize = 14.sp) },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        viewModel.deleteExercise(exercise.id)
                                        showDeleteConfirmDialog = false
                                        onBackClick() // Exit detail screen
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("confirm_delete_exercise_btn")
                                ) {
                                    Text("Delete")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                                    Text("Cancel")
                                }
                            },
                            shape = RoundedCornerShape(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
        ) {
            // Elegant Visual Banner Block with Ambient Breath Rings OR live looping media
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .drawBehind {
                        // Background gradient
                        if (exercise.media.mediaUri.isBlank()) {
                            val drawGradient = Brush.verticalGradient(
                                colors = when (exercise.categoryId) {
                                    "neck", "head" -> listOf(
                                        Color(0xFFE2ECE2),
                                        Color(0xFFD3E7D3)
                                    )
                                    "eyes" -> listOf(Color(0xFFE1EBF2), Color(0xFFCFDDE7))
                                    "hands", "arms" -> listOf(
                                        Color(0xFFF7ECE6),
                                        Color(0xFFECD7CC)
                                    )
                                    "abs", "hips" -> listOf(
                                        Color(0xFFEDE7F2),
                                        Color(0xFFDDD2E8)
                                    )
                                    "legs" -> listOf(Color(0xFFF6F7E6), Color(0xFFEBECC8))
                                    "full_body" -> listOf(
                                        Color(0xFFEDF2F0),
                                        Color(0xFFDBE7E4)
                                    )
                                    else -> listOf(Color(0xFFEEEFEA), Color(0xFFEEEFEA))
                                }
                            )
                            drawRect(brush = drawGradient)

                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val radiusBase = size.minDimension * 0.35f

                            // Inner soft breathing core
                            drawCircle(
                                color = Color(0xFF5A725A).copy(alpha = 0.12f),
                                radius = radiusBase * breathingScale,
                                center = Offset(cx, cy)
                            )

                            // Symmetrical dynamic zen orbits
                            for (i in 1..4) {
                                val r = radiusBase * (0.25f * i + 0.15f)
                                drawCircle(
                                    color = Color(0xFFB08C75).copy(alpha = 0.05f * (5 - i)),
                                    radius = r,
                                    center = Offset(cx, cy),
                                    style = Stroke(width = 1.5.dp.toPx())
                                )
                            }

                            // Symmetrical outer dots representing the pathways
                            val numDots = 8
                            for (d in 0 until numDots) {
                                val angle = (d * (360f / numDots)) * PI.toFloat() / 180f
                                val dotRadius = radiusBase * 0.9f
                                drawCircle(
                                    color = Color(0xFF5A725A).copy(alpha = 0.25f),
                                    radius = 4f,
                                    center = Offset(
                                        cx + dotRadius * cos(angle),
                                        cy + dotRadius * sin(angle)
                                    )
                                )
                            }
                        }
                    }
            ) {
                if (exercise.media.mediaUri.isNotBlank()) {
                    LoopingMediaView(
                        media = exercise.media,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Edit Media Overlay Button on the top right
                IconButton(
                    onClick = { showingEditMedia = true },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .padding(16.dp)
                        .size(40.dp)
                        .align(Alignment.TopEnd)
                        .testTag("edit_media_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit demonstration media",
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Category Chip Overlay
                Box(
                    modifier = Modifier
                        .padding(20.dp)
                        .align(Alignment.BottomStart)
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    val matchedCategory = categories.find { it.id == exercise.categoryId }
                    Text(
                        text = (matchedCategory?.name ?: "YOGA").uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main Content Body containing exercise name and tag chips
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Text(
                    text = exercise.getLocalizedName(languageCode),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 0.5.sp,
                        lineHeight = 36.sp
                    ),
                    modifier = Modifier.testTag("exercise_title")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Tag Chips Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    exercise.purposeTags.forEach { tag ->
                        Box(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = tag.displayName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            )
                        }
                    }

                    // Total expected duration support badge
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Prescribed: ${exercise.defaultDurationSeconds}s",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Interactive Countdown Timer Widget Card
                Card(
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Display Timer
                        Column {
                            Text(
                                text = "PRACTICE TIMER",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // Beautiful animated timer reading
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = String.format("%02d:%02d", secondsRemaining / 60, secondsRemaining % 60),
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 36.sp
                                    ),
                                    modifier = Modifier.testTag("timer_text")
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isPlaying) "active" else "paused",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        fontWeight = FontWeight.Medium
                                    ),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                        }

                        // Play/Pause and Reset Buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Reset
                            IconButton(
                                onClick = {
                                    isPlaying = false
                                    secondsRemaining = exercise.defaultDurationSeconds
                                },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("timer_reset_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reset timer",
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Play/Pause
                            FilledIconButton(
                                onClick = { isPlaying = !isPlaying },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (isPlaying) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .size(54.dp)
                                    .testTag("timer_play_pause_btn")
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause stretch" else "Start stretch",
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Structured instructions area
                Text(
                    text = "STEP-BY-STEP ALIGNMENT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Instructional card with nice spacing
                Card(
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 40.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        // Breakdown sentence blocks for clean readable blocks
                        val rawInstructions = exercise.getLocalizedInstructions(languageCode)
                        val paragraphSegments = if (languageCode == "hi") {
                            rawInstructions.split(Regex("[।.]\\s*"))
                        } else {
                            rawInstructions.split(". ")
                        }
                        paragraphSegments.forEachIndexed { index, paragraph ->
                            if (paragraph.trim().isNotBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    val primaryColor = MaterialTheme.colorScheme.primary
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = primaryColor
                                        ),
                                        modifier = Modifier
                                            .size(24.dp)
                                            .drawBehind {
                                                drawCircle(
                                                    color = primaryColor.copy(alpha = 0.12f),
                                                    radius = size.minDimension / 2f
                                                )
                                            }
                                            .wrapContentSize(Alignment.Center)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = paragraph.trim().let { if (it.endsWith(".")) it else "$it." },
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurface,
                                            lineHeight = 22.sp,
                                            fontWeight = FontWeight.Normal
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- REPLACEMENT MEDIA EDITING FLOW ---
    if (showingEditMedia) {
        AlertDialog(
            onDismissRequest = { showingEditMedia = false },
            title = {
                Text(
                    text = "Edit Exercise Media",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Customize visual demonstrations for this yoga poses. Supports normal picture, looping GIF animation, or mp4 video streams.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )

                    // Mode select
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Exercise Mode *",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ExerciseMode.entries.toTypedArray().forEach { mode ->
                                val selected = editMode == mode
                                FilterChip(
                                    selected = selected,
                                    onClick = { editMode = mode },
                                    label = {
                                        Text(
                                            text = when (mode) {
                                                ExerciseMode.YOGA -> "🧘 Yoga"
                                                ExerciseMode.GYM -> "🏋️ Gym"
                                            }
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).testTag("edit_mode_chip_${mode.name.lowercase()}")
                                )
                            }
                        }
                    }

                    // Pick media type
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MediaType.entries.toTypedArray().forEach { type ->
                            FilterChip(
                                selected = editMediaType == type,
                                onClick = {
                                    editMediaType = type
                                    // Set automatic serene test URLs for ease of use
                                    editMediaUrl = when (type) {
                                        MediaType.IMAGE -> "https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?q=80&w=600"
                                        MediaType.GIF -> "https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExbDVqbm13MnRreDVqYzJ1bzFqMWs4MnU2bTNqdDB3MG5lOTd4YXk0ayZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/Vz9ek7jhXYrMA/giphy.gif"
                                        MediaType.VIDEO -> "https://assets.mixkit.co/videos/preview/mixkit-forest-stream-in-the-sunlight-529-large.mp4"
                                    }
                                },
                                label = {
                                    Text(
                                        when (type) {
                                            MediaType.IMAGE -> "Picture"
                                            MediaType.GIF -> "GIF Loop"
                                            MediaType.VIDEO -> "Video"
                                        }
                                    )
                                },
                                modifier = Modifier.weight(1f).testTag("edit_media_type_chip_${type.name.lowercase()}")
                            )
                        }
                    }

                    // Calming Presets help users play immediately
                    Text(
                        text = "Select Calm Preset:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val presets = when (editMediaType) {
                            MediaType.IMAGE -> listOf(
                                "Mindful" to "https://images.unsplash.com/photo-1506126613408-eca07ce68773?q=80&w=600",
                                "Stretches" to "https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?q=80&w=600"
                            )
                            MediaType.GIF -> listOf(
                                "Pulse" to "https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExbDVqbm13MnRreDVqYzJ1bzFqMWs4MnU2bTNqdDB3MG5lOTd4YXk0ayZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/Vz9ek7jhXYrMA/giphy.gif",
                                "Relax" to "https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExMnB2a3g0eWZ6dzlmcWVvdmlxdzkwMW16djNhdzZ4MGdycDB2Y24waSZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/8vF8sR9Sscf7O/giphy.gif"
                            )
                            MediaType.VIDEO -> listOf(
                                "Sunlight" to "https://assets.mixkit.co/videos/preview/mixkit-forest-stream-in-the-sunlight-529-large.mp4",
                                "Waterfall" to "https://assets.mixkit.co/videos/preview/mixkit-waterfall-in-forest-2213-large.mp4"
                            )
                        }

                        presets.forEach { (label, url) ->
                            Button(
                                onClick = { editMediaUrl = url },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Native media picker trigger
                    Button(
                        onClick = {
                            val mimeType = when (editMediaType) {
                                MediaType.IMAGE -> "image/*"
                                MediaType.GIF -> "image/gif"
                                MediaType.VIDEO -> "video/*"
                            }
                            mediaPickerLauncher.launch(mimeType)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("edit_media_picker_button")
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (editMediaType) {
                                MediaType.IMAGE -> "Choose Image from Device"
                                MediaType.GIF -> "Choose GIF from Device"
                                MediaType.VIDEO -> "Choose Video from Device"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Direct connection URL input
                    OutlinedTextField(
                        value = editMediaUrl,
                        onValueChange = { editMediaUrl = it },
                        label = { Text("Connection Link or Device URI") },
                        placeholder = { Text("https://... or content://...") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("edit_media_url_input")
                    )

                    // Preview nested in prompt card
                    if (editMediaUrl.isNotBlank()) {
                        Text(
                            text = "Live Preview:",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            LoopingMediaView(
                                media = ExerciseMedia(editMediaType, editMediaUrl),
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updatedEx = exercise.copy(
                            media = ExerciseMedia(editMediaType, editMediaUrl),
                            mode = editMode
                        )
                        viewModel.updateExercise(updatedEx)
                        showingEditMedia = false
                    },
                    modifier = Modifier.testTag("save_media_button")
                ) {
                    Text("Save Media")
                }
            },
            dismissButton = {
                TextButton(onClick = { showingEditMedia = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
