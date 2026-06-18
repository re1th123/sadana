package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.Exercise
import com.example.model.Shortcut
import com.example.model.ShortcutExercise
import com.example.model.Category
import com.example.model.ExerciseMode
import com.example.model.getLocalizedName
import com.example.model.getLocalizedInstructions
import com.example.ui.components.IconPicker
import com.example.ui.components.LoopingMediaView
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import com.example.viewmodel.ExerciseViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyShortcutsScreen(
    viewModel: ExerciseViewModel,
    onBackClick: () -> Unit,
    onStartShortcutClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val shortcuts by viewModel.shortcuts.collectAsState()
    val allExercises by viewModel.exercises.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val languageCode = remember(userProfile) { userProfile.languageCode }
    val activeMode by viewModel.activeMode.collectAsState()
    var composingShortcutMode by remember { mutableStateOf(ExerciseMode.YOGA) }

    // Screen State for Active Edit or Creation (We use a clean dedicated state sub-screen for composing a shortcut!)
    var isComposingShortcut by remember { mutableStateOf(false) }
    var composingShortcutId by remember { mutableStateOf<String?>(null) } // null means new shortcut
    var nameInput by remember { mutableStateOf("") }
    var isEmojiSelection by remember { mutableStateOf(true) }
    var iconInputVal by remember { mutableStateOf("✨") }
    var completionModeInput by remember { mutableStateOf("AUTO_PROCEED") }
    
    // Ordered exercises in the shortcut being composed
    var composedExercisesState = remember { mutableStateListOf<ShortcutExercise>() }

    // Dialog state for adding exercises from general library to shortcut
    var showBrowseExercisesDialog by remember { mutableStateOf(false) }
    var exerciseSearchQuery by remember { mutableStateOf("") }
    var filteredExerciseCategory by remember { mutableStateOf<Category?>(null) }

    var showBrowseShortcutsDialog by remember { mutableStateOf(false) }
    var shortcutSearchQuery by remember { mutableStateOf("") }

    // Delete confirmation state
    var deletingShortcut by remember { mutableStateOf<Shortcut?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    if (isComposingShortcut) {
        // --- SECONDARY VIEW: COMPOSE / EDIT SHORTCUT DETAILS ---
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (composingShortcutId == null) "Create Shortcut" else "Edit Shortcut",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { isComposingShortcut = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // Name Input
                item {
                    Text(
                        text = "Shortcut Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Routine Name") },
                        placeholder = { Text("e.g. Afternoon Focus Loop") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("shortcut_name_input")
                    )
                }

                // Unified Icon Picker with Emoji and Image presets
                item {
                    IconPicker(
                        isEmoji = isEmojiSelection,
                        onModeChange = { isEmojiSelection = it },
                        selectedValue = iconInputVal,
                        onValueSelected = { iconInputVal = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Completion Mode Selection Toggle
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Completion Mode",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Choose whether exercises in this routine automatically advance or require a manual done action when the timer reaches zero.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (completionModeInput == "AUTO_PROCEED") MaterialTheme.colorScheme.primary
                                        else Color.Transparent
                                    )
                                    .clickable { completionModeInput = "AUTO_PROCEED" }
                                    .testTag("completion_mode_auto_proceed"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Auto Proceed",
                                    color = if (completionModeInput == "AUTO_PROCEED") MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (completionModeInput == "DONE") MaterialTheme.colorScheme.primary
                                        else Color.Transparent
                                    )
                                    .clickable { completionModeInput = "DONE" }
                                    .testTag("completion_mode_done"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Done",
                                    color = if (completionModeInput == "DONE") MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // Selected Exercises Section Header
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column {
                            Text(
                                text = "Ordered Routine Exercises",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${composedExercisesState.size} items in flow",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { showBrowseExercisesDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("add_exercise_to_shortcut_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Exercise", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = { showBrowseShortcutsDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("add_shortcut_to_shortcut_button")
                            ) {
                                Icon(Icons.Default.LibraryAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Shortcut", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // List of ordered exercises currently included
                if (composedExercisesState.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.LibraryBooks,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Your routine flow is empty.",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Tap 'Browse Exercises' above to fill it.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(composedExercisesState) { index, shortcutExercise ->
                        if (shortcutExercise.nestedShortcutId != null) {
                            val matchedShortcut = shortcuts.find { it.id == shortcutExercise.nestedShortcutId }
                            if (matchedShortcut != null) {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                                    ),
                                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth().testTag("shortcut_composer_nested_$index")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        // Step order index circle
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
                                        ) {
                                            Text(
                                                text = "${index + 1}",
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.secondary,
                                                fontSize = 12.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = matchedShortcut.iconValue,
                                                    fontSize = 18.sp,
                                                    modifier = Modifier.padding(end = 6.dp)
                                                )
                                                Text(
                                                    text = matchedShortcut.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                text = "Nested Sub-Routine",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                            Text(
                                                text = "Contains ${matchedShortcut.exercises.size} exercises",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }

                                        // Move Up / Move Down Reordering Action Triggers
                                        Column(
                                            verticalArrangement = Arrangement.Center,
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    if (index > 0) {
                                                        val prev = composedExercisesState[index - 1]
                                                        composedExercisesState[index - 1] = shortcutExercise
                                                        composedExercisesState[index] = prev
                                                    }
                                                },
                                                enabled = index > 0,
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(
                                                onClick = {
                                                    if (index < composedExercisesState.size - 1) {
                                                        val next = composedExercisesState[index + 1]
                                                        composedExercisesState[index + 1] = shortcutExercise
                                                        composedExercisesState[index] = next
                                                    }
                                                },
                                                enabled = index < composedExercisesState.size - 1,
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(16.dp))
                                            }
                                        }

                                        // Trash item out of the list
                                        IconButton(
                                            onClick = { composedExercisesState.removeAt(index) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remove routine",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            val matchedExe = allExercises.find { it.id == shortcutExercise.exerciseId }
                            if (matchedExe != null) {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                    modifier = Modifier.fillMaxWidth().testTag("shortcut_composer_exercise_$index")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        // Step order index circle
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                        ) {
                                            Text(
                                                text = "${index + 1}",
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 12.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = matchedExe.getLocalizedName(languageCode),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            
                                            // Interactive Custom Duration Field inside each routine row
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(top = 2.dp)
                                            ) {
                                                Text(
                                                    text = "Duration: ",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                // Quick increase/decrease counters
                                                IconButton(
                                                    onClick = {
                                                        val curr = shortcutExercise.customDurationSeconds
                                                        if (curr > 5) {
                                                            composedExercisesState[index] = shortcutExercise.copy(
                                                                customDurationSeconds = curr - 5
                                                            )
                                                        }
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.Remove, contentDescription = "Decrease Duration", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                                }
                                                Text(
                                                    text = "${shortcutExercise.customDurationSeconds}s",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.padding(horizontal = 4.dp)
                                                )
                                                IconButton(
                                                    onClick = {
                                                        val curr = shortcutExercise.customDurationSeconds
                                                        composedExercisesState[index] = shortcutExercise.copy(
                                                            customDurationSeconds = curr + 5
                                                        )
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = "Increase Duration", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                                }
                                            }

                                            // Repeats stepper (e.g. 1, 2, 3...)
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(top = 2.dp)
                                            ) {
                                                Text(
                                                    text = "Repeats: ",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                IconButton(
                                                    onClick = {
                                                        val curr = shortcutExercise.repeatCount
                                                        if (curr > 1) {
                                                            composedExercisesState[index] = shortcutExercise.copy(
                                                                repeatCount = curr - 1
                                                            )
                                                        }
                                                    },
                                                    modifier = Modifier.size(24.dp).testTag("decrease_repeats_$index")
                                                ) {
                                                    Icon(Icons.Default.Remove, contentDescription = "Decrease Repeats", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                                }
                                                Text(
                                                    text = "${shortcutExercise.repeatCount}x",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.padding(horizontal = 4.dp)
                                                )
                                                IconButton(
                                                    onClick = {
                                                        val curr = shortcutExercise.repeatCount
                                                        composedExercisesState[index] = shortcutExercise.copy(
                                                            repeatCount = curr + 1
                                                        )
                                                    },
                                                    modifier = Modifier.size(24.dp).testTag("increase_repeats_$index")
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = "Increase Repeats", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                        }

                                        // Move Up / Move Down Reordering Action Triggers
                                        Column(
                                            verticalArrangement = Arrangement.Center,
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    if (index > 0) {
                                                        val prev = composedExercisesState[index - 1]
                                                        composedExercisesState[index - 1] = shortcutExercise
                                                        composedExercisesState[index] = prev
                                                    }
                                                },
                                                enabled = index > 0,
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(
                                                onClick = {
                                                    if (index < composedExercisesState.size - 1) {
                                                        val next = composedExercisesState[index + 1]
                                                        composedExercisesState[index + 1] = shortcutExercise
                                                        composedExercisesState[index] = next
                                                    }
                                                },
                                                enabled = index < composedExercisesState.size - 1,
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(16.dp))
                                            }
                                        }

                                        // Trash item out of the list
                                        IconButton(
                                            onClick = { composedExercisesState.removeAt(index) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remove exercise",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Submit button
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (nameInput.isNotBlank()) {
                                if (composingShortcutId == null) {
                                    val newShortcut = Shortcut(
                                        id = UUID.randomUUID().toString(),
                                        name = nameInput,
                                        isEmoji = isEmojiSelection,
                                        iconValue = iconInputVal,
                                        exercises = composedExercisesState.toList(),
                                        completionMode = completionModeInput,
                                        mode = composingShortcutMode
                                    )
                                    viewModel.addShortcut(newShortcut)
                                } else {
                                    val updatedShortcut = Shortcut(
                                        id = composingShortcutId!!,
                                        name = nameInput,
                                        isEmoji = isEmojiSelection,
                                        iconValue = iconInputVal,
                                        exercises = composedExercisesState.toList(),
                                        completionMode = completionModeInput,
                                        mode = composingShortcutMode
                                    )
                                    viewModel.updateShortcut(updatedShortcut)
                                }
                                isComposingShortcut = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        enabled = nameInput.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_shortcut_button")
                    ) {
                        Text("Save Routine Shortcut")
                    }
                }
            }

            // --- Browse & Filter Dialog to Append Exercises ---
            if (showBrowseExercisesDialog) {
                AlertDialog(
                    onDismissRequest = { showBrowseExercisesDialog = false },
                    title = { Text("List of Practices", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                        ) {
                            // Local filters
                            OutlinedTextField(
                                value = exerciseSearchQuery,
                                onValueChange = { exerciseSearchQuery = it },
                                placeholder = { Text("Filter by name...") },
                                leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            )

                            // Horizontal Category filter chips inside dialog
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = filteredExerciseCategory == null,
                                    onClick = { filteredExerciseCategory = null },
                                    label = { Text("All", fontSize = 11.sp) }
                                )
                                categories.forEach { cat ->
                                    FilterChip(
                                        selected = filteredExerciseCategory?.id == cat.id,
                                        onClick = { filteredExerciseCategory = cat },
                                        label = { Text(cat.name, fontSize = 11.sp) }
                                    )
                                }
                            }

                            // List of exercises matching filtered criteria
                            val matches = allExercises.filter { exe ->
                                val matchMode = exe.mode == composingShortcutMode
                                val matchQuery = exe.name.contains(exerciseSearchQuery, ignoreCase = true) ||
                                        exe.getLocalizedName(languageCode).contains(exerciseSearchQuery, ignoreCase = true)
                                val matchCat = filteredExerciseCategory == null || exe.categoryId == filteredExerciseCategory!!.id
                                matchMode && matchQuery && matchCat
                            }

                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            ) {
                                if (matches.isEmpty()) {
                                    item {
                                        Text("No matches.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    items(matches) { itemExe ->
                                        // Check if already contains
                                        val isAlreadyAdded = composedExercisesState.any { it.exerciseId == itemExe.id }

                                        Card(
                                            onClick = {
                                                if (!isAlreadyAdded) {
                                                    composedExercisesState.add(
                                                        ShortcutExercise(
                                                            exerciseId = itemExe.id,
                                                            customDurationSeconds = itemExe.defaultDurationSeconds
                                                        )
                                                    )
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isAlreadyAdded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                                else MaterialTheme.colorScheme.surface
                                            ),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(10.dp)
                                            ) {
                                                Text(
                                                    text = ""
                                                 )
                                             }
                                             Row(
                                                 verticalAlignment = Alignment.CenterVertically,
                                                 modifier = Modifier.padding(8.dp)
                                             ) {
                                                 val matchedCategory = categories.find { it.id == itemExe.categoryId }
                                                 // Thumbnail of the exercise media
                                                 Box(
                                                     contentAlignment = Alignment.Center,
                                                     modifier = Modifier
                                                         .size(52.dp)
                                                         .clip(RoundedCornerShape(8.dp))
                                                         .drawBehind {
                                                             if (itemExe.media.mediaUri.isBlank()) {
                                                                 val bgGradient = Brush.verticalGradient(
                                                                     colors = when (itemExe.categoryId) {
                                                                         "neck", "head" -> listOf(Color(0xFFE2ECE2), Color(0xFFD3E7D3))
                                                                         "eyes" -> listOf(Color(0xFFE1EBF2), Color(0xFFCFDDE7))
                                                                         "hands", "arms" -> listOf(Color(0xFFF7ECE6), Color(0xFFECD7CC))
                                                                         "abs", "hips" -> listOf(Color(0xFFEDE7F2), Color(0xFFDDD2E8))
                                                                         "legs" -> listOf(Color(0xFFF6F7E6), Color(0xFFEBECC8))
                                                                         "full_body" -> listOf(Color(0xFFEDF2F0), Color(0xFFDBE7E4))
                                                                         else -> listOf(Color(0xFFEDE9E5), Color(0xFFE5DCD4))
                                                                     }
                                                                 )
                                                                 drawRect(brush = bgGradient)
                                                             }
                                                         }
                                                 ) {
                                                     if (itemExe.media.mediaUri.isNotBlank()) {
                                                         LoopingMediaView(
                                                             media = itemExe.media,
                                                             modifier = Modifier.fillMaxSize()
                                                         )
                                                     } else {
                                                         if (matchedCategory != null) {
                                                             Text(
                                                                 text = matchedCategory.iconValue,
                                                                 fontSize = 24.sp
                                                             )
                                                         } else {
                                                             Text(
                                                                 text = "🧘",
                                                                 fontSize = 24.sp
                                                             )
                                                         }
                                                     }
                                                 }

                                                 Spacer(modifier = Modifier.width(10.dp))

                                                 Column(
                                                     modifier = Modifier.weight(1f),
                                                     verticalArrangement = Arrangement.spacedBy(2.dp)
                                                 ) {
                                                     Text(
                                                         text = itemExe.getLocalizedName(languageCode),
                                                         fontSize = 13.sp,
                                                         fontWeight = FontWeight.Bold,
                                                         color = MaterialTheme.colorScheme.onSurface,
                                                         maxLines = 1,
                                                         overflow = TextOverflow.Ellipsis
                                                     )
                                                     Text(
                                                         text = matchedCategory?.name ?: "Practice",
                                                         fontSize = 11.sp,
                                                         fontWeight = FontWeight.Medium,
                                                         color = MaterialTheme.colorScheme.primary
                                                     )
                                                 }

                                                 Spacer(modifier = Modifier.width(8.dp))

                                                 if (isAlreadyAdded) {
                                                     Icon(
                                                         imageVector = Icons.Default.CheckCircle,
                                                         contentDescription = "Added",
                                                         tint = MaterialTheme.colorScheme.primary,
                                                         modifier = Modifier.size(24.dp)
                                                     )
                                                 } else {
                                                     Icon(
                                                         imageVector = Icons.Default.AddCircleOutline,
                                                         contentDescription = "Add Exercise",
                                                         tint = MaterialTheme.colorScheme.secondary,
                                                         modifier = Modifier.size(24.dp)
                                                     )
                                                 }
                                             }
                                             if (false) {
                                                 Text(
                                                     text = "",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (isAlreadyAdded) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Added",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                } else {
                                                    Text(
                                                        text = "Add +",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { showBrowseExercisesDialog = false },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Done")
                        }
                    },
                    shape = RoundedCornerShape(24.dp)
                )
            }

            // --- Browse & Filter Dialog to Append Shortcuts (Nesting) ---
            if (showBrowseShortcutsDialog) {
                AlertDialog(
                    onDismissRequest = { showBrowseShortcutsDialog = false },
                    title = { Text("List of Saved Routines", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                        ) {
                            OutlinedTextField(
                                value = shortcutSearchQuery,
                                onValueChange = { shortcutSearchQuery = it },
                                placeholder = { Text("Filter routines by name...") },
                                leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            )

                            // List of shortcuts matching query
                            val matches = shortcuts.filter { sc ->
                                sc.mode == composingShortcutMode && sc.name.contains(shortcutSearchQuery, ignoreCase = true)
                            }

                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            ) {
                                if (matches.isEmpty()) {
                                    item {
                                        Text("No routines match.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    items(matches) { candidateSc ->
                                        val isCurrent = candidateSc.id == composingShortcutId
                                        val isCircularCycle = composingShortcutId?.let { selfId ->
                                            isShortcutNested(selfId, candidateSc.id, shortcuts)
                                        } ?: false

                                        val isBlocked = isCurrent || isCircularCycle
                                        // Check if already nested in the current composed exercises state
                                        val isAlreadyAdded = composedExercisesState.any { it.nestedShortcutId == candidateSc.id }

                                        Card(
                                            onClick = {
                                                if (isBlocked) {
                                                    // Blocked click
                                                } else if (!isAlreadyAdded) {
                                                    composedExercisesState.add(
                                                        ShortcutExercise(
                                                            exerciseId = "", // representing nested shortcut item
                                                            customDurationSeconds = 0,
                                                            nestedShortcutId = candidateSc.id
                                                        )
                                                    )
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = when {
                                                    isBlocked -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                                                    isAlreadyAdded -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                                    else -> MaterialTheme.colorScheme.surface
                                                }
                                            ),
                                            border = BorderStroke(
                                                1.dp,
                                                when {
                                                    isBlocked -> MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                                                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                                }
                                            ),
                                            modifier = Modifier.fillMaxWidth().testTag("nested_shortcut_picker_item_${candidateSc.id}")
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(10.dp)
                                            ) {
                                                Text(
                                                    text = candidateSc.iconValue,
                                                    fontSize = 18.sp,
                                                    modifier = Modifier.padding(end = 6.dp)
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = candidateSc.name,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = if (isBlocked) "Circular Dependency Blocked" else "Contains ${candidateSc.exercises.size} items",
                                                        fontSize = 10.sp,
                                                        color = if (isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (isAlreadyAdded) {
                                                     Icon(
                                                         imageVector = Icons.Default.Check,
                                                         contentDescription = "Added",
                                                         tint = MaterialTheme.colorScheme.primary,
                                                         modifier = Modifier.size(16.dp)
                                                     )
                                                } else if (isBlocked) {
                                                     Icon(
                                                         imageVector = Icons.Default.Warning,
                                                         contentDescription = "Invalid Option",
                                                         tint = MaterialTheme.colorScheme.error,
                                                         modifier = Modifier.size(16.dp)
                                                     )
                                                } else {
                                                    Text(
                                                        text = "Add +",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { showBrowseShortcutsDialog = false },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Done")
                        }
                    },
                    shape = RoundedCornerShape(24.dp)
                )
            }
        }
    } else {
        // --- PRIMARY VIEW: MY SHORTCUTS DIRECTORY LISTING ---
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text("My Custom Shortcuts", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        // Reset form state for brand-new routine
                        composingShortcutId = null
                        nameInput = ""
                        isEmojiSelection = true
                        iconInputVal = "🧘"
                        composedExercisesState.clear()
                        completionModeInput = "AUTO_PROCEED"
                        composingShortcutMode = activeMode
                        isComposingShortcut = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    modifier = Modifier.testTag("create_shortcut_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Routine")
                }
            },
            modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    text = "Bespoke composite wellness flows stacked with custom timer intervals so you can play your daily routine uninterrupted.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (shortcuts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No custom shortcut sequences yet.",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Build personalized morning routines now!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    var activeLengthFilter by remember { mutableStateOf<String?>(null) }
                    val lazyListState = rememberLazyListState()
                    val filteredShortcutsList = remember(shortcuts, activeMode) { shortcuts.filter { it.mode == activeMode } }
                    var localShortcuts by remember(filteredShortcutsList) { mutableStateOf(filteredShortcutsList) }
                    var activeDraggingId by remember { mutableStateOf<String?>(null) }
                    var dragOffset by remember { mutableStateOf(0f) }

                    val displayedShortcuts = remember(localShortcuts, activeLengthFilter, allExercises) {
                        if (activeLengthFilter == null) {
                            localShortcuts
                        } else {
                            localShortcuts.filter { sc ->
                                val durationSecs = calculateShortcutDurationSeconds(sc.id, localShortcuts, allExercises)
                                val durationMinutes = durationSecs / 60.0
                                when (activeLengthFilter) {
                                    "QUICK" -> durationMinutes < 5.0
                                    "MEDIUM" -> durationMinutes >= 5.0 && durationMinutes < 15.0
                                    "LONG" -> durationMinutes >= 15.0
                                    else -> true
                                }
                            }
                        }
                    }

                    // Routine Length Filter buttons row
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Routine Length Filter",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val filterOptions = listOf(
                                Triple(null, "All", "⏳"),
                                Triple("QUICK", "Quick (<5m)", "⚡"),
                                Triple("MEDIUM", "Med (5-15m)", "🪷"),
                                Triple("LONG", "Long (15m+)", "🔥")
                            )
                            filterOptions.forEach { (filterType, label, emoji) ->
                                val isSelected = activeLengthFilter == filterType
                                ElevatedFilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        activeLengthFilter = filterType
                                    },
                                    label = {
                                        Text(
                                            text = "$emoji $label",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            ),
                                            maxLines = 1
                                        )
                                    },
                                    modifier = Modifier.weight(1f).testTag("filter_chip_${filterType ?: "all"}"),
                                    colors = FilterChipDefaults.elevatedFilterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }

                    LazyColumn(
                        state = lazyListState,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(shortcuts) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        val draggedItem = lazyListState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { item ->
                                                offset.y.toInt() in item.offset..(item.offset + item.size)
                                            }
                                        if (draggedItem != null) {
                                            activeDraggingId = draggedItem.key as? String
                                            dragOffset = 0f
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset += dragAmount.y

                                        val draggedItem = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == activeDraggingId }
                                        if (draggedItem != null) {
                                            val draggedItemCenterY = draggedItem.offset + draggedItem.size / 2f + dragOffset

                                            val targetItem = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                                                item.key != draggedItem.key && draggedItemCenterY.toInt() in item.offset..(item.offset + item.size)
                                            }

                                            if (targetItem != null) {
                                                val originIndex = localShortcuts.indexOfFirst { it.id == draggedItem.key }
                                                val realTargetIndex = localShortcuts.indexOfFirst { it.id == targetItem.key }

                                                if (originIndex != -1 && realTargetIndex != -1) {
                                                    localShortcuts = localShortcuts.toMutableList().apply {
                                                        val item = removeAt(originIndex)
                                                        add(realTargetIndex, item)
                                                    }
                                                    dragOffset -= (targetItem.offset - draggedItem.offset)
                                                }
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        viewModel.updateShortcutsOrder(localShortcuts)
                                        activeDraggingId = null
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        activeDraggingId = null
                                        dragOffset = 0f
                                    }
                                )
                            }
                    ) {
                        items(displayedShortcuts, key = { it.id }) { shortcut ->
                            val isDragging = shortcut.id == activeDraggingId
                            val itemDragModifier = if (isDragging) {
                                Modifier
                                    .zIndex(10f)
                                    .offset { IntOffset(0, dragOffset.roundToInt()) }
                            } else {
                                Modifier.animateItem()
                            }
                            ShortcutItemCard(
                                shortcut = shortcut,
                                allShortcuts = shortcuts,
                                allExercises = allExercises,
                                onStart = { onStartShortcutClick(shortcut.id) },
                                onEdit = {
                                    composingShortcutId = shortcut.id
                                    nameInput = shortcut.name
                                    isEmojiSelection = shortcut.isEmoji
                                    iconInputVal = shortcut.iconValue
                                    composedExercisesState.clear()
                                    composedExercisesState.addAll(shortcut.exercises)
                                    completionModeInput = shortcut.completionMode
                                    composingShortcutMode = shortcut.mode
                                    isComposingShortcut = true
                                },
                                onDelete = {
                                    deletingShortcut = shortcut
                                    showDeleteConfirmDialog = true
                                },
                                isDragging = isDragging,
                                modifier = itemDragModifier
                            )
                        }
                    }
                }
            }

            // Delete Confirm dialog
            if (showDeleteConfirmDialog && deletingShortcut != null) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmDialog = false },
                    title = { Text("Delete Routine?", fontWeight = FontWeight.Bold) },
                    text = { Text("Are you absolutely sure you want to permanently delete custom sequence '${deletingShortcut!!.name}'?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.deleteShortcut(deletingShortcut!!.id)
                                showDeleteConfirmDialog = false
                                deletingShortcut = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(12.dp)
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
        }
    }
}

@Composable
fun ShortcutItemCard(
    shortcut: Shortcut,
    allShortcuts: List<Shortcut>,
    allExercises: List<Exercise>,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    isDragging: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    val totalSeconds = remember(shortcut, allShortcuts, allExercises) {
        calculateShortcutDurationSeconds(shortcut.id, allShortcuts, allExercises)
    }
    val minOfLength = totalSeconds / 60
    val secOfLength = totalSeconds % 60
    val durationLabel = if (minOfLength > 0) "${minOfLength}m ${secOfLength}s" else "${secOfLength}s"

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 8.dp else 2.dp),
        border = BorderStroke(
            width = if (isDragging) 2.dp else 1.dp,
            color = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("shortcut_item_card_${shortcut.id}")
            .scale(if (isDragging) 1.03f else 1f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Prominent Representative Icon/Emoji
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            ) {
                if (shortcut.isEmoji) {
                    Text(text = shortcut.iconValue, fontSize = 28.sp)
                } else {
                    AsyncImage(
                        model = shortcut.iconValue,
                        contentDescription = "Shortcut Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Text Info Panel (Name & Details)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = shortcut.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsRun,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${shortcut.exercises.size} movement${if (shortcut.exercises.size != 1) "s" else ""}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                    Text(
                        text = if (shortcut.completionMode == "DONE") "Done Tap" else "Auto Adv", maxLines = 1, softWrap = false,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = durationLabel, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Play / Start Button
            IconButton(
                onClick = onStart,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .testTag("start_shortcut_button_${shortcut.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Start Routine",
                    modifier = Modifier.size(24.dp)
                )
            }

            // Dropdown Menu Action Button (Overflow dots)
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.testTag("menu_shortcut_button_${shortcut.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Routine Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit Option") },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        modifier = Modifier.testTag("edit_shortcut_button_${shortcut.id}")
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Permanent") },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        },
                        modifier = Modifier.testTag("delete_shortcut_button_${shortcut.id}")
                    )
                }
            }
        }
    }
}

private fun isShortcutNested(targetShortcutId: String, currentShortcutId: String, allShortcuts: List<Shortcut>): Boolean {
    if (targetShortcutId == currentShortcutId) return true
    val shortcut = allShortcuts.find { it.id == currentShortcutId } ?: return false
    for (item in shortcut.exercises) {
        val nestedId = item.nestedShortcutId
        if (nestedId != null) {
            if (isShortcutNested(targetShortcutId, nestedId, allShortcuts)) {
                return true
            }
        }
    }
    return false
}

fun calculateShortcutDurationSeconds(
    shortcutId: String,
    allShortcuts: List<Shortcut>,
    allExercises: List<Exercise>,
    visited: Set<String> = emptySet()
): Int {
    if (visited.contains(shortcutId)) return 0
    val shortcut = allShortcuts.find { it.id == shortcutId } ?: return 0
    var totalSeconds = 0
    for (item in shortcut.exercises) {
        if (item.nestedShortcutId != null) {
            val nestedDur = calculateShortcutDurationSeconds(
                item.nestedShortcutId,
                allShortcuts,
                allExercises,
                visited + shortcutId
            )
            totalSeconds += nestedDur
        } else if (item.exerciseId != null) {
            val exec = allExercises.find { it.id == item.exerciseId }
            val execDur = exec?.defaultDurationSeconds ?: 0
            totalSeconds += execDur * item.repeatCount
        }
    }
    return totalSeconds
}

