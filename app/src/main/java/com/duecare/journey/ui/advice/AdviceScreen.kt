package com.duecare.journey.ui.advice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.duecare.journey.advice.AdviceViewModel

/**
 * Working chat surface for the Advice tab.
 *
 * Sends a question to the [AdviceViewModel] which:
 *   1. Pulls recent journal entries from the SQLCipher DB.
 *   2. Builds a journey-aware prompt via PromptAssembler (persona +
 *      journal summary + GREP hits + RAG snippets + tool results +
 *      question).
 *   3. Streams Gemma's response back token-by-token.
 *
 * If the MediaPipe model is downloaded and the engine is loaded, it's
 * real Gemma. Otherwise the bound StubGemmaEngine returns canned text
 * so the UI flow is testable end-to-end without the 1.4 GB model.
 *
 * Worker can switch engines via Settings → Use real Gemma toggle.
 */
@Composable
fun AdviceScreen(padding: PaddingValues, vm: AdviceViewModel = hiltViewModel()) {
    val messages by vm.messages.collectAsState()
    var input by remember { mutableStateOf("") }
    var inFlight by remember { mutableStateOf(false) }
    var streamingText by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, streamingText?.length) {
        // Auto-scroll to the bottom on every new chunk
        val lastIndex = messages.size + (if (streamingText != null) 1 else 0) - 1
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        // Conversation
        if (messages.isEmpty() && streamingText == null) {
            Box(modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(24.dp),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Ask anything about your journey",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "I'll cite the specific statute, the controlling " +
                            "ILO convention, and the right NGO/regulator " +
                            "for your corridor. I won't tell you what to " +
                            "do — you choose.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    val suggestedSend: (String) -> Unit = { q ->
                        if (!inFlight) {
                            inFlight = true
                            streamingText = ""
                            input = ""
                            vm.askStreaming(
                                question = q,
                                onChunk = { streamingText = it },
                                onComplete = {
                                    streamingText = null
                                    inFlight = false
                                },
                            )
                        }
                    }
                    SuggestedPrompt("Is a ₱50,000 \"training fee\" legal?",
                        onTap = suggestedSend)
                    Spacer(Modifier.height(8.dp))
                    SuggestedPrompt("My recruiter is keeping my passport \"for safekeeping\". Is that allowed?",
                        onTap = suggestedSend)
                    Spacer(Modifier.height(8.dp))
                    SuggestedPrompt("My loan APR is 68% per year. Is this legal in Hong Kong?",
                        onTap = suggestedSend)
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(text = msg.text, isUser = msg.role == AdviceViewModel.ChatMessage.Role.USER)
                }
                if (streamingText != null) {
                    item("streaming") {
                        MessageBubble(text = streamingText!!, isUser = false, isStreaming = true)
                    }
                }
            }
        }

        // Input row
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask about a fee, a contract clause, anything...") },
                    enabled = !inFlight,
                    singleLine = false,
                    maxLines = 4,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
                Spacer(Modifier.padding(4.dp))
                IconButton(
                    onClick = {
                        val q = input.trim()
                        if (q.isNotEmpty() && !inFlight) {
                            inFlight = true
                            streamingText = ""
                            input = ""
                            vm.askStreaming(
                                question = q,
                                onChunk = { streamingText = it },
                                onComplete = {
                                    streamingText = null
                                    inFlight = false
                                },
                            )
                        }
                    },
                    enabled = input.isNotBlank() && !inFlight,
                ) {
                    if (inFlight) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(4.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Outlined.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestedPrompt(text: String, onTap: (String) -> Unit) {
    Surface(
        onClick = { onTap(text) },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.widthIn(max = 320.dp),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MessageBubble(text: String, isUser: Boolean, isStreaming: Boolean = false) {
    val align = if (isUser) Alignment.End else Alignment.Start
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = if (isUser) 16.dp else 4.dp,
                topEnd = if (isUser) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp,
            ),
            color = bubbleColor,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .heightIn(min = 36.dp),
        ) {
            Text(
                text = text + (if (isStreaming) "▌" else ""),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
            )
        }
    }
}
