package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.rounded.Info
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.Category
import com.example.model.Exercise
import com.example.model.ExerciseMode
import com.example.model.MediaType
import com.example.model.ExerciseMedia
import com.example.model.PurposeTag
import com.example.model.getLocalizedInstructions
import com.example.model.getLocalizedName
import com.example.ui.components.LoopingMediaView
import com.example.viewmodel.ExerciseViewModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.BarChart

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BrowseScreen(
    viewModel: ExerciseViewModel,
    onExerciseClick: (String) -> Unit,
    onManageCategoriesClick: () -> Unit,
    onMyShortcutsClick: () -> Unit,
    onStreakClick: () -> Unit,
    onStatsAndHistoryClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedTag by viewModel.selectedTag.collectAsState()
    val exercises by viewModel.filteredExercises.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val languageCode = remember(userProfile) { userProfile.languageCode }

    val focusManager = LocalFocusManager.current

    // Dialog trigger states
    var showingAddExercise by remember { mutableStateOf(false) }
    var showingInlineCategoryAdd by remember { mutableStateOf(false) }
    var deletingExercise by remember { mutableStateOf<Exercise?>(null) }

    // Form inputs variables
    var extName by remember { mutableStateOf("") }
    var extSelectedCategoryId by remember { mutableStateOf("") }
    var extInstructions by remember { mutableStateOf("") }
    var extDuration by remember { mutableStateOf("60") }
    var extMediaType by remember { mutableStateOf(MediaType.IMAGE) }
    var extMediaUrl by remember { mutableStateOf("") }
    var extSelectedTags by remember { mutableStateOf(setOf<PurposeTag>()) }
    var extMode by remember { mutableStateOf(ExerciseMode.YOGA) }

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
            extMediaUrl = it.toString()
        }
    }

    var inlineCatName by remember { mutableStateOf("") }
    var inlineCatEmoji by remember { mutableStateOf("🧘") }

    // Auto-select first category on draw
    LaunchedEffect(categories, showingAddExercise) {
        if (showingAddExercise && extSelectedCategoryId.isEmpty()) {
            extSelectedCategoryId = categories.firstOrNull()?.id ?: ""
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showingAddExercise = true },
                icon = { Icon(Icons.Default.Add, contentDescription = "Add custom movement") },
                text = { Text("Add Exercise") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_custom_exercise_fab")
            )
        },
        containerColor = Color.Transparent,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // App Header with calming decoration and direct screen redirects
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SADANA",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 4.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.SansSerif
                            ),
                            modifier = Modifier.testTag("app_title")
                        )
                        Text(
                            text = "Daily Gym and Yoga",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                letterSpacing = 1.sp
                            )
                        )
                        
                        // Prominent, interactive Streak Pill indicator
                        val currentStreak by viewModel.currentStreak.collectAsState()
                        Spacer(modifier = Modifier.height(6.dp))
                        var showStreakMenu by remember { mutableStateOf(false) }
                        Box {
                            Card(
                                onClick = { showStreakMenu = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                modifier = Modifier.testTag("streak_indicator")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("🔥", fontSize = 12.sp)
                                    Text(
                                        text = if (currentStreak == 1) "1-day streak" else "$currentStreak-day streak",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showStreakMenu,
                                onDismissRequest = { showStreakMenu = false },
                                modifier = Modifier.testTag("streak_dropdown_menu")
                            ) {
                                DropdownMenuItem(
                                    text = { 
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Text("Practice Calendar") 
                                        }
                                    },
                                    onClick = {
                                        showStreakMenu = false
                                        onStreakClick()
                                    },
                                    modifier = Modifier.testTag("streak_menu_calendar")
                                )
                                DropdownMenuItem(
                                    text = { 
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Default.BarChart, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Text("History & Stats") 
                                        }
                                    },
                                    onClick = {
                                        showStreakMenu = false
                                        onStatsAndHistoryClick()
                                    },
                                    modifier = Modifier.testTag("streak_menu_stats")
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Quick Bookmarks trigger to "My Shortcuts" (Custom routines)
                        IconButton(
                            onClick = onMyShortcutsClick,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("my_shortcuts_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stars,
                                contentDescription = "My Shortcuts",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Settings trigger to "Manage Categories"
                        IconButton(
                            onClick = onManageCategoriesClick,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("manage_categories_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Manage Categories",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Stats & History Button
                        IconButton(
                            onClick = onStatsAndHistoryClick,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("stats_history_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = "History & Stats",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Dynamic, interactive User Profile Badge
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable { onProfileClick() }
                                .testTag("user_profile_badge")
                        ) {
                            if (userProfile.isEmoji && userProfile.avatarValue.isNotEmpty()) {
                                Text(
                                    text = userProfile.avatarValue,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = 18.sp
                                    )
                                )
                            } else if (!userProfile.isEmoji && userProfile.avatarValue.isNotEmpty()) {
                                AsyncImage(
                                    model = userProfile.avatarValue,
                                    contentDescription = "User Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                // Fallback to initials
                                val initials = userProfile.name.split(" ")
                                    .mapNotNull { it.firstOrNull() }
                                    .take(2)
                                    .joinToString("")
                                    .uppercase()
                                
                                Text(
                                    text = if (initials.isNotEmpty()) initials else "JD",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Prominent Gym/Yoga Segmented Control Toggle
            val activeMode by viewModel.activeMode.collectAsState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ExerciseMode.entries.forEach { mode ->
                        val isSelected = activeMode == mode
                        val iconValue = if (mode == ExerciseMode.YOGA) "🧘" else "🏋️"
                        val displayName = if (mode == ExerciseMode.YOGA) "Yoga Flow" else "Gym Training"
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clickable {
                                    viewModel.setActiveMode(mode)
                                }
                                .testTag("mode_toggle_${mode.name.lowercase()}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = iconValue,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Filters and Search section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 2.dp)
            ) {
                // Rounded Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = {
                        Text(
                            "Search exercises (e.g. Neck, Twist)...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exercise_search_bar")
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Section Label - Categories
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "BODY PARTS / ZONES",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                // Category horizontally scrollable chips (fully dynamic from VM!)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { viewModel.selectCategory(null) },
                            label = { Text("All Parts") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.testTag("category_chip_all")
                        )
                    }
                    items(categories, key = { it.id }) { cat ->
                        FilterChip(
                            selected = selectedCategory?.id == cat.id,
                            onClick = { viewModel.selectCategory(cat) },
                            label = { Text(cat.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.testTag("category_chip_${cat.id}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Section Label - Purpose Tags
                Text(
                    text = "PURPOSE & VIBE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )

                // Purpose Tags horizontally scrollable chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedTag == null,
                            onClick = { viewModel.selectTag(null) },
                            label = { Text("Any Purpose") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.testTag("tag_chip_all")
                        )
                    }
                    items(PurposeTag.entries.toTypedArray()) { tag ->
                        FilterChip(
                            selected = selectedTag == tag,
                            onClick = { viewModel.selectTag(tag) },
                            label = { Text(tag.displayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.testTag("tag_chip_${tag.name.lowercase()}")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Water Tracker Card (Optional / Opt-in)
            if (userProfile.waterTrackerEnabled) {
                val todayIntake by viewModel.todayWaterIntake.collectAsState()
                val goal = userProfile.waterGoalMl
                val progress = if (goal > 0) (todayIntake.toFloat() / goal.toFloat()).coerceIn(0f, 1f) else 1f
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .testTag("water_tracker_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("💧", fontSize = 24.sp)
                                Column {
                                    Text(
                                        text = "Hydration Tracker",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "Goal: ${goal}ml",
                                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f))
                                    )
                                }
                            }
                            
                            Text(
                                text = "${todayIntake} / ${goal} ml",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                            )
                        }
                        
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.tertiary,
                            trackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { viewModel.logWaterIntake(250) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("log_water_250_btn"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Text("+250ml", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            }
                            
                            Button(
                                onClick = { viewModel.logWaterIntake(500) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("log_water_500_btn"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Text("+500ml", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            }

                            OutlinedButton(
                                onClick = { viewModel.clearWaterLogs() },
                                modifier = Modifier.testTag("reset_water_btn"),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Text("Reset")
                            }
                        }
                    }
                }
            }

            // Grid/List of exercises
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (exercises.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Silently Searching...",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "No movements match your selected filters.\nTry resetting search or filters to start warm-up.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            ),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { viewModel.clearFilters() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text("Reset All Filters")
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = exercises,
                            key = { it.id }
                        ) { exercise ->
                            ExerciseCard(
                                exercise = exercise,
                                categories = categories,
                                onClick = { onExerciseClick(exercise.id) },
                                onDeleteClick = { deletingExercise = exercise },
                                languageCode = languageCode,
                                modifier = Modifier.testTag("exercise_card_${exercise.id}")
                            )
                        }
                    }
                }
            }
        }
    }

    if (deletingExercise != null) {
        AlertDialog(
            onDismissRequest = { deletingExercise = null },
            title = { Text("Delete Exercise?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete '${deletingExercise!!.name}'? This will automatically remove it from all routines.", fontSize = 14.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteExercise(deletingExercise!!.id)
                        deletingExercise = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("confirm_delete_exercise_browse_btn")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingExercise = null }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // --- DIALOGS FOR ADDING CUSTOM MOVEMENT ---
    if (showingAddExercise) {
        AlertDialog(
            onDismissRequest = { showingAddExercise = false },
            title = {
                Text(
                    text = "Craft Custom Exercise",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Exercise Name
                    OutlinedTextField(
                        value = extName,
                        onValueChange = { extName = it },
                        label = { Text("Exercise Name *") },
                        placeholder = { Text("e.g. Lotus Breath Flow") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("add_exercise_name")
                    )

                    // Gym / Yoga Mode Select
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Exercise Mode *",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ExerciseMode.entries.toTypedArray().forEach { mode ->
                                val isSelected = extMode == mode
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { extMode = mode },
                                    label = {
                                        Text(
                                            text = when (mode) {
                                                ExerciseMode.YOGA -> "🧘 Yoga"
                                                ExerciseMode.GYM -> "🏋️ Gym"
                                            }
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).testTag("select_mode_chip_${mode.name.lowercase()}")
                                )
                            }
                        }
                    }

                    // Case Category select & inline creation
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Body Part / Category *",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            TextButton(
                                onClick = { showingInlineCategoryAdd = true },
                                modifier = Modifier.testTag("add_inline_category_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New inline", fontSize = 12.sp)
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            items(categories) { cat ->
                                FilterChip(
                                    selected = extSelectedCategoryId == cat.id,
                                    onClick = { extSelectedCategoryId = cat.id },
                                    label = { Text("${cat.iconValue} ${cat.name}") },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("form_category_chip_${cat.id}")
                                )
                            }
                        }
                    }

                    // Purpose Tags multi-selector
                    Column {
                        Text(
                            "Select Purpose Tags (Pick multiple) *",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            PurposeTag.entries.toTypedArray().forEach { t ->
                                val isSelected = extSelectedTags.contains(t)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        extSelectedTags = if (isSelected) extSelectedTags - t else extSelectedTags + t
                                    },
                                    label = { Text(t.displayName) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("form_tag_chip_${t.name.lowercase()}")
                                )
                            }
                        }
                    }

                    // Duration Input field
                    OutlinedTextField(
                        value = extDuration,
                        onValueChange = { extDuration = it.filter { char -> char.isDigit() } },
                        label = { Text("Default Duration (Seconds) *") },
                        placeholder = { Text("60") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("add_exercise_duration")
                    )

                    // Write Instructions
                    OutlinedTextField(
                        value = extInstructions,
                        onValueChange = { extInstructions = it },
                        label = { Text("Alignment & Instructions *") },
                        placeholder = { Text("Describe details step-by-step...") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag("add_exercise_instructions")
                    )

                    // Media upload and Preset choices
                    Column {
                        Text(
                            "Demonstration Media",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MediaType.entries.toTypedArray().forEach { type ->
                                FilterChip(
                                    selected = extMediaType == type,
                                    onClick = {
                                        extMediaType = type
                                        // Auto load peaceful preset for smooth out-of-the-box user experience
                                        extMediaUrl = when (type) {
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
                                    modifier = Modifier.weight(1f).testTag("media_type_chip_${type.name.lowercase()}")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Curated calming motion presets selector
                        Text(
                            "Calming Motion Presets:",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val presetsForType = when (extMediaType) {
                                MediaType.IMAGE -> listOf(
                                    "Stretches" to "https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?q=80&w=600",
                                    "Mind Yoga" to "https://images.unsplash.com/photo-1506126613408-eca07ce68773?q=80&w=600"
                                )
                                MediaType.GIF -> listOf(
                                    "Breathing" to "https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExbDVqbm13MnRreDVqYzJ1bzFqMWs4MnU2bTNqdDB3MG5lOTd4YXk0ayZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/Vz9ek7jhXYrMA/giphy.gif",
                                    "Flowing" to "https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExMnB2a3g0eWZ6dzlmcWVvdmlxdzkwMW16djNhdzZ4MGdycDB2Y24waSZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/8vF8sR9Sscf7O/giphy.gif"
                                )
                                MediaType.VIDEO -> listOf(
                                    "Stream" to "https://assets.mixkit.co/videos/preview/mixkit-forest-stream-in-the-sunlight-529-large.mp4",
                                    "Waterfall" to "https://assets.mixkit.co/videos/preview/mixkit-waterfall-in-forest-2213-large.mp4"
                                )
                            }

                            presetsForType.forEach { (label, url) ->
                                Button(
                                    onClick = { extMediaUrl = url },
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

                        Spacer(modifier = Modifier.height(10.dp))

                        // Native media picker trigger
                        Button(
                            onClick = {
                                val mimeType = when (extMediaType) {
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
                            modifier = Modifier.fillMaxWidth().testTag("add_exercise_media_picker_button")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (extMediaType) {
                                    MediaType.IMAGE -> "Choose Image from Device"
                                    MediaType.GIF -> "Choose GIF from Device"
                                    MediaType.VIDEO -> "Choose Video from Device"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Custom URL text area
                        OutlinedTextField(
                            value = extMediaUrl,
                            onValueChange = { extMediaUrl = it },
                            label = { Text("Custom Media Connection URL or Device URI") },
                            placeholder = { Text("https://... or content://...") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("add_exercise_media_url")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Live media Preview area within custom form
                        if (extMediaUrl.isNotBlank()) {
                            Text(
                                "Demonstration Preview:",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                LoopingMediaView(
                                    media = ExerciseMedia(extMediaType, extMediaUrl),
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val duration = extDuration.toIntOrNull() ?: 60
                        if (extName.isNotBlank() && extSelectedCategoryId.isNotBlank() && extInstructions.isNotBlank()) {
                            val newEx = Exercise(
                                id = "custom_" + System.currentTimeMillis() + "_" + extName.hashCode().toString().take(4),
                                name = extName,
                                categoryId = extSelectedCategoryId,
                                purposeTags = extSelectedTags.toList(),
                                media = ExerciseMedia(extMediaType, extMediaUrl),
                                instructions = extInstructions,
                                defaultDurationSeconds = duration,
                                mode = extMode
                            )
                            viewModel.addExercise(newEx)
                            showingAddExercise = false

                            // Clear form inputs
                            extName = ""
                            extInstructions = ""
                            extDuration = "60"
                            extMediaUrl = ""
                            extSelectedTags = emptySet()
                            extMode = ExerciseMode.YOGA
                        }
                    },
                    modifier = Modifier.testTag("save_exercise_button")
                ) {
                    Text("Save Exercise")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showingAddExercise = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- DIALOG FOR ADDING NEW CATEGORY INLINE ---
    if (showingInlineCategoryAdd) {
        AlertDialog(
            onDismissRequest = { showingInlineCategoryAdd = false },
            title = { Text("Create Inline Body Part") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = inlineCatName,
                        onValueChange = { inlineCatName = it },
                        label = { Text("Category/Body Part Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("inline_category_name_input")
                    )
                    OutlinedTextField(
                        value = inlineCatEmoji,
                        onValueChange = { inlineCatEmoji = it },
                        label = { Text("Emoji Character") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("inline_category_emoji_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inlineCatName.isNotBlank()) {
                            val genId = "custom_cat_" + inlineCatName.lowercase().replace(" ", "_")
                            viewModel.addCategory(
                                Category(
                                    id = genId,
                                    name = inlineCatName,
                                    isEmoji = true,
                                    iconValue = inlineCatEmoji
                                )
                            )
                            extSelectedCategoryId = genId // auto-select
                            showingInlineCategoryAdd = false
                            inlineCatName = ""
                            inlineCatEmoji = "🧘"
                        }
                    },
                    modifier = Modifier.testTag("confirm_inline_category_btn")
                ) {
                    Text("Add Category")
                }
            },
            dismissButton = {
                TextButton(onClick = { showingInlineCategoryAdd = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ExerciseCard(
    exercise: Exercise,
    categories: List<Category>,
    onClick: () -> Unit,
    onDeleteClick: (() -> Unit)? = null,
    languageCode: String = "en",
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(290.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            val matchedCategory = categories.find { it.id == exercise.categoryId }

            // Elegant procedural geometric art OR dynamic looping media!
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                    .drawBehind {
                        // Background base gradient using category mapping
                        if (exercise.media.mediaUri.isBlank()) {
                            val bgGradient = Brush.verticalGradient(
                                colors = when (exercise.categoryId) {
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

                            // Calm concentric flowing energy orbits
                            val originX = size.width / 2f
                            val originY = size.height / 2f
                            val maxR = size.minDimension * 0.45f

                            drawCircle(
                                color = Color(0xFFB08C75).copy(alpha = 0.15f),
                                radius = maxR * 0.4f,
                                center = Offset(originX, originY)
                            )

                            for (i in 1..3) {
                                val radius = maxR * (i * 0.28f + 0.15f)
                                drawCircle(
                                    color = Color(0xFF5A725A).copy(alpha = 0.08f * (4 - i)),
                                    radius = radius,
                                    center = Offset(originX, originY),
                                    style = Stroke(width = 1.5.dp.toPx())
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
                } else {
                    // Centered category icon representation (Supports dynamic emojis OR image backdrops!)
                    if (matchedCategory != null) {
                        if (matchedCategory.isEmoji) {
                            Text(
                                text = matchedCategory.iconValue,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = 36.sp
                                ),
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .align(Alignment.Center)
                            ) {
                                AsyncImage(
                                    model = matchedCategory.iconValue,
                                    contentDescription = "Uploaded Category Icon",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                // Overlaid category pill badge
                Box(
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.TopStart)
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = (matchedCategory?.name ?: "YOGA").uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp
                        )
                    )
                }

                // Overlaid duration indicator
                Box(
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.BottomEnd)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${exercise.defaultDurationSeconds}s",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            // Text info block
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = exercise.getLocalizedName(languageCode),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 22.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (onDeleteClick != null) {
                            IconButton(
                                onClick = { onDeleteClick() },
                                modifier = Modifier
                                    .size(24.dp)
                                    .testTag("delete_exercise_button_${exercise.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete exercise from library",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = exercise.getLocalizedInstructions(languageCode),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Tags flow at the bottom
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    exercise.purposeTags.forEach { tag ->
                        Box(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = tag.displayName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
