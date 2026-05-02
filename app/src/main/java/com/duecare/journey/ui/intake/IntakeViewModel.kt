package com.duecare.journey.ui.intake

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duecare.journey.intel.IntakeWizard
import com.duecare.journey.journal.JournalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the guided intake wizard. Walks worker through
 * [IntakeWizard.ALL] one question at a time. Each answered question
 * becomes a real JournalEntry (auto-tagged via JournalRepository.add).
 *
 * Skip is allowed on every question — the wizard never blocks; the
 * worker can fill in 1 question or all 10.
 *
 * State machine: index ∈ [0, ALL.size]. When index == ALL.size, the
 * wizard is complete; the calling screen surfaces a "Done — see your
 * Reports tab" CTA.
 */
@HiltViewModel
class IntakeViewModel @Inject constructor(
    private val journal: JournalRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        IntakeUiState(
            questionIndex = 0,
            totalQuestions = IntakeWizard.ALL.size,
            currentQuestion = IntakeWizard.ALL.firstOrNull(),
            currentAnswer = "",
            entriesCreated = 0,
            isComplete = false,
        )
    )
    val state: StateFlow<IntakeUiState> = _state.asStateFlow()

    fun updateAnswer(text: String) {
        _state.value = _state.value.copy(currentAnswer = text)
    }

    fun submitAndAdvance() {
        val cur = _state.value
        val q = cur.currentQuestion ?: return
        val draft = IntakeWizard.toDraft(q, cur.currentAnswer)
        viewModelScope.launch {
            if (draft != null) {
                journal.add(
                    stage = draft.stage,
                    kind = draft.kind,
                    title = draft.title,
                    body = draft.body,
                )
                _state.value = cur.copy(entriesCreated = cur.entriesCreated + 1)
            }
            advance()
        }
    }

    fun skipCurrent() {
        advance()
    }

    fun restart() {
        _state.value = IntakeUiState(
            questionIndex = 0,
            totalQuestions = IntakeWizard.ALL.size,
            currentQuestion = IntakeWizard.ALL.firstOrNull(),
            currentAnswer = "",
            entriesCreated = 0,
            isComplete = false,
        )
    }

    private fun advance() {
        val next = _state.value.questionIndex + 1
        val q = IntakeWizard.ALL.getOrNull(next)
        _state.value = _state.value.copy(
            questionIndex = next,
            currentQuestion = q,
            currentAnswer = "",
            isComplete = q == null,
        )
    }
}

data class IntakeUiState(
    val questionIndex: Int,
    val totalQuestions: Int,
    val currentQuestion: IntakeWizard.Question?,
    val currentAnswer: String,
    val entriesCreated: Int,
    val isComplete: Boolean,
)
