package com.duecare.journey.advice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duecare.journey.inference.GemmaInferenceEngine
import com.duecare.journey.journal.JournalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State holder for the Advice tab. Drives the chat surface: takes a
 * worker question, builds a journey-aware prompt via [PromptAssembler],
 * streams Gemma's response, and persists the exchange back into the
 * encrypted DB so the next prompt has the full context.
 */
@HiltViewModel
class AdviceViewModel @Inject constructor(
    private val inference: GemmaInferenceEngine,
    private val assembler: PromptAssembler,
    private val journal: JournalRepository,
) : ViewModel() {

    data class ChatMessage(
        val id: String,
        val role: Role,
        val text: String,
        val timestampMillis: Long,
    ) {
        enum class Role { USER, ASSISTANT }
    }

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    /**
     * Send a worker question. Returns a Flow of incrementally-growing
     * response text so the UI can render the streaming token-by-token.
     */
    fun ask(question: String): Flow<String> = flow {
        appendMessage(ChatMessage.Role.USER, question)
        val recent = journal.recent(limit = 10)
        val prompt = assembler.assemble(
            question = question,
            recentEntries = recent,
            currentStage = journal.currentStage(),
            corridor = journal.detectedCorridor(),
        )
        val sb = StringBuilder()
        inference.streamGenerate(prompt, maxNewTokens = 1024).collect { token ->
            sb.append(token)
            emit(sb.toString())
        }
        appendMessage(ChatMessage.Role.ASSISTANT, sb.toString())
    }

    private fun appendMessage(role: ChatMessage.Role, text: String) {
        viewModelScope.launch {
            _messages.value = _messages.value + ChatMessage(
                id = java.util.UUID.randomUUID().toString(),
                role = role,
                text = text,
                timestampMillis = System.currentTimeMillis(),
            )
        }
    }
}
