package com.englishtutor.app.ui.vocab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.englishtutor.app.EnglishTutorApp
import com.englishtutor.app.data.TutorException
import com.englishtutor.app.data.TutorRepository
import com.englishtutor.app.data.TutorSettings
import com.englishtutor.app.data.local.FrequentError
import com.englishtutor.app.data.local.VocabEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estado del diálogo "Agregar palabra". */
data class AddWordState(val loading: Boolean = false, val error: String? = null, val lastAdded: String? = null)

class VocabViewModel(
    private val repository: TutorRepository,
    private val settings: Flow<TutorSettings>,
) : ViewModel() {
    private val all = repository.observeVocab()

    /** "Mis palabras": las que pediste guardar porque te cuestan. */
    val myWords: StateFlow<List<VocabEntity>> = all.map { list -> list.filter { it.isMine } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Palabras nuevas que te enseñó el tutor. */
    val tutorWords: StateFlow<List<VocabEntity>> = all.map { list -> list.filterNot { it.isMine } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val errors: StateFlow<List<FrequentError>> = repository.observeFrequentErrors()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _addState = MutableStateFlow(AddWordState())
    val addState: StateFlow<AddWordState> = _addState.asStateFlow()

    /** Agrega una palabra escrita a mano: el tutor (LLM) busca su significado y un ejemplo. */
    fun addWord(term: String) {
        if (term.isBlank() || _addState.value.loading) return
        viewModelScope.launch {
            _addState.value = AddWordState(loading = true)
            _addState.value = try {
                val word = repository.defineWord(term, settings.first().llmModel)
                repository.saveMyWord(word)
                AddWordState(lastAdded = word.term)
            } catch (e: TutorException) {
                AddWordState(error = e.message)
            }
        }
    }

    fun clearAddState() { _addState.value = AddWordState() }
    fun markReviewed(id: Long) = viewModelScope.launch { repository.markVocabReviewed(id) }
    fun setMine(id: Long, mine: Boolean) = viewModelScope.launch { repository.setMine(id, mine) }
    fun delete(id: Long) = viewModelScope.launch { repository.deleteVocab(id) }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EnglishTutorApp
                VocabViewModel(app.repository, app.settingsRepository.settings)
            }
        }
    }
}
