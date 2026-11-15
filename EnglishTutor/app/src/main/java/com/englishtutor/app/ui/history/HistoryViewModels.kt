package com.englishtutor.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.englishtutor.app.EnglishTutorApp
import com.englishtutor.app.data.TutorRepository
import com.englishtutor.app.data.local.MessageWithCorrections
import com.englishtutor.app.data.local.SessionEntity
import com.englishtutor.app.data.local.SessionSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Lista de sesiones anteriores. */
class HistoryViewModel(repository: TutorRepository) : ViewModel() {
    // null = cargando
    val sessions: StateFlow<List<SessionSummary>?> = repository.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        val Factory = viewModelFactory {
            initializer { HistoryViewModel((this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EnglishTutorApp).repository) }
        }
    }
}

/** Mensajes y correcciones de una sesión. */
class SessionDetailViewModel(private val repository: TutorRepository, private val sessionId: Long) : ViewModel() {
    val session: StateFlow<SessionEntity?> = repository.observeSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val messages: StateFlow<List<MessageWithCorrections>> = repository.observeMessages(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            onDone()
        }
    }

    companion object {
        fun factory(sessionId: Long) = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EnglishTutorApp
                SessionDetailViewModel(app.repository, sessionId)
            }
        }
    }
}
