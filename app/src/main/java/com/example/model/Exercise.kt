package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ExerciseMode {
    GYM, YOGA
}

enum class PurposeTag(val displayName: String) {
    WARM_UP("Warm-up"),
    COOLDOWN("Cooldown"),
    MOBILITY("Mobility"),
    RECOVERY("Recovery"),
    RELAXATION("Relaxation");

    companion object {
        fun fromString(value: String): PurposeTag? {
            return entries.find { 
                it.displayName.equals(value, ignoreCase = true) || 
                it.name.replace("_", "-").equals(value, ignoreCase = true) 
            }
        }
    }
}

enum class MediaType {
    IMAGE, GIF, VIDEO;

    companion object {
        fun fromString(value: String): MediaType {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: IMAGE
        }
    }
}

data class ExerciseMedia(
    val mediaType: MediaType,
    val mediaUri: String
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val id: String,
    val name: String,
    val isEmoji: Boolean = true,
    val iconValue: String // Emoji character like "🧘" or image/gif URL
)

data class ShortcutExercise(
    val exerciseId: String,
    val customDurationSeconds: Int,
    val repeatCount: Int = 1,
    val nestedShortcutId: String? = null
)

@Entity(tableName = "shortcuts")
data class Shortcut(
    @PrimaryKey val id: String,
    val name: String,
    val isEmoji: Boolean = true,
    val iconValue: String,
    val exercises: List<ShortcutExercise>,
    val completionMode: String = "AUTO_PROCEED", // "AUTO_PROCEED" or "DONE"
    val order: Int = 0,
    val mode: ExerciseMode = ExerciseMode.YOGA
)

@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey val id: String,
    val name: String,
    val categoryId: String, // foreign-key style relation
    val purposeTags: List<PurposeTag>,
    val media: ExerciseMedia,
    val instructions: String,
    val defaultDurationSeconds: Int,
    val instructionsMap: Map<String, String> = emptyMap(),
    val nameMap: Map<String, String> = emptyMap(),
    val mode: ExerciseMode = ExerciseMode.YOGA
)

fun Exercise.getLocalizedInstructions(langCode: String): String {
    return instructionsMap[langCode] ?: instructionsMap["en"] ?: instructions
}

fun Exercise.getLocalizedName(langCode: String): String {
    return nameMap[langCode] ?: nameMap["en"] ?: name
}

@Entity(tableName = "practice_logs")
data class PracticeLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val dateString: String, // "yyyy-MM-dd" local date to simplify history and streak checks
    val shortcutId: String,
    val shortcutNameSnapshot: String = "",
    val actualDurationSeconds: Int = 0,
    val categoriesSnapshot: String = ""
)

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val isEmoji: Boolean = true,
    val avatarValue: String,
    val waterTrackerEnabled: Boolean = false,
    val waterGoalMl: Int = 2000,
    val waterReminderEnabled: Boolean = false,
    val waterReminderType: String = "INTERVAL", // "INTERVAL" or "CUSTOM"
    val waterReminderIntervalHours: Int = 2, // 1, 2, or 3
    val waterReminderCustomTimes: String = "08:00,12:00,16:00,20:00", // comma separated list
    val languageCode: String = "en"
)

@Entity(tableName = "water_logs")
data class WaterLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val amountMl: Int,
    val dateString: String // "yyyy-MM-dd"
)

object ExerciseRepository {
    val defaultCategories = listOf(
        Category(id = "neck", name = "Neck", isEmoji = true, iconValue = "🧘"),
        Category(id = "head", name = "Head", isEmoji = true, iconValue = "💆"),
        Category(id = "eyes", name = "Eyes", isEmoji = true, iconValue = "☁️"),
        Category(id = "hands", name = "Hands", isEmoji = true, iconValue = "🌿"),
        Category(id = "arms", name = "Arms", isEmoji = true, iconValue = "💪"),
        Category(id = "abs", name = "Abs", isEmoji = true, iconValue = "✨"),
        Category(id = "hips", name = "Hips", isEmoji = true, iconValue = "🧘"),
        Category(id = "legs", name = "Legs", isEmoji = true, iconValue = "🏃"),
        Category(id = "full_body", name = "Full Body", isEmoji = true, iconValue = "🌸")
    )

    val defaultShortcuts = listOf(
        Shortcut(
            id = "morning_wakeup",
            name = "5-Minute Morning Wake-Up",
            isEmoji = true,
            iconValue = "🌅",
            exercises = listOf(
                ShortcutExercise(exerciseId = "neck_rolls", customDurationSeconds = 30, repeatCount = 2),
                ShortcutExercise(exerciseId = "wrist_circles", customDurationSeconds = 30, repeatCount = 2),
                ShortcutExercise(exerciseId = "sun_salutation_flow", customDurationSeconds = 60, repeatCount = 2),
                ShortcutExercise(exerciseId = "downward_dog", customDurationSeconds = 60, repeatCount = 1)
            ),
            completionMode = "AUTO_PROCEED",
            order = 0,
            mode = ExerciseMode.YOGA
        ),
        Shortcut(
            id = "gym_cooldown",
            name = "Post-Gym Cooldown",
            isEmoji = true,
            iconValue = "🏋️",
            exercises = listOf(
                ShortcutExercise(exerciseId = "triceps_stretch", customDurationSeconds = 60, repeatCount = 1),
                ShortcutExercise(exerciseId = "standing_quad", customDurationSeconds = 60, repeatCount = 1),
                ShortcutExercise(exerciseId = "hamstring_fold", customDurationSeconds = 90, repeatCount = 1),
                ShortcutExercise(exerciseId = "shoulder_shrugs", customDurationSeconds = 90, repeatCount = 1)
            ),
            completionMode = "AUTO_PROCEED",
            order = 1,
            mode = ExerciseMode.GYM
        ),
        Shortcut(
            id = "desk_break",
            name = "Office Desk Break",
            isEmoji = true,
            iconValue = "💻",
            exercises = listOf(
                ShortcutExercise(exerciseId = "neck_side_stretch", customDurationSeconds = 45, repeatCount = 1),
                ShortcutExercise(exerciseId = "jaw_release", customDurationSeconds = 30, repeatCount = 1),
                ShortcutExercise(exerciseId = "eye_focus_shift", customDurationSeconds = 30, repeatCount = 1),
                ShortcutExercise(exerciseId = "cat_cow_seated", customDurationSeconds = 45, repeatCount = 1),
                ShortcutExercise(exerciseId = "seated_twists", customDurationSeconds = 60, repeatCount = 1)
            ),
            completionMode = "AUTO_PROCEED",
            order = 2,
            mode = ExerciseMode.YOGA
        ),
        Shortcut(
            id = "evening_winddown",
            name = "Evening Wind-Down",
            isEmoji = true,
            iconValue = "🌙",
            exercises = listOf(
                ShortcutExercise(exerciseId = "scalp_massage", customDurationSeconds = 60, repeatCount = 1),
                ShortcutExercise(exerciseId = "eye_palming", customDurationSeconds = 60, repeatCount = 1),
                ShortcutExercise(exerciseId = "butterfly_pose", customDurationSeconds = 90, repeatCount = 1),
                ShortcutExercise(exerciseId = "childs_pose", customDurationSeconds = 90, repeatCount = 2)
            ),
            completionMode = "AUTO_PROCEED",
            order = 3,
            mode = ExerciseMode.YOGA
        ),
        Shortcut(
            id = "zen_flow",
            name = "Zen Daily Flow",
            isEmoji = true,
            iconValue = "✨",
            exercises = listOf(
                ShortcutExercise(exerciseId = "neck_rolls", customDurationSeconds = 30, repeatCount = 1),
                ShortcutExercise(exerciseId = "eye_palming", customDurationSeconds = 45, repeatCount = 1),
                ShortcutExercise(exerciseId = "childs_pose", customDurationSeconds = 60, repeatCount = 1)
            ),
            completionMode = "AUTO_PROCEED",
            order = 4,
            mode = ExerciseMode.YOGA
        )
    )

    val exercises = listOf(
        Exercise(
            id = "neck_rolls",
            name = "Gentle Neck Rolls",
            categoryId = "neck",
            purposeTags = listOf(PurposeTag.MOBILITY, PurposeTag.RELAXATION),
            media = ExerciseMedia(MediaType.IMAGE, ""),
            instructions = "Sit or stand with your posture tall and shoulders relaxed. Slowly drop your chin towards your chest. Slowly roll your right ear toward your right shoulder, hold for a breath, then gently roll your head back and across to the left shoulder. Repeat in a smooth, continuous circle, breathing deeply. Reverse the direction after 30 seconds. Avoid pinching the back of your neck.",
            defaultDurationSeconds = 60,
            instructionsMap = mapOf(
                "en" to "Sit or stand with your posture tall and shoulders relaxed. Slowly drop your chin towards your chest. Slowly roll your right ear toward your right shoulder, hold for a breath, then gently roll your head back and across to the left shoulder. Repeat in a smooth, continuous circle, breathing deeply. Reverse the direction after 30 seconds. Avoid pinching the back of your neck.",
                "hi" to "सीधे बैठें या खड़े हों और कंधों को ढीला छोड़ें। धीरे-धीरे अपनी ठुड्डी को छाती की तरफ झुकाएं। फिर धीरे-धीरे अपने दाहिने कान को दाहिने कंधे की तरफ ले जाएं, एक सांस के लिए रुकें, और सिर को पीछे घुमाते हुए बाएं कंधे की ओर लाएं। इसी तरह गोलाई में घुमाते रहें। ३० सेकंड के बाद दिशा बदलें।"
            ),
            nameMap = mapOf(
                "en" to "Gentle Neck Rolls",
                "hi" to "कोमल गर्दन रोटेशन"
            )
        ),
        Exercise(
            id = "neck_side_stretch",
            name = "Seated Lateral Neck Stretch",
            categoryId = "neck",
            purposeTags = listOf(PurposeTag.MOBILITY, PurposeTag.RECOVERY),
            media = ExerciseMedia(MediaType.IMAGE, ""),
            instructions = "Sit upright. Hold the bottom edge of your chair with your right hand to anchor your shoulder down. Gently drop your left ear towards your left shoulder. For a deeper stretch, place your left hand lightly on top of your head and apply very gentle pressure. Breathe deeply into the side of your neck for 30 seconds, then switch sides.",
            defaultDurationSeconds = 60,
            instructionsMap = mapOf(
                "en" to "Sit upright. Hold the bottom edge of your chair with your right hand to anchor your shoulder down. Gently drop your left ear towards your left shoulder. For a deeper stretch, place your left hand lightly on top of your head and apply very gentle pressure. Breathe deeply into the side of your neck for 30 seconds, then switch sides.",
                "hi" to "सीधे बैठें। दाहिने कंधे को नीचे रखने के लिए कुर्सी का किनारा पकड़ें। धीरे से बाएं कान को बाएं कंधे की तरफ झुकाएं। ३० सेकंड के लिए गहरी सांसें लें, फिर दूसरी तरफ दोहराएं।"
            ),
            nameMap = mapOf(
                "en" to "Seated Lateral Neck Stretch",
                "hi" to "गर्दन का पार्श्व खिंचाव"
            )
        ),
        Exercise(
            id = "jaw_release",
            name = "Myofascial Jaw Release",
            categoryId = "head",
            purposeTags = listOf(PurposeTag.RELAXATION, PurposeTag.RECOVERY),
            media = ExerciseMedia(MediaType.IMAGE, ""),
            instructions = "Place the pads of your fingers or knuckles on the masseter muscles of your jaw, just below your cheekbones and in front of your ears. Press gently and open your mouth slowly as you slide your knuckles downward along the jawline. You may feel a mild release. Repeat 5-10 times to drop tension from subconscious clenching.",
            defaultDurationSeconds = 45,
            instructionsMap = mapOf(
                "en" to "Place the pads of your fingers or knuckles on the masseter muscles of your jaw, just below your cheekbones and in front of your ears. Press gently and open your mouth slowly as you slide your knuckles downward along the jawline. You may feel a mild release. Repeat 5-10 times to drop tension from subconscious clenching.",
                "hi" to "अपनी उंगलियों या पोरों को जबड़े की मांसपेशियों पर रखें (कान के ठीक सामने)। धीरे से दबाएं और मुंह धीरे-धीरे खोलते हुए उंगलियों को नीचे की तरफ स्लाइड करें। ५-१० बार दोहराएं।"
            ),
            nameMap = mapOf(
                "en" to "Myofascial Jaw Release",
                "hi" to "मायोफेशियल जबड़ा तनावमुक्ति"
            )
        ),
        Exercise(
            id = "scalp_massage",
            name = "Tension-Relieving Scalp Slide",
            categoryId = "head",
            purposeTags = listOf(PurposeTag.RELAXATION, PurposeTag.COOLDOWN),
            media = ExerciseMedia(MediaType.IMAGE, ""),
            instructions = "Spread your fingers wide and place them firmly on your scalp above your ears. Without sliding your fingers over your hair, physically slide your scalp in slow circular movements relative to the skull. Move your hands around to the back of the head and the crown. This stimulates blood flow, calms the nervous system, and breaks down bound fascia.",
            defaultDurationSeconds = 90,
            instructionsMap = mapOf(
                "en" to "Spread your fingers wide and place them firmly on your scalp above your ears. Without sliding your fingers over your hair, physically slide your scalp in slow circular movements relative to the skull. Move your hands around to the back of the head and the crown. This stimulates blood flow, calms the nervous system, and breaks down bound fascia.",
                "hi" to "अपनी उंगलियों को फैलाएं और उन्हें कानों के ऊपर सिर की त्वचा पर रखें। उंगलियों को बालों पर बिना खिसकाए, सिर की त्वचा को गोलाई में धीरे-धीरे हिलाएं। इससे तनाव से आराम मिलता है।"
            ),
            nameMap = mapOf(
                "en" to "Tension-Relieving Scalp Slide",
                "hi" to "तनाव मुक्त स्कैल्प मालिश"
            )
        ),
        Exercise(
            id = "eye_palming",
            name = "Warm Eye Palming",
            categoryId = "eyes",
            purposeTags = listOf(PurposeTag.RELAXATION, PurposeTag.RECOVERY),
            media = ExerciseMedia(MediaType.IMAGE, ""),
            instructions = "Vigorously rub your palms together for 15 seconds until they feel warm and energized. Close your eyes and gently cup your warm palms over your orbits without putting pressure directly on the eyeballs. Let the pitch-black darkness and soothing thermal energy sink into your ocular muscles. Breathe deeply for 1 minute.",
            defaultDurationSeconds = 60,
            instructionsMap = mapOf(
                "en" to "Vigorously rub your palms together for 15 seconds until they feel warm and energized. Close your eyes and gently cup your warm palms over your orbits without putting pressure directly on the eyeballs. Let the pitch-black darkness and soothing thermal energy sink into your ocular muscles. Breathe deeply for 1 minute.",
                "hi" to "अपने दोनों हाथों को १५ सेकंड तक तेजी से रगड़ें जब तक कि वे गर्म न हो जाएं। अपनी आँखें बंद करें और अपनी गर्म हथेलियों को धीरे से अपनी आँखों पर रखें। आँखों पर सीधे दबाव न डालें। गहरी सांसें लें और शांत महसूस करें।"
            ),
            nameMap = mapOf(
                "en" to "Warm Eye Palming",
                "hi" to "वॉर्म ऑय पामिंग"
            )
        ),
        Exercise(
            id = "eye_focus_shift",
            name = "20-20 Ocular Range Shift",
            categoryId = "eyes",
            purposeTags = listOf(PurposeTag.MOBILITY, PurposeTag.RELAXATION),
            media = ExerciseMedia(MediaType.IMAGE, ""),
            instructions = "Hold your thumb out about 10 inches in front of your eyes and focus on its fine lines. After 5 seconds, shift your focus to a point at least 20 feet away (e.g., out a window) and trace its edges. Alternate your focus back and forth smoothly every few seconds. This prevents digital eye strain and exercises the ciliary muscles.",
            defaultDurationSeconds = 45
        ),
        Exercise(
            id = "wrist_circles",
            name = "Wrist Wave & Circles",
            categoryId = "hands",
            purposeTags = listOf(PurposeTag.MOBILITY, PurposeTag.WARM_UP),
            media = ExerciseMedia(MediaType.IMAGE, ""),
            instructions = "Clasp your fingers together in front of your chest. Press your palms together and roll your wrists in a continuous fluid, infinity-shaped wave motion. Keep the action soft and painless. After several loops, reverse the direction. This lubricates the wrist joints, stimulates carpal tunnel release, and warms up the hands.",
            defaultDurationSeconds = 30
        ),
        Exercise(
            id = "finger_spreads",
            name = "Tension-Release Finger Extensions",
            categoryId = "hands",
            purposeTags = listOf(PurposeTag.RECOVERY, PurposeTag.WARM_UP),
            media = ExerciseMedia(MediaType.IMAGE, ""),
            instructions = "Make a gentle, tight fist with both hands, wrapping your thumbs over your fingers. Hold for 2 seconds. Then, aggressively and fully splay all ten fingers as wide as possible, stretching the webs between them. Hold for 2 seconds. Alternate between fist and maximum extension smoothly of about 15 reps.",
            defaultDurationSeconds = 45
        ),
        Exercise(
            id = "shoulder_shrugs",
            name = "Active Shoulder Shrugs",
            categoryId = "arms",
            purposeTags = listOf(PurposeTag.MOBILITY, PurposeTag.WARM_UP),
            media = ExerciseMedia(MediaType.IMAGE, ""),
            instructions = "With your arms hanging naturally at your sides, inhale deeply and shrug both shoulders straight up towards your ears as high as comfortable. Hold them high for a micro-pause, then exhale while forcefully dropping them down, releasing all physical weight. Follow with 10 slow backwards shoulder rolls.",
            defaultDurationSeconds = 30,
            mode = ExerciseMode.GYM
        ),
        Exercise(
            id = "triceps_stretch",
            name = "Overhead Triceps & Lat Stretch",
            categoryId = "arms",
            purposeTags = listOf(PurposeTag.COOLDOWN, PurposeTag.RECOVERY),
            media = ExerciseMedia(MediaType.IMAGE, ""),
            instructions = "Raise your right arm straight up toward the sky. Bend your elbow and reach your right hand down the center of your back, between your shoulder blades. Reach your left hand overhead and grab your right elbow. Pull back gently while leaning your torso slightly to the left. Hold for 30 seconds, then swap arms.",
            defaultDurationSeconds = 60,
            mode = ExerciseMode.GYM
        ),
        Exercise(
            id = "cat_cow_seated",
            name = "Seated Cat-Cow Flow",
            categoryId = "abs",
            purposeTags = listOf(PurposeTag.MOBILITY, PurposeTag.WARM_UP),
            media = ExerciseMedia(MediaType.IMAGE, ""),
            instructions = "Sit upright on a stable surface, resting your hands flat on your knees. Inhale, push your chest forwards, roll your shoulders back, and arch your spine slightly to lift your gaze (Cow). Exhale, round your spine completely, tuck your chin into your chest, and pull your belly button in (Cat). Flow in sync with your breath.",
            defaultDurationSeconds = 60
        ),
        Exercise(
            id = "seated_twists",
            name = "Seated Spinal Rotation",
            categoryId = "abs",
            purposeTags = listOf(PurposeTag.MOBILITY, PurposeTag.COOLDOWN),
            media = ExerciseMedia(MediaType.IMAGE, ""),
            instructions = "Sit tall. Cross your right leg over your left or keep feet flat. Take your left hand to your outer right thigh. Place your right hand behind your lower tailbone. Inhale to lengthen your spine skyward. Exhale and roll your shoulders open to the right, looking back. Do not strain; listen to your lower back. Swap after 30 seconds.",
            defaultDurationSeconds = 60
        ),
        Exercise(
            id = "seated_90_90",
            name = "90/90 Seated Hip Opener",
            categoryId = "hips",
            purposeTags = listOf(PurposeTag.MOBILITY, PurposeTag.RECOVERY),
            media = ExerciseMedia(MediaType.IMAGE, ""),
            instructions = "Sit flat on the floor. Arrange your right leg at a 90-degree angle directly in front of you (thigh perpendicular to torso, shin vertical). Position your left leg at a 90-degree angle to the side and pointing backward. Keep your spine erect as you slowly lean your chest forward over your front shin. Hold for 45s, then switch.",
            defaultDurationSeconds = 90
        ),
        Exercise(
            id = "butterfly_pose",
            name = "Seated Butterfly Stretch",
            categoryId = "hips",
            purposeTags = listOf(PurposeTag.RELAXATION, PurposeTag.COOLDOWN),
            media = ExerciseMedia(MediaType.IMAGE, ""),
            instructions = "Sit upright on the floor with your knees bent out to the sides, bringing the soles of your feet together to touch. Grasp your ankles or toes with both hands. Gently flutter your knees up and down a few times. Lengthen your spine on an inhale, and pull your navel gently forward over your feet as you exhale, relaxing the thighs toward the ground.",
            defaultDurationSeconds = 120
        ),
        Exercise(
            id = "standing_quad",
            name = "Balanced Quad Stretch",
            categoryId = "legs",
            purposeTags = listOf(PurposeTag.COOLDOWN, PurposeTag.WARM_UP),
            media = ExerciseMedia(MediaType.IMAGE, ""),
            instructions = "Stand near a wall for balance if needed. Shift your weight on your left leg. Bend your right knee and raise your heel behind you. Reach back and capture your right foot or ankle with your right hand. Squeeze your hand toward your glute while keeping your knees close together and tucking your pelvis under. Repeat on both legs.",
            defaultDurationSeconds = 60,
            mode = ExerciseMode.GYM
        ),
        Exercise(
            id = "hamstring_fold",
            name = "Seated Single-Leg Hamstring fold",
            categoryId = "legs",
            purposeTags = listOf(PurposeTag.RECOVERY, PurposeTag.COOLDOWN),
            media = ExerciseMedia(MediaType.IMAGE, ""),
            instructions = "Sit with your right leg extended straight out, and tuck your left foot flat against your inner right thigh. Root down through your sit bones. Inhale to lift your torso, then hinge from your hips to walk your hands forward on either side of your right leg. Keep a micro-bend in your knee to isolate the hamstring tissue.",
            defaultDurationSeconds = 90,
            mode = ExerciseMode.GYM
        ),
        Exercise(
            id = "ankle_circles",
            name = "Ankle Joint Mobility Rolls",
            categoryId = "legs",
            purposeTags = listOf(PurposeTag.WARM_UP, PurposeTag.MOBILITY),
            media = ExerciseMedia(MediaType.IMAGE, ""),
            instructions = "Raise one leg slightly off the floor, or sit comfortably in a chair. Lift your foot and roll your ankle slowly in a wide circle. Pretend you are drawing circles with your big toe. Make 15 slow circles clockwise, then 15 counter-clockwise. This increases ankle range, mobilizes the achilles tendon, and fires up lower leg reflexes.",
            defaultDurationSeconds = 45
        ),
        Exercise(
            id = "sun_salutation_flow",
            name = "Mini Sun Salutation A",
            categoryId = "full_body",
            purposeTags = listOf(PurposeTag.WARM_UP, PurposeTag.MOBILITY),
            media = ExerciseMedia(MediaType.IMAGE, ""),
            instructions = "Inhale and sweep arms overhead (Mountain). Exhale and slide into a deep forward fold (Uttonasana). Inhale to a flat back. Exhale and step back into a high-to-low pushup. Inhale to Cobra or Upward-facing dog (arch chest forwards). Exhale, lift hips up and back into Downward-Facing Dog. Inhale, walk to fronts of feet and rise again.",
            defaultDurationSeconds = 120
        ),
        Exercise(
            id = "childs_pose",
            name = "Restorative Child's Pose",
            categoryId = "full_body",
            purposeTags = listOf(PurposeTag.RELAXATION, PurposeTag.COOLDOWN, PurposeTag.RECOVERY),
            media = ExerciseMedia(MediaType.IMAGE, ""),
            instructions = "Kneel on the floor, bring your big toes together and separate your knees wide about distance of your mat. Sit your hips back onto your heels. Lean forward, extending your torso to rest between your thighs, and lay your forehead flat to the ground. Extend your arms out ahead with palms down, and melt all muscles completely.",
            defaultDurationSeconds = 180
        ),
        Exercise(
            id = "downward_dog",
            name = "Active Downward-Facing Dog",
            categoryId = "full_body",
            purposeTags = listOf(PurposeTag.MOBILITY, PurposeTag.WARM_UP, PurposeTag.RECOVERY),
            media = ExerciseMedia(MediaType.IMAGE, ""),
            instructions = "Begin on your hands and knees in a tabletop position. Splay your hands flat and index fingers parallel. Tuck your toes, press down through your palms, and lift your knees off the ground. Extend your hips towards the ceiling and push your thigh bones back. Pedal your feet out slowly. Push your shoulders away from your neck.",
            defaultDurationSeconds = 90
        )
    )
}
