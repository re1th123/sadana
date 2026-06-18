package com.example.service

import com.example.model.Category
import com.example.model.Exercise
import com.example.model.Shortcut
import com.example.model.PracticeLog
import com.example.model.UserProfile
import com.example.model.WaterLog
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BackupPayload(
    val categories: List<Category>,
    val exercises: List<Exercise>,
    val shortcuts: List<Shortcut>,
    val practiceLogs: List<PracticeLog>,
    val waterLogs: List<WaterLog>,
    val userProfile: UserProfile?,
    val settings: Map<String, String>
)
