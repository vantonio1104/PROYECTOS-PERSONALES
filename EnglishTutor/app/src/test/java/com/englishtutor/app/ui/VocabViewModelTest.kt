package com.englishtutor.app.ui

import com.englishtutor.app.FakeGroqApi
import com.englishtutor.app.data.TutorRepository
import com.englishtutor.app.data.TutorSettings
import com.englishtutor.app.data.local.AppDatabase
import com.englishtutor.app.inMemoryDatabase
import com.englishtutor.app.ui.vocab.VocabViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class VocabViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private val api = FakeGroqApi()
    private lateinit var repository: TutorRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = inMemoryDatabase()
        repository = TutorRepository(api, db.tutorDao())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun `agregar una palabra a mano busca su significado y la guarda`() = runTest(dispatcher) {
        api.chatReplies += """{"term":"though","meaning":"aunque","example":"Nice, though."}"""
        val vm = VocabViewModel(repository, flowOf(TutorSettings(llmModel = "openai/gpt-oss-120b")))
        backgroundScope.launch { vm.myWords.collect {} } // la pantalla está "mirando"

        vm.addWord("though")
        advanceUntilIdle()

        assertEquals("though", vm.addState.value.lastAdded)
        assertNull(vm.addState.value.error)
        assertEquals("aunque", vm.myWords.value.single().meaning)
        assertEquals("openai/gpt-oss-120b", api.chatRequests.single().model)
    }

    @Test
    fun `si no encuentra el significado muestra el error y no guarda nada`() = runTest(dispatcher) {
        api.chatReplies += "sorry"
        val vm = VocabViewModel(repository, flowOf(TutorSettings()))
        vm.addWord("xyz")
        advanceUntilIdle()
        assertTrue(vm.addState.value.error!!.contains("significado"))
        assertTrue(repository.observeVocab().first().isEmpty())
    }

    @Test
    fun `separa mis palabras de las del tutor y la estrella las mueve`() = runTest(dispatcher) {
        repository.saveMyWord(com.englishtutor.app.data.Vocab("though", "aunque"))
        repository.saveTutorTurn(repository.startSession(), null, com.englishtutor.app.data.TutorReply("a", new_vocab = listOf(com.englishtutor.app.data.Vocab("gist", "idea"))))
        val vm = VocabViewModel(repository, flowOf(TutorSettings()))
        backgroundScope.launch { vm.myWords.collect {} }
        backgroundScope.launch { vm.tutorWords.collect {} }
        advanceUntilIdle()
        assertEquals(listOf("though"), vm.myWords.value.map { it.term })
        assertEquals(listOf("gist"), vm.tutorWords.value.map { it.term })

        vm.setMine(vm.tutorWords.value.single().id, true)
        advanceUntilIdle()
        assertEquals(2, vm.myWords.value.size)
        assertTrue(vm.tutorWords.value.isEmpty())
    }
}
