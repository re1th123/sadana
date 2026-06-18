package com.example.ui.screens

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import java.util.Locale
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.Category
import com.example.model.Exercise
import com.example.model.getLocalizedInstructions
import com.example.model.getLocalizedName
import com.example.model.Shortcut
import com.example.ui.components.LoopingMediaView
import com.example.viewmodel.ExerciseViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class FlattenedPracticeStep(
    val exercise: Exercise,
    val durationSeconds: Int,
    val repeatCount: Int,
    val showDoneButton: Boolean,
    val parentShortcutName: String
)

fun flattenShortcut(
    scId: String,
    allShortcuts: List<Shortcut>,
    allExercises: List<Exercise>,
    visited: Set<String> = emptySet(),
    sessionShowDoneButton: Boolean = false
): List<FlattenedPracticeStep> {
    if (visited.contains(scId)) return emptyList() // safety cycle check
    val sc = allShortcuts.find { it.id == scId } ?: return emptyList()
    val list = mutableListOf<FlattenedPracticeStep>()
    for (item in sc.exercises) {
        if (item.nestedShortcutId != null) {
            list.addAll(flattenShortcut(item.nestedShortcutId, allShortcuts, allExercises, visited + scId, sessionShowDoneButton))
        } else {
            val exe = allExercises.find { it.id == item.exerciseId }
            if (exe != null) {
                list.add(
                    FlattenedPracticeStep(
                        exercise = exe,
                        durationSeconds = item.customDurationSeconds,
                        repeatCount = item.repeatCount,
                        showDoneButton = sessionShowDoneButton,
                        parentShortcutName = sc.name
                    )
                )
            }
        }
    }
    return list
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeModeScreen(
    shortcutId: String,
    viewModel: ExerciseViewModel,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val shortcuts by viewModel.shortcuts.collectAsState()
    val allExercises by viewModel.exercises.collectAsState()
    val categories by viewModel.categories.collectAsState()

    val bgSoundEnabled by viewModel.bgSoundEnabled.collectAsState()
    val bgSoundType by viewModel.bgSoundType.collectAsState()
    val keepScreenAwake by viewModel.keepScreenAwake.collectAsState()
    val countdownLength by viewModel.countdownLength.collectAsState()
    val profile by viewModel.userProfile.collectAsState()
    val languageCode = remember(profile) { profile.languageCode }

    val shortcut = remember(shortcutId, shortcuts) {
        shortcuts.find { it.id == shortcutId }
    }

    val sessionShowDoneButton = remember(shortcut) {
        shortcut?.completionMode == "DONE"
    }

    // Flatten shortcuts recursively for playback
    val flattenedSteps = remember(shortcutId, shortcuts, allExercises, sessionShowDoneButton) {
        flattenShortcut(shortcutId, shortcuts, allExercises, sessionShowDoneButton = sessionShowDoneButton)
    }

    // Handle invalid routine fail-safe
    if (shortcut == null || flattenedSteps.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Routine not found or empty.", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onCloseClick) {
                    Text("Go Back")
                }
            }
        }
        return
    }

    // Active state indexes & playback
    val scope = rememberCoroutineScope()
    var currentIndex by remember { mutableIntStateOf(0) }
    val currentStep = flattenedSteps.getOrNull(currentIndex)
    val currentExercise = currentStep?.exercise

    var currentRepeat by remember(currentIndex, shortcutId) { mutableIntStateOf(1) }

    var secondsRemaining by remember(currentIndex, shortcutId) {
        mutableIntStateOf(currentStep?.durationSeconds ?: 0)
    }
    var isPlaying by remember { mutableStateOf(true) }
    var totalTimeSpentSeconds by remember { mutableIntStateOf(0) }
    var exercisesCompletedCount by remember { mutableIntStateOf(0) }

    // Session completion state
    var isSessionComplete by remember { mutableStateOf(false) }
    var hasLoggedSession by remember { mutableStateOf(false) }

    val coveredCategoryIds = remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(currentStep?.exercise?.categoryId) {
        currentStep?.exercise?.categoryId?.let { catId ->
            coveredCategoryIds.value = coveredCategoryIds.value + catId
        }
    }

    val categoriesSnapshotValue = remember(coveredCategoryIds.value, categories) {
        coveredCategoryIds.value.mapNotNull { catId ->
            categories.find { it.id == catId }?.name
        }.joinToString(",")
    }

    LaunchedEffect(isSessionComplete) {
        if (isSessionComplete && !hasLoggedSession) {
            viewModel.logPracticeSession(
                shortcutId = shortcutId,
                shortcutNameSnapshot = shortcut?.name ?: "Unknown Routine",
                actualDurationSeconds = totalTimeSpentSeconds,
                categoriesSnapshot = categoriesSnapshotValue
            )
            hasLoggedSession = true
        }
    }

    // Keep Screen Awake Support
    DisposableEffect(keepScreenAwake) {
        fun findActivity(ctx: Context): android.app.Activity? {
            var currentContext = ctx
            while (currentContext is android.content.ContextWrapper) {
                if (currentContext is android.app.Activity) {
                    return currentContext
                }
                currentContext = currentContext.baseContext
            }
            return null
        }

        if (keepScreenAwake) {
            val activity = findActivity(context)
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose {
                activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        } else {
            onDispose {}
        }
    }

    // Background Ambient Music Playback Loops
    DisposableEffect(bgSoundEnabled, bgSoundType, isSessionComplete) {
        if (bgSoundEnabled && !isSessionComplete) {
            val job = scope.launch {
                val file = com.example.util.AmbientSoundManager.getAudioFile(context, bgSoundType)
                com.example.util.AmbientSoundManager.startPlaying(context, file)
            }
            onDispose {
                job.cancel()
                com.example.util.AmbientSoundManager.stopPlaying()
            }
        } else {
            com.example.util.AmbientSoundManager.stopPlaying()
            onDispose {}
        }
    }

    // Intermittent flash visual effect trigger for transitions
    var showFlashEffect by remember { mutableStateOf(false) }

    // Confirm exit dialog state
    var showExitConfirmDialog by remember { mutableStateOf(false) }

    // Text-to-speech initialization and shutdown lifecycle
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsInitialized by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val speech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsInitialized = true
            }
        }
        speech.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                com.example.util.AmbientSoundManager.duckVolume()
            }
            override fun onDone(utteranceId: String?) {
                com.example.util.AmbientSoundManager.restoreVolume()
            }
            override fun onError(utteranceId: String?) {
                com.example.util.AmbientSoundManager.restoreVolume()
            }
        })
        tts = speech
        onDispose {
            speech.stop()
            speech.shutdown()
        }
    }

    LaunchedEffect(languageCode, ttsInitialized) {
        if (ttsInitialized) {
            val locale = if (languageCode == "hi") Locale("hi", "IN") else Locale.US
            tts?.language = locale
        }
    }

    LaunchedEffect(currentIndex, ttsInitialized) {
        if (ttsInitialized && currentExercise != null) {
            val nameToSpeak = currentExercise.getLocalizedName(languageCode)
            tts?.speak(nameToSpeak, TextToSpeech.QUEUE_FLUSH, null, "exercise_start")
        }
    }

    LaunchedEffect(secondsRemaining, ttsInitialized) {
        if (ttsInitialized && secondsRemaining in 1..countdownLength) {
            tts?.speak(secondsRemaining.toString(), TextToSpeech.QUEUE_FLUSH, null, "countdown_$secondsRemaining")
        }
    }

    // Pulsing/Breathing animations for geometric art centerpiece
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_breathing_practice")
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Trigger transition effects (Sound beeps + vibration haptic cues)
    fun playTransitionAlert() {
        try {
            // Sound cue: Soothing CDMA dual-pip tone
            val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 200)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            // Android vibration alert
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(
                            250,
                            VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(250)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Reset loop function for restart option
    fun restartSession() {
        currentIndex = 0
        secondsRemaining = flattenedSteps.firstOrNull()?.durationSeconds ?: 0
        currentRepeat = 1
        isPlaying = true
        totalTimeSpentSeconds = 0
        exercisesCompletedCount = 0
        isSessionComplete = false
    }

    // Timing countdown loop coroutine
    LaunchedEffect(isPlaying, secondsRemaining, isSessionComplete) {
        if (currentStep == null) return@LaunchedEffect
        if (!isSessionComplete && isPlaying) {
            if (secondsRemaining > 0) {
                delay(1000)
                secondsRemaining -= 1
                totalTimeSpentSeconds += 1
            } else {
                // timer is at or below zero
                if (currentStep.showDoneButton) {
                    // "Done" button option: keep counting past zero (negative secondsRemaining)
                    delay(1000)
                    secondsRemaining -= 1
                    totalTimeSpentSeconds += 1
                } else {
                    // "Auto Proceed" option: auto-advance
                    playTransitionAlert()

                    // Trigger Visual Flash Pulse Overlay
                    showFlashEffect = true

                    if (currentRepeat < currentStep.repeatCount) {
                        // Restart same exercise timer with durationSeconds
                        currentRepeat += 1
                        secondsRemaining = currentStep.durationSeconds
                        delay(300)
                        showFlashEffect = false
                    } else {
                        // Done with all repeats
                        exercisesCompletedCount += 1

                        if (currentIndex < flattenedSteps.size - 1) {
                            // Auto Advance
                            currentIndex += 1
                            delay(300)
                            showFlashEffect = false
                        } else {
                            // Complete Routine
                            delay(300)
                            showFlashEffect = false
                            isSessionComplete = true
                        }
                    }
                }
            }
        }
    }

    // --- IMMERSIVE VIEWPORT BODY LAYOUT ---
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.05f)
                    )
                )
            )
    ) {
        if (isSessionComplete) {
            // --- "SESSION COMPLETE" INTERACTION GRID ---
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Large polished completion star decoration representing full wellness
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .testTag("session_complete_card")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stars,
                        contentDescription = "Success Star",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(72.dp)
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = "Congratulate Yourself!",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                )

                Text(
                    text = "You have successfully finished your custom mind-body flow. Savor this peaceful state and carry it through your day.",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 24.sp,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Stats Dashboard Cards Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Exercises finished card
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "PRACTICES",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$exercisesCompletedCount",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Total Elapsed Seconds Finished Card
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "TOTAL TIME",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            val mins = totalTimeSpentSeconds / 60
                            val secs = totalTimeSpentSeconds % 60
                            val displayTime = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"

                            Text(
                                text = displayTime,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = 24.sp
                                ),
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                // Bottom Action buttons
                Button(
                    onClick = { restartSession() },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("restart_session_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Restart Session", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onCloseClick,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("exit_session_complete_button")
                ) {
                    Text("Return to Shortcuts", fontWeight = FontWeight.Bold)
                }
            }
        } else {
            // --- ACTIVE PRACTICING WORKSPACE VIEW ---
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                // Miniature Round Icon representer for current running Shortcut/Routine
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                ) {
                                    if (shortcut.isEmoji) {
                                        Text(shortcut.iconValue, fontSize = 16.sp)
                                    } else {
                                        AsyncImage(
                                            model = shortcut.iconValue,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = shortcut.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("practice_title")
                                )
                            }
                        },
                        navigationIcon = {
                            // Immersive session EXIT trigger
                            IconButton(
                                onClick = { showExitConfirmDialog = true },
                                modifier = Modifier.testTag("close_session_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "End Practice",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                },
                containerColor = Color.Transparent,
                modifier = Modifier.fillMaxSize()
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 24.dp)
                ) {
                    val totalExercises = flattenedSteps.size

                    // --- SEGMENTED INTEGRATED STEP INDICATOR BAR ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (i in 0 until totalExercises) {
                            val activeStateColor = when {
                                i == currentIndex -> MaterialTheme.colorScheme.primary
                                i < currentIndex -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(activeStateColor)
                            )
                        }
                    }

                    // Progress Numeric indicator text label
                    val currentStepRepeatCount = currentStep?.repeatCount ?: 1
                    Text(
                        text = "Practice ${currentIndex + 1} of $totalExercises",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        ),
                        modifier = Modifier.fillMaxWidth().padding(bottom = if (currentStepRepeatCount > 1) 4.dp else 12.dp).testTag("practice_progress_indicator"),
                        textAlign = TextAlign.Center
                    )

                    if (currentStepRepeatCount > 1) {
                        Text(
                            text = "Rep $currentRepeat of $currentStepRepeatCount",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                letterSpacing = 1.sp
                            ),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("practice_rep_indicator"),
                            textAlign = TextAlign.Center
                        )
                    }

                    // --- LARGE GEOMETRIC PULSATION ORBIT SPACE (PHOTO/MEDIA BANNER) ---
                    if (currentExercise != null) {
                        val matchedCategory = categories.find { it.id == currentExercise.categoryId }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(230.dp)
                                .clip(RoundedCornerShape(32.dp))
                                .drawBehind {
                                    if (currentExercise.media.mediaUri.isBlank()) {
                                        // Custom visual energy mapping matching category background
                                        val drawBackground = Brush.verticalGradient(
                                            colors = when (currentExercise.categoryId) {
                                                "neck", "head" -> listOf(Color(0xFFE2ECE2), Color(0xFFD3E7D3))
                                                "eyes" -> listOf(Color(0xFFE1EBF2), Color(0xFFCFDDE7))
                                                "hands", "arms" -> listOf(Color(0xFFF7ECE6), Color(0xFFECD7CC))
                                                "abs", "hips" -> listOf(Color(0xFFEDE7F2), Color(0xFFDDD2E8))
                                                "legs" -> listOf(Color(0xFFF6F7E6), Color(0xFFEBECC8))
                                                "full_body" -> listOf(Color(0xFFEDF2F0), Color(0xFFDBE7E4))
                                                else -> listOf(Color(0xFFEDE9E5), Color(0xFFE5DCD4))
                                            }
                                        )
                                        drawRect(brush = drawBackground)

                                        val cx = size.width / 2f
                                        val cy = size.height / 2f
                                        val rBase = size.minDimension * 0.38f

                                        // Dynamic pulsing breath aura representation
                                        drawCircle(
                                            color = Color(0xFF5A725A).copy(alpha = 0.1f),
                                            radius = rBase * breathingScale,
                                            center = Offset(cx, cy)
                                        )

                                        // Calm orbits representation
                                        for (indexO in 1..4) {
                                            val rad = rBase * (0.23f * indexO + 0.12f)
                                            drawCircle(
                                                color = Color(0xFFB08C75).copy(alpha = 0.04f * (5 - indexO)),
                                                radius = rad,
                                                center = Offset(cx, cy),
                                                style = Stroke(width = 1.5.dp.toPx())
                                            )
                                        }

                                        // Symmetrical outer guide points
                                        val numGPoints = 12
                                        for (p in 0 until numGPoints) {
                                            val angle = (p * (360f / numGPoints)) * PI.toFloat() / 180f
                                            val pDist = rBase * 0.82f
                                            drawCircle(
                                                color = Color(0xFF5A725A).copy(alpha = 0.18f),
                                                radius = 3f,
                                                center = Offset(
                                                    cx + pDist * cos(angle),
                                                    cy + pDist * sin(angle)
                                                )
                                            )
                                        }
                                    }
                                }
                        ) {
                            if (currentExercise.media.mediaUri.isNotBlank()) {
                                // Renders visual demonstration using LoopingMediaView with continuous autoplay/loops!
                                LoopingMediaView(
                                    media = currentExercise.media,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                // Center high-contrast icon overlay fallback when URL is blank
                                if (matchedCategory != null) {
                                    if (matchedCategory.isEmoji) {
                                        Text(
                                            text = matchedCategory.iconValue,
                                            style = MaterialTheme.typography.displayLarge.copy(
                                                fontSize = 54.sp
                                            ),
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(80.dp)
                                                .clip(RoundedCornerShape(20.dp))
                                                .align(Alignment.Center)
                                        ) {
                                            AsyncImage(
                                                model = matchedCategory.iconValue,
                                                contentDescription = "Category Icon",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            }

                            // Dynamic category text banner overlay
                            Box(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .align(Alignment.BottomStart)
                                    .background(
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = (matchedCategory?.name ?: "PRACTICE").uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 1.sp
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // --- CORE TEXT & INSTRUCTIONS PANEL (SCROLLABLE WELLCASE) ---
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            Text(
                                text = currentExercise.getLocalizedName(languageCode),
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                                    .padding(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = currentExercise.getLocalizedInstructions(languageCode),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 22.sp
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // --- LARGE VISUAL COUNTDOWN CENTRAL RING ---
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(100.dp)
                            ) {
                                val originalDuration = (currentStep?.durationSeconds ?: 0).toFloat()
                                val isOvertime = secondsRemaining < 0
                                val absSeconds = if (secondsRemaining < 0) -secondsRemaining else secondsRemaining
                                val fractionRemaining = if (isOvertime) 1f else if (originalDuration > 0) secondsRemaining.toFloat() / originalDuration else 1f

                                // Circular countdown energy circle outline representation
                                CircularProgressIndicator(
                                    progress = { fractionRemaining },
                                    color = if (isOvertime) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                    strokeWidth = 6.dp,
                                    trackColor = if (isOvertime) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Huge numeric timer label
                                val viewMin = absSeconds / 60
                                val viewSec = absSeconds % 60
                                val timerString = if (isOvertime) String.format("+%02d:%02d", viewMin, viewSec) else String.format("%02d:%02d", viewMin, viewSec)

                                Text(
                                    text = timerString,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isOvertime) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.testTag("practice_timer")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (currentStep?.showDoneButton == true) {
                            Button(
                                onClick = {
                                    playTransitionAlert()
                                    showFlashEffect = true

                                    if (currentRepeat < currentStep.repeatCount) {
                                        // Next repeat/rep of the active exercise
                                        currentRepeat += 1
                                        secondsRemaining = currentStep.durationSeconds
                                    } else {
                                        // Done with all repeats of this exercise
                                        exercisesCompletedCount += 1
                                        if (currentIndex < flattenedSteps.size - 1) {
                                            currentIndex += 1
                                        } else {
                                            isSessionComplete = true
                                        }
                                    }

                                    scope.launch {
                                        delay(300)
                                        showFlashEffect = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .padding(horizontal = 32.dp)
                                    .testTag("practice_done_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Done", fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // --- IMMERSIVE PLAYBACK CONTROL GRID BAR ---
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Back button (Previous exercise in stack flow)
                            IconButton(
                                onClick = {
                                    if (currentIndex > 0) {
                                        currentIndex -= 1
                                    }
                                },
                                enabled = currentIndex > 0,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (currentIndex > 0) MaterialTheme.colorScheme.surface
                                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
                                    )
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                        CircleShape
                                    )
                                    .testTag("skip_prev_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Previous Exercise",
                                    tint = if (currentIndex > 0) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                            }

                            // Large Primary play/pause bubble controller
                            IconButton(
                                onClick = { isPlaying = !isPlaying },
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .testTag("play_pause_button")
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause timer" else "Resume timer",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            // Forward button (Next / Skip exercise in stack flow)
                            IconButton(
                                onClick = {
                                    if (currentIndex < flattenedSteps.size - 1) {
                                        currentIndex += 1
                                    } else {
                                        isSessionComplete = true
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                        CircleShape
                                    )
                                    .testTag("skip_next_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Skip Exercise",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- SUBTLE PULSE FLASH CUE LAYOUT OVERLAY ON TRANSTION ADVANCES ---
        AnimatedVisibility(
            visible = showFlashEffect,
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(400))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                     shape = RoundedCornerShape(24.dp),
                     colors = CardDefaults.cardColors(
                         containerColor = MaterialTheme.colorScheme.surface
                     ),
                     modifier = Modifier.padding(32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Complete Indicator",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Next Practice!",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }

        // --- EXIT CONFIRMATION POPUP SYSTEM ---
        if (showExitConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showExitConfirmDialog = false },
                title = { Text("End Session Early?", fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to end this session early?") },
                confirmButton = {
                    Button(
                        onClick = {
                            if (totalTimeSpentSeconds > 0 && !hasLoggedSession) {
                                viewModel.logPracticeSession(
                                    shortcutId = shortcutId,
                                    shortcutNameSnapshot = shortcut?.name ?: "Unknown Routine",
                                    actualDurationSeconds = totalTimeSpentSeconds,
                                    categoriesSnapshot = categoriesSnapshotValue
                                )
                                hasLoggedSession = true
                            }
                            showExitConfirmDialog = false
                            onCloseClick()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("exit_confirm_dialog_confirm")
                    ) {
                        Text("Exit Session")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showExitConfirmDialog = false },
                        modifier = Modifier.testTag("exit_confirm_dialog_cancel")
                    ) {
                        Text("Continue")
                    }
                },
                shape = RoundedCornerShape(24.dp)
            )
        }
    }
}
