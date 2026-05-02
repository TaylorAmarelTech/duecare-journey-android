package com.duecare.journey.ui.intake

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * The guided intake wizard. Top-level full-screen UI launched from a
 * "Quick guided intake" button on the Journal tab.
 *
 * One question at a time. Worker types an answer (or taps Skip), submits,
 * and the next question appears. Each non-empty answer becomes a real
 * journal entry that flows through the same auto-tag pipeline as a
 * manual entry.
 *
 * After all 10 questions, the worker sees a completion summary with the
 * count of entries created and a CTA to view the Reports tab.
 */
@Composable
fun IntakeScreen(
    onClose: () -> Unit,
    vm: IntakeViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header(s, onClose)
        if (s.isComplete) {
            CompletionCard(s, onClose, onRestart = { vm.restart() })
        } else {
            s.currentQuestion?.let { q ->
                QuestionCard(
                    q = q,
                    answer = s.currentAnswer,
                    onAnswerChange = { vm.updateAnswer(it) },
                    onSubmit = { vm.submitAndAdvance() },
                    onSkip = { vm.skipCurrent() },
                )
            }
        }
    }
}

@Composable
private fun Header(s: IntakeUiState, onClose: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Quick guided intake",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Text(
                    if (s.isComplete) "Done — ${s.entriesCreated} entries created"
                    else "Question ${s.questionIndex + 1} of ${s.totalQuestions} · " +
                        "${s.entriesCreated} entries created so far",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onClose) { Text("Close") }
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = {
                if (s.totalQuestions == 0) 0f
                else s.questionIndex.coerceAtMost(s.totalQuestions).toFloat() / s.totalQuestions
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun QuestionCard(
    q: com.duecare.journey.intel.IntakeWizard.Question,
    answer: String,
    onAnswerChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSkip: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(4.dp),
            ) {
                Text(q.category,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(8.dp))
            Text(q.prompt,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(q.helperText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = answer,
                onValueChange = onAnswerChange,
                label = { Text("Your answer") },
                placeholder = { Text(q.placeholder) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3, maxLines = 6,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSubmit,
                    enabled = answer.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { Text("Save and next") }
                OutlinedButton(onClick = onSkip) { Text("Skip") }
            }
        }
    }
}

@Composable
private fun CompletionCard(s: IntakeUiState, onClose: () -> Unit, onRestart: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Intake complete", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(6.dp))
            Text(
                "Created ${s.entriesCreated} journal entries from your answers. " +
                    "Open the Reports tab to see the ILO indicator coverage, fee " +
                    "table, and a generated NGO intake document.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onClose, modifier = Modifier.weight(1f)) {
                    Text("Done")
                }
                OutlinedButton(onClick = onRestart) { Text("Restart") }
            }
        }
    }
}
