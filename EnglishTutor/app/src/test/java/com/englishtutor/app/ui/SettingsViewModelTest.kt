package com.englishtutor.app.ui

import com.englishtutor.app.InMemoryPreferences
import com.englishtutor.app.FakeVoice
import com.englishtutor.app.data.SettingsRepository
import com.englishtutor.app.data.TutorSettings
import com.englishtutor.app.data.VoiceEngine
import com.englishtutor.app.ui.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val log = mutableListOf<String>()
    private val androidVoice = FakeVoice(log = log, tag = "android:")
    private val spanishVoice = FakeVoice(log = log, tag = "es:")

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun kotlinx.coroutines.test.TestScope.createVm(orpheus: FakeVoice = FakeVoice(log = log, tag = "orpheus:")): Pair<SettingsViewModel, SettingsRepository> {
        val repo = SettingsRepository(InMemoryPreferences())
        val vm = SettingsViewModel(repo, MutableStateFlow(emptyList()), androidVoice, orpheus, spanishVoice)
        backgroundScope.launch { vm.settings.collect {} }
        return vm to repo
    }

    @Test
    fun `probar voz habla en ingles y luego el ejemplo en espanol`() = runTest(dispatcher) {
        val (vm, _) = createVm()
        advanceUntilIdle()
        vm.preview()
        advanceUntilIdle()
        assertEquals(listOf("android:${SettingsViewModel.PREVIEW_EN}", "es:${SettingsViewModel.PREVIEW_ES}"), log)
    }

    @Test
    fun `con Orpheus y sin correcciones habladas solo habla Orpheus`() = runTest(dispatcher) {
        val (vm, repo) = createVm()
        repo.update { it.copy(voiceEngine = VoiceEngine.ORPHEUS, speakCorrections = false) }
        advanceUntilIdle()
        vm.preview()
        advanceUntilIdle()
        assertEquals(listOf("orpheus:${SettingsViewModel.PREVIEW_EN}"), log)
    }

    @Test
    fun `si la voz falla se muestra el error`() = runTest(dispatcher) {
        val (vm, repo) = createVm(orpheus = FakeVoice(error = IllegalStateException("401")))
        repo.update { it.copy(voiceEngine = VoiceEngine.ORPHEUS) }
        advanceUntilIdle()
        vm.preview()
        advanceUntilIdle()
        assertTrue(vm.previewError.value!!.contains("401"))
    }

    @Test
    fun `update guarda en DataStore`() = runTest(dispatcher) {
        val (vm, repo) = createVm()
        vm.update { it.copy(silenceMs = 2000) }
        advanceUntilIdle()
        assertEquals(2000, repo.settings.first().silenceMs)
        assertEquals(TutorSettings().llmModel, repo.settings.first().llmModel)
    }
}
