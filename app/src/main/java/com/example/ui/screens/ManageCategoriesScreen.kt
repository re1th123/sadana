package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.Category
import com.example.ui.components.IconPicker
import com.example.viewmodel.ExerciseViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageCategoriesScreen(
    viewModel: ExerciseViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categories by viewModel.categories.collectAsState()
    val exercises by viewModel.exercises.collectAsState()

    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }

    var nameInput by remember { mutableStateOf("") }
    var isEmojiSelection by remember { mutableStateOf(true) }
    var iconInputVal by remember { mutableStateOf("🧘") }

    var deletingCategory by remember { mutableStateOf<Category?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("Manage Categories", fontWeight = FontWeight.Bold, fontSize = 20.sp) 
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick, modifier = Modifier.testTag("back_button")) {
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
                    editingCategory = null
                    nameInput = ""
                    isEmojiSelection = true
                    iconInputVal = "🧘"
                    showAddEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier.testTag("add_category_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Category")
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
                text = "Structure your personal wellness practices by creating bespoke zones for physical recovery and tension relief.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                ),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 80.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(categories, key = { it.id }) { category ->
                    CategoryItemCard(
                        category = category,
                        exerciseCount = exercises.count { it.categoryId == category.id },
                        onEdit = {
                            editingCategory = category
                            nameInput = category.name
                            isEmojiSelection = category.isEmoji
                            iconInputVal = category.iconValue
                            showAddEditDialog = true
                        },
                        onDelete = {
                            deletingCategory = category
                            showDeleteConfirmDialog = true
                        }
                    )
                }
            }
        }

        // --- Dialog 1: Add / Edit Category Dialog ---
        if (showAddEditDialog) {
            AlertDialog(
                onDismissRequest = { showAddEditDialog = false },
                title = {
                    Text(
                        if (editingCategory == null) "Create Category" else "Edit Category",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                    ) {
                        item {
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                label = { Text("Category Name") },
                                placeholder = { Text("e.g. Back Relaxation") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("category_name_input")
                            )
                        }

                        item {
                            IconPicker(
                                isEmoji = isEmojiSelection,
                                onModeChange = { isEmojiSelection = it },
                                selectedValue = iconInputVal,
                                onValueSelected = { iconInputVal = it }
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (nameInput.isNotBlank()) {
                                if (editingCategory == null) {
                                    val newCat = Category(
                                        id = UUID.randomUUID().toString(),
                                        name = nameInput,
                                        isEmoji = isEmojiSelection,
                                        iconValue = iconInputVal
                                    )
                                    viewModel.addCategory(newCat)
                                } else {
                                    val updatedCat = editingCategory!!.copy(
                                        name = nameInput,
                                        isEmoji = isEmojiSelection,
                                        iconValue = iconInputVal
                                    )
                                    viewModel.updateCategory(updatedCat)
                                }
                                showAddEditDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        enabled = nameInput.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("save_category_button")
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddEditDialog = false }) {
                        Text("Cancel")
                    }
                },
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.testTag("category_add_edit_dialog")
            )
        }

        // --- Dialog 2: Delete Category Confirmation Dialog (Robust dynamic checks) ---
        if (showDeleteConfirmDialog && deletingCategory != null) {
            val assignedExercises = exercises.filter { it.categoryId == deletingCategory!!.id }
            val hasAssigned = assignedExercises.isNotEmpty()

            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = {
                    Text("Delete Category?", fontWeight = FontWeight.Bold)
                },
                text = {
                    Column {
                        Text("Are you sure you want to delete '${deletingCategory!!.name}'? This action cannot be undone.")
                        if (hasAssigned) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "⚠️ Warning: This zone has ${assignedExercises.size} exercise(s) currently assigned. Deleting this category will automatically reassign them into a safe, general 'Uncategorized' zone so they aren't lost.",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteCategory(
                                categoryId = deletingCategory!!.id,
                                reassignToUncategorized = true
                            )
                            showDeleteConfirmDialog = false
                            deletingCategory = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("confirm_delete_category")
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("Cancel")
                    }
                },
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.testTag("category_delete_confirm_dialog")
            )
        }
    }
}

@Composable
fun CategoryItemCard(
    category: Category,
    exerciseCount: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("category_item_card_${category.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Representative category icon (Supports emoji OR URL beautifully!)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            ) {
                if (category.isEmoji) {
                    Text(text = category.iconValue, fontSize = 24.sp)
                } else {
                    AsyncImage(
                        model = category.iconValue,
                        contentDescription = "Category Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Text(
                    text = "$exerciseCount exercises assigned",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            // Action controls (Do not show delete button on Uncategorized fallback to maintain stability!)
            IconButton(onClick = onEdit, modifier = Modifier.testTag("edit_category_button_${category.id}")) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Category",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            if (category.id != "uncategorized") {
                IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_category_button_${category.id}")) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Category",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
