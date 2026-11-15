package com.englishtutor.app.ui.vocab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.englishtutor.app.EnglishTutorApp
import com.englishtutor.app.data.TutorRepository
import com.englishtutor.app.data.local.FrequentError
import com.englishtutor.app.data.local.VocabEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VocabViewModel(private val repository: TutorRepository) : ViewModel() {
    val vocab: StateFlow<List<VocabEntity>> = repository.observeVocab()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val errors: StateFlow<List<FrequentError>> = repository.observeFrequentErrors()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun markReviewed(id: Long) = viewModelScope.launch { repository.markVocabReviewed(id) }
    fun delete(id: Long) = viewModelScope.launch { repository.deleteVocab(id) }

    companion object {
        val Factory = viewModelFactory {
            initializer { VocabViewModel((this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EnglishTutorApp).repository) }
        }
    }
}
