package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.viewmodel.ExerciseViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PracticeSettingsTest {

    @Test
    fun `test settings default values`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ExerciseViewModel(application)

        // Verify initial defaults
        assertTrue(viewModel.keepScreenAwake.value) // defaults to true
        assertEquals(5, viewModel.countdownLength.value) // defaults to 5 seconds
        assertEquals("Calm Meditation", viewModel.bgSoundType.value) // defaults to Calm Meditation
        assertEquals(false, viewModel.bgSoundEnabled.value) // defaults to disabled
    }

    @Test
    fun `test settings updates persist`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ExerciseViewModel(application)

        // Perform updates
        viewModel.updateCountdownLength(10)
        viewModel.updateKeepScreenAwake(false)
        viewModel.updateBgSoundType("Zen Bells")
        viewModel.updateBgSoundEnabled(true)

        // Verify state is updated in VM
        assertEquals(10, viewModel.countdownLength.value)
        assertEquals(false, viewModel.keepScreenAwake.value)
        assertEquals("Zen Bells", viewModel.bgSoundType.value)
        assertEquals(true, viewModel.bgSoundEnabled.value)

        // Create a new VM instance and ensure they survived/restored
        val newViewModel = ExerciseViewModel(application)
        assertEquals(10, newViewModel.countdownLength.value)
        assertEquals(false, newViewModel.keepScreenAwake.value)
        assertEquals("Zen Bells", newViewModel.bgSoundType.value)
        assertEquals(true, newViewModel.bgSoundEnabled.value)
    }
}
