package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.model.UserProfile
import com.example.ui.components.IconPicker
import com.example.viewmodel.ExerciseViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.ApiException
import com.example.service.BackupService
import com.example.database.AppDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ExerciseViewModel,
    onBackClick: () -> Unit,
    onStatsAndHistoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    // Google Sign In Setup
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/drive.appdata"))
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }
    var googleAccount by remember { mutableStateOf(GoogleSignIn.getLastSignedInAccount(context)) }

    // State bindings
    val lastBackupTimeVal by viewModel.driveLastBackupTime.collectAsState()
    val autoBackupEnabledVal by viewModel.driveAutoBackupEnabled.collectAsState()
    val isReminderEnabled by viewModel.isReminderEnabled.collectAsState()
    val reminderHour by viewModel.reminderHour.collectAsState()
    val reminderMinute by viewModel.reminderMinute.collectAsState()
    val bgSoundEnabledVal by viewModel.bgSoundEnabled.collectAsState()
    val bgSoundTypeVal by viewModel.bgSoundType.collectAsState()
    val keepScreenAwakeVal by viewModel.keepScreenAwake.collectAsState()
    val countdownLengthVal by viewModel.countdownLength.collectAsState()

    var isBackupInProgress by remember { mutableStateOf(false) }
    var isRestoreInProgress by remember { mutableStateOf(false) }
    var backupStatusMessage by remember { mutableStateOf<String?>(null) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var showPermissionDeniedWarning by remember { mutableStateOf(false) }

    // Google SignIn Launcher
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            googleAccount = account
            backupStatusMessage = "Successfully connected: ${account?.email}"
        } catch (e: Exception) {
            e.printStackTrace()
            backupStatusMessage = "Sign-in failed: ${e.localizedMessage ?: "Unknown Error"}"
        }
    }

    // Notification Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showPermissionDeniedWarning = false
            viewModel.updateReminderSettings(true, reminderHour, reminderMinute)
        } else {
            showPermissionDeniedWarning = true
            viewModel.updateReminderSettings(false, reminderHour, reminderMinute)
        }
    }

    val profile by viewModel.userProfile.collectAsState()

    // Form inputs initialized from profile
    var nameInput by remember { mutableStateOf("") }
    var isEmojiSelection by remember { mutableStateOf(true) }
    var avatarInputVal by remember { mutableStateOf("🧘") }

    var waterTrackerEnabled by remember { mutableStateOf(false) }
    var waterGoalMl by remember { mutableStateOf(2000) }
    var waterReminderEnabled by remember { mutableStateOf(false) }
    var waterReminderType by remember { mutableStateOf("INTERVAL") }
    var waterReminderIntervalHours by remember { mutableStateOf(2) }
    var waterReminderCustomTimes by remember { mutableStateOf("08:00,12:00,16:00,20:00") }
    var languageCode by remember { mutableStateOf("en") }

    var isEditingProfile by remember { mutableStateOf(false) }
    var showAddTimeDialog by remember { mutableStateOf(false) }
    var hasInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(profile) {
        if (!hasInitialized) {
            nameInput = profile.name
            isEmojiSelection = profile.isEmoji
            avatarInputVal = profile.avatarValue
            waterTrackerEnabled = profile.waterTrackerEnabled
            waterGoalMl = profile.waterGoalMl
            waterReminderEnabled = profile.waterReminderEnabled
            waterReminderType = profile.waterReminderType
            waterReminderIntervalHours = profile.waterReminderIntervalHours
            waterReminderCustomTimes = profile.waterReminderCustomTimes
            languageCode = profile.languageCode
            hasInitialized = true
        }
    }

    // Helper to immediately save Room user profile changes
    val saveProfileChanges = {
        val updatedProfile = UserProfile(
            id = 1,
            name = nameInput.trim().ifEmpty { "Self Practitioner" },
            isEmoji = isEmojiSelection,
            avatarValue = avatarInputVal,
            waterTrackerEnabled = waterTrackerEnabled,
            waterGoalMl = waterGoalMl,
            waterReminderEnabled = waterReminderEnabled,
            waterReminderType = waterReminderType,
            waterReminderIntervalHours = waterReminderIntervalHours,
            waterReminderCustomTimes = waterReminderCustomTimes,
            languageCode = languageCode
        )
        viewModel.updateUserProfile(updatedProfile)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Wellness Hub & Settings",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("profile_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            focusManager.clearFocus()
                            saveProfileChanges()
                            isEditingProfile = false
                            onBackClick()
                        },
                        modifier = Modifier.testTag("profile_save_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Save and Close",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
             // SECTION 1: ACCOUNT
            SettingsSectionHeader(title = "ACCOUNT IDENTITY", icon = Icons.Default.Person)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().testTag("account_section_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (!isEditingProfile) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                ) {
                                    if (isEmojiSelection && avatarInputVal.isNotEmpty()) {
                                        Text(text = avatarInputVal, fontSize = 32.sp)
                                    } else if (!isEmojiSelection && avatarInputVal.isNotEmpty()) {
                                        AsyncImage(
                                            model = avatarInputVal,
                                            contentDescription = "Avatar Preview",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Text(
                                            text = nameInput.take(2).uppercase().ifEmpty { "SP" },
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = nameInput.ifEmpty { "Self Practitioner" },
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "Active Wellness Practitioner",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = { isEditingProfile = true },
                                modifier = Modifier.testTag("edit_profile_badge_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Profile Identity",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                if (isEmojiSelection && avatarInputVal.isNotEmpty()) {
                                    Text(text = avatarInputVal, fontSize = 40.sp)
                                } else if (!isEmojiSelection && avatarInputVal.isNotEmpty()) {
                                    AsyncImage(
                                        model = avatarInputVal,
                                        contentDescription = "Avatar Preview",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = nameInput.take(2).uppercase().ifEmpty { "SP" },
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                label = { Text("Practitioner Name") },
                                placeholder = { Text("Enter your name") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("profile_name_input"),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                            )

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Choose Profile Avatar",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.align(Alignment.Start)
                            )

                            IconPicker(
                                isEmoji = isEmojiSelection,
                                onModeChange = { isEmojiSelection = it },
                                selectedValue = avatarInputVal,
                                onValueSelected = { avatarInputVal = it }
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        nameInput = profile.name
                                        isEmojiSelection = profile.isEmoji
                                        avatarInputVal = profile.avatarValue
                                        isEditingProfile = false
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).testTag("cancel_profile_edit_btn")
                                ) {
                                    Text("Cancel")
                                }
                                Button(
                                    onClick = {
                                        focusManager.clearFocus()
                                        saveProfileChanges()
                                        isEditingProfile = false
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).testTag("save_profile_edit_btn")
                                ) {
                                    Text("Save ID")
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Google Sync Account Connectivity Status inside Account section
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "Google Sync Account Backup Status",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        if (googleAccount == null) {
                            Text(
                                text = "Connect your Google Account to back up configurations safely.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    signInLauncher.launch(googleSignInClient.signInIntent)
                                },
                                modifier = Modifier.fillMaxWidth().testTag("google_signin_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Text("Connect Google Account")
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Google Account Connected",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = googleAccount?.email ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        googleSignInClient.signOut().addOnCompleteListener {
                                            googleAccount = null
                                            backupStatusMessage = "Account disconnected successfully."
                                        }
                                    },
                                    modifier = Modifier.testTag("google_signout_button")
                                ) {
                                    Text("Disconnect", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 2: PRACTICE PREFERENCES
            SettingsSectionHeader(title = "PRACTICE PREFERENCES", icon = Icons.Default.PlayCircleOutline)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().testTag("practice_prefs_section_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Countdown Length picker
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    "Step Countdown Length",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    "Remaining seconds are spoken aloud near step completion",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(3, 5, 10).forEach { seconds ->
                                val isSelected = countdownLengthVal == seconds
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.updateCountdownLength(seconds) },
                                    label = {
                                        Text(
                                            text = "$seconds Seconds",
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("countdown_${seconds}s"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // 2. Keep screen awake toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Vibration, // using as light standby/stand icon
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Keep Screen Awake",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Keeps device screen on when active in practicing",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = keepScreenAwakeVal,
                            onCheckedChange = { viewModel.updateKeepScreenAwake(it) },
                            modifier = Modifier.testTag("keep_awake_switch")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // 3. Background sound/ambient music choice
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "Ambient Soundscapes",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "Play gentle healing background sound on loop",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = bgSoundEnabledVal,
                                onCheckedChange = { viewModel.updateBgSoundEnabled(it) },
                                modifier = Modifier.testTag("bg_sound_switch")
                            )
                        }

                        AnimatedVisibility(
                            visible = bgSoundEnabledVal,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Select Audio Ambience Track",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("Calm Meditation", "Gentle Waves", "Zen Bells").forEach { track ->
                                        val isSelected = bgSoundTypeVal == track
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { viewModel.updateBgSoundType(track) },
                                            label = {
                                                Text(
                                                    text = when (track) {
                                                        "Calm Meditation" -> "🧘 Calm"
                                                        "Gentle Waves" -> "🌊 Ocean"
                                                        "Zen Bells" -> "🔔 Bells"
                                                        else -> track
                                                    },
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                                                )
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("bg_sound_chip_$track"),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 3: NOTIFICATIONS
            SettingsSectionHeader(title = "NOTIFICATION ALERTS", icon = Icons.Default.Notifications)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().testTag("notifications_section_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // A. Daily Practice Reminder
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "Daily Practice Reminder",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = if (isReminderEnabled) "Reminder active" else "Reminder disabled",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = isReminderEnabled,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            val checkPermission = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.POST_NOTIFICATIONS
                                            )
                                            if (checkPermission == PackageManager.PERMISSION_GRANTED) {
                                                showPermissionDeniedWarning = false
                                                viewModel.updateReminderSettings(true, reminderHour, reminderMinute)
                                            } else {
                                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        } else {
                                            showPermissionDeniedWarning = false
                                            viewModel.updateReminderSettings(true, reminderHour, reminderMinute)
                                        }
                                    } else {
                                        viewModel.updateReminderSettings(false, reminderHour, reminderMinute)
                                    }
                                },
                                modifier = Modifier.testTag("reminder_active_switch")
                            )
                        }

                        AnimatedVisibility(
                            visible = showPermissionDeniedWarning,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Permission Action Required",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = "Please enable Notifications in App Settings to allow practice reminder pings.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = isReminderEnabled,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(12.dp)
                                    .testTag("time_selector_panel"),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Choose Alert Time",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Hour Adjuster
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        IconButton(
                                            onClick = {
                                                val newHour = (reminderHour + 1) % 24
                                                viewModel.updateReminderSettings(true, newHour, reminderMinute)
                                            },
                                            modifier = Modifier.size(36.dp).testTag("hour_up_button")
                                        ) {
                                            Icon(Icons.Default.Add, "Hour Up")
                                        }

                                        val displayedHour = if (reminderHour == 0 || reminderHour == 12) 12 else reminderHour % 12
                                        Text(
                                            text = String.format("%02d", displayedHour),
                                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        IconButton(
                                            onClick = {
                                                val newHour = if (reminderHour - 1 < 0) 23 else reminderHour - 1
                                                viewModel.updateReminderSettings(true, newHour, reminderMinute)
                                            },
                                            modifier = Modifier.size(36.dp).testTag("hour_down_button")
                                        ) {
                                            Icon(Icons.Default.Remove, "Hour Down")
                                        }
                                    }

                                    Text(
                                        text = ":",
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )

                                    // Minute Adjuster
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        IconButton(
                                            onClick = {
                                                val newMinute = (reminderMinute + 5) % 60
                                                viewModel.updateReminderSettings(true, reminderHour, newMinute)
                                            },
                                            modifier = Modifier.size(36.dp).testTag("minute_up_button")
                                        ) {
                                            Icon(Icons.Default.Add, "Minute Up")
                                        }

                                        Text(
                                            text = String.format("%02d", reminderMinute),
                                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        IconButton(
                                            onClick = {
                                                val newMinute = if (reminderMinute - 5 < 0) 55 else reminderMinute - 5
                                                viewModel.updateReminderSettings(true, reminderHour, newMinute)
                                            },
                                            modifier = Modifier.size(36.dp).testTag("minute_down_button")
                                        ) {
                                            Icon(Icons.Default.Remove, "Minute Down")
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    // AM/PM Block
                                    val isAm = reminderHour < 12
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .clickable {
                                                val newHour = (reminderHour + 12) % 24
                                                viewModel.updateReminderSettings(true, newHour, reminderMinute)
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                            .testTag("ampm_toggle_button")
                                    ) {
                                        Text(
                                            text = if (isAm) "AM" else "PM",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // B. Hydration Alerts
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        "Hydration Notification Pings",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        "Get reminded to log water throughout the day",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = waterReminderEnabled,
                                onCheckedChange = {
                                    waterReminderEnabled = it
                                    saveProfileChanges()
                                },
                                modifier = Modifier.testTag("water_reminders_toggle")
                            )
                        }

                        AnimatedVisibility(
                            visible = waterReminderEnabled,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = waterReminderType == "INTERVAL",
                                        onClick = {
                                            waterReminderType = "INTERVAL"
                                            saveProfileChanges()
                                        },
                                        label = { Text("Periodic Interval") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = waterReminderType == "CUSTOM",
                                        onClick = {
                                            waterReminderType = "CUSTOM"
                                            saveProfileChanges()
                                        },
                                        label = { Text("Specific Times") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                if (waterReminderType == "INTERVAL") {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "Set Frequency Preset",
                                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            listOf(1, 2, 3).forEach { hours ->
                                                FilterChip(
                                                    selected = waterReminderIntervalHours == hours,
                                                    onClick = {
                                                        waterReminderIntervalHours = hours
                                                        saveProfileChanges()
                                                    },
                                                    label = { Text("Every $hours hr") },
                                                    modifier = Modifier.weight(1f).testTag("preset_interval_$hours")
                                                )
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = "Or Adjust Hour Gap",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                FilledIconButton(
                                                    onClick = {
                                                        if (waterReminderIntervalHours > 1) {
                                                            waterReminderIntervalHours--
                                                            saveProfileChanges()
                                                        }
                                                    },
                                                    colors = IconButtonDefaults.filledIconButtonColors(
                                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                                    ),
                                                    modifier = Modifier.size(32.dp).testTag("decrease_interval_btn")
                                                ) {
                                                    Icon(Icons.Default.Remove, contentDescription = "Decrease Interval", modifier = Modifier.size(16.dp))
                                                }
                                                Text(
                                                    text = "$waterReminderIntervalHours ${if (waterReminderIntervalHours == 1) "hr" else "hrs"}",
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary),
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.widthIn(min = 36.dp)
                                                )
                                                FilledIconButton(
                                                    onClick = {
                                                        if (waterReminderIntervalHours < 12) {
                                                            waterReminderIntervalHours++
                                                            saveProfileChanges()
                                                        }
                                                    },
                                                    colors = IconButtonDefaults.filledIconButtonColors(
                                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                                    ),
                                                    modifier = Modifier.size(32.dp).testTag("increase_interval_btn")
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = "Increase Interval", modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "Specific Alert Times",
                                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        )

                                        val customTimesList = remember(waterReminderCustomTimes) {
                                            waterReminderCustomTimes.split(",")
                                                .map { it.trim() }
                                                .filter { it.isNotEmpty() }
                                                .sorted()
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            InputChip(
                                                selected = false,
                                                onClick = { showAddTimeDialog = true },
                                                label = {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Add Time")
                                                    }
                                                },
                                                modifier = Modifier.testTag("add_custom_time_chip")
                                            )

                                            customTimesList.forEach { time ->
                                                InputChip(
                                                    selected = true,
                                                    onClick = { },
                                                    label = { Text(time, fontWeight = FontWeight.SemiBold) },
                                                    trailingIcon = {
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = "Remove $time",
                                                            modifier = Modifier
                                                                .size(14.dp)
                                                                .clickable {
                                                                    val updatedList = customTimesList.toMutableList().apply { remove(time) }
                                                                    waterReminderCustomTimes = updatedList.joinToString(",")
                                                                    saveProfileChanges()
                                                                }
                                                        )
                                                    },
                                                    modifier = Modifier.testTag("custom_time_chip_$time")
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 4: HYDRATION
            SettingsSectionHeader(title = "HYDRATION INTELLIGENCE", icon = Icons.Default.WaterDrop)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().testTag("hydration_section_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Activate water tracking card
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Log Water Consumption",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Turn on logging analytics card on main screen",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = waterTrackerEnabled,
                            onCheckedChange = {
                                waterTrackerEnabled = it
                                saveProfileChanges()
                            },
                            modifier = Modifier.testTag("water_tracker_toggle")
                        )
                    }

                    if (waterTrackerEnabled) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        // Daily Goal
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Daily Goal Limit: ${waterGoalMl}ml",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        waterGoalMl = (waterGoalMl - 250).coerceAtLeast(500)
                                        saveProfileChanges()
                                    },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrease Goal")
                                }

                                Slider(
                                    value = waterGoalMl.toFloat(),
                                    onValueChange = {
                                        waterGoalMl = (it / 250).toInt() * 250
                                        saveProfileChanges()
                                    },
                                    valueRange = 500f..5000f,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        thumbColor = MaterialTheme.colorScheme.primary
                                    )
                                )

                                IconButton(
                                    onClick = {
                                        waterGoalMl = (waterGoalMl + 250).coerceAtMost(5000)
                                        saveProfileChanges()
                                    },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Increase Goal")
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 5: LANGUAGE
            SettingsSectionHeader(title = "LANGUAGE SETTINGS", icon = Icons.Default.Translate)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().testTag("language_section_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                "App Instruction Translation Language",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                "Choose preferred instructions translation & narration",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("en" to "English 🇺🇸", "hi" to "हिन्दी 🇮🇳").forEach { (code, name) ->
                            val isSelected = languageCode == code
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    languageCode = code
                                    saveProfileChanges()
                                },
                                label = { Text(name, modifier = Modifier.padding(vertical = 4.dp)) },
                                modifier = Modifier.weight(1f).testTag("lang_chip_$code"),
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null
                            )
                        }
                    }
                }
            }

            // SECTION 6: DATA & BACKUP
            SettingsSectionHeader(title = "DATA BACKUP & SYNC ENGINE", icon = Icons.Default.CloudSync)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().testTag("backup_restore_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val formatter = remember { SimpleDateFormat("MMM d, yyyy - h:mm a", Locale.getDefault()) }
                    val backupTextStr = if (lastBackupTimeVal == 0L) "Never Backed Up" else formatter.format(Date(lastBackupTimeVal))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.RotateRight, // rotate status represent sync timer
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Last Backed Up Syncing Time:",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Timestamp of last secure storage backup",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = backupTextStr,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("last_backup_time_text")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (googleAccount == null) {
                                    backupStatusMessage = "Please connect your Google Account in the Account Identity section first."
                                    return@Button
                                }
                                coroutineScope.launch {
                                    isBackupInProgress = true
                                    backupStatusMessage = null
                                    try {
                                        val bService = BackupService(context)
                                        val dbInstance = AppDatabase.getDatabase(context)
                                        val done = bService.executeBackup(dbInstance.appDao, googleAccount!!)
                                        if (done) {
                                            viewModel.updateLastBackupTime(System.currentTimeMillis())
                                            backupStatusMessage = "Backup completed successfully!"
                                        } else {
                                            backupStatusMessage = "Backup failed. Please try again."
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        backupStatusMessage = e.localizedMessage ?: "Unknown Error occurred during backup."
                                    } finally {
                                        isBackupInProgress = false
                                    }
                                }
                            },
                            enabled = !isBackupInProgress && !isRestoreInProgress,
                            modifier = Modifier.weight(1f).testTag("backup_button")
                        ) {
                            if (isBackupInProgress) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Back Up Now")
                            }
                        }

                        Button(
                            onClick = {
                                if (googleAccount == null) {
                                    backupStatusMessage = "Please connect your Google Account in the Account Identity section first to restore data."
                                    return@Button
                                }
                                showRestoreConfirmDialog = true
                            },
                            enabled = !isBackupInProgress && !isRestoreInProgress,
                            modifier = Modifier.weight(1f).testTag("restore_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            if (isRestoreInProgress) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onSecondary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Restore")
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Auto Daily Backup Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto Daily Backup",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Keep a secure copy saved daily automatically in background",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoBackupEnabledVal,
                            onCheckedChange = { viewModel.updateAutoBackupEnabled(it) },
                            modifier = Modifier.testTag("auto_backup_switch")
                        )
                    }
                }
            }
        }
    }

    // Modal dialogs
    if (showAddTimeDialog) {
        var selectedHour by remember { mutableStateOf(8) }
        var selectedMinute by remember { mutableStateOf(0) }

        AlertDialog(
            onDismissRequest = { showAddTimeDialog = false },
            title = { Text("Add Hydration Reminder", fontWeight = FontWeight.Bold) },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("HOUR (24h)", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { selectedHour = (selectedHour + 23) % 24 }) {
                                Icon(Icons.Default.Remove, contentDescription = "Prev Hour")
                            }
                            Text(
                                text = String.format("%02d", selectedHour),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            IconButton(onClick = { selectedHour = (selectedHour + 1) % 24 }) {
                                Icon(Icons.Default.Add, contentDescription = "Next Hour")
                            }
                        }
                    }

                    Text(":", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 8.dp))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("MINUTE", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { selectedMinute = (selectedMinute + 45) % 60 }) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease Minute")
                            }
                            Text(
                                text = String.format("%02d", selectedMinute),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            IconButton(onClick = { selectedMinute = (selectedMinute + 15) % 60 }) {
                                Icon(Icons.Default.Add, contentDescription = "Increase Minute")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val formatted = String.format("%02d:%02d", selectedHour, selectedMinute)
                        val currentList = waterReminderCustomTimes.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                        if (!currentList.contains(formatted)) {
                            currentList.add(formatted)
                            currentList.sort()
                            waterReminderCustomTimes = currentList.joinToString(",")
                            saveProfileChanges()
                        }
                        showAddTimeDialog = false
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTimeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRestoreConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmDialog = false },
            title = { Text("Overwrite Device Data?") },
            text = { Text("Are you absolutely sure you want to download and restore? This will fully overwrite all current practices, shortcuts, log history, water stats, and reminder settings on this device. This process cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreConfirmDialog = false
                        coroutineScope.launch {
                            isRestoreInProgress = true
                            backupStatusMessage = null
                            try {
                                val bService = BackupService(context)
                                val dbInstance = AppDatabase.getDatabase(context)
                                val done = bService.executeRestore(dbInstance.appDao, googleAccount!!)
                                if (done) {
                                    backupStatusMessage = "Restore completed successfully! Device updated."
                                } else {
                                    backupStatusMessage = "Restore ended incomplete."
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                backupStatusMessage = e.localizedMessage ?: "Unknown Error occurred during restore."
                            } finally {
                                isRestoreInProgress = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_restore_button")
                ) {
                    Text("Overwrite & Restore")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRestoreConfirmDialog = false },
                    modifier = Modifier.testTag("cancel_restore_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (backupStatusMessage != null) {
        AlertDialog(
            onDismissRequest = { backupStatusMessage = null },
            title = { Text("Sync Notification") },
            text = { Text(backupStatusMessage ?: "") },
            confirmButton = {
                Button(
                    onClick = { backupStatusMessage = null },
                    modifier = Modifier.testTag("status_dialog_ok_button")
                ) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun SettingsSectionHeader(
    title: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.3.sp,
                color = MaterialTheme.colorScheme.primary
            )
        )
    }
}
