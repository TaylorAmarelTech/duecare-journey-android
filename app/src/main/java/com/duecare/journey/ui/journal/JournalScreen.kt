package com.duecare.journey.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.duecare.journey.journal.AssessmentVerdict
import com.duecare.journey.journal.EntryKind
import com.duecare.journey.journal.JournalEntry
import com.duecare.journey.journal.JourneyStage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

@Composable
fun JournalScreen(
    padding: PaddingValues,
    vm: JournalViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    var detailEntry by remember { mutableStateOf<JournalEntry?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showIntake by remember { mutableStateOf(false) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    if (showIntake) {
        com.duecare.journey.ui.intake.IntakeScreen(onClose = { showIntake = false })
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            StageHeader(state.stage, state.corridor)
            Spacer(Modifier.height(12.dp))
            GuidedIntakeCta(
                hasEntries = state.entries.isNotEmpty(),
                onTap = { showIntake = true },
            )
            Spacer(Modifier.height(12.dp))
            if (state.entries.isEmpty()) {
                Box(modifier = Modifier
                    .fillMaxSize()
                    .padding(40.dp),
                    contentAlignment = Alignment.Center) {
                    Text(
                        "No entries yet — tap the wand above for a guided " +
                            "intake, or tap + to add a free-form entry. " +
                            "Photos of receipts, recruiter messages, " +
                            "anything you want to remember and later prove.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.entries, key = { it.id }) { entry ->
                        JournalEntryCard(entry = entry, onClick = { detailEntry = entry })
                    }
                    item { Spacer(Modifier.height(80.dp)) }   // FAB clearance
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            text = { Text("Add entry") },
        )
    }

    detailEntry?.let { entry ->
        EntryDetailDialog(
            entry,
            onDismiss = { detailEntry = null },
            onDelete = {
                pendingDeleteId = entry.id
                detailEntry = null
            },
        )
    }
    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            confirmButton = {
                Button(
                    onClick = {
                        vm.deleteEntry(id)
                        pendingDeleteId = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingDeleteId = null }) {
                    Text("Cancel")
                }
            },
            title = { Text("Delete this entry?") },
            text = {
                Text(
                    "This will remove the entry from your journal. You " +
                        "can't undo this. If the entry was evidence for " +
                        "a future complaint, consider exporting it first.",
                )
            },
        )
    }
    if (showAddDialog) {
        AddEntryDialog(
            currentStage = state.stage,
            onDismiss = { showAddDialog = false },
            onSave = { stage, kind, title, body ->
                vm.addEntry(stage, kind, title, body)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun GuidedIntakeCta(hasEntries: Boolean, onTap: () -> Unit) {
    Surface(
        onClick = onTap,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    if (hasEntries) "Add more via guided intake"
                    else "Quick guided intake",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    "10 questions · creates entries automatically · skip anything you don't know",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

@Composable
private fun StageHeader(stage: JourneyStage, corridor: String?) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Stage: ${stage.label()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "Corridor: ${corridor ?: "(not set)"}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun JournalEntryCard(entry: JournalEntry, onClick: () -> Unit) {
    val borderColor = if (entry.taggedConcerns.isNotEmpty()) Color(0xFFEF4444)
    else MaterialTheme.colorScheme.outlineVariant
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                EntryKindIcon(entry.kind)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = DateFmt.format(Date(entry.timestampMillis)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = entry.body,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.taggedConcerns.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                ConcernsBanner(entry.taggedConcerns)
            }
        }
    }
}

@Composable
private fun ConcernsBanner(concerns: List<String>) {
    Surface(
        color = Color(0xFFEF4444).copy(alpha = 0.10f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.PriorityHigh,
                contentDescription = null,
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Flagged: ${concerns.joinToString(", ")}",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFEF4444),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun EntryKindIcon(kind: EntryKind) {
    val (icon: ImageVector, tint: Color) = when (kind) {
        EntryKind.MESSAGE  -> Icons.Outlined.Forum to Color(0xFF3B82F6)
        EntryKind.EXPENSE  -> Icons.Outlined.AttachMoney to Color(0xFFEF4444)
        EntryKind.PHOTO    -> Icons.Outlined.Image to Color(0xFF10B981)
        EntryKind.DOCUMENT -> Icons.Outlined.Description to Color(0xFF6366F1)
        EntryKind.NOTE     -> Icons.Outlined.Notes to Color(0xFF6B7280)
        EntryKind.INCIDENT -> Icons.Outlined.Warning to Color(0xFFF59E0B)
    }
    Surface(
        color = tint.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.size(36.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = kind.name, tint = tint)
        }
    }
}

@Composable
private fun EntryDetailDialog(
    entry: JournalEntry,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            TextButton(
                onClick = onDelete,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) { Text("Delete") }
        },
        title = { Text(entry.title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Text(
                    DateFmt.format(Date(entry.timestampMillis)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Stage: ${entry.stage.label()}", style = MaterialTheme.typography.labelSmall)
                Text("Kind: ${entry.kind.name}", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(10.dp))
                Text(entry.body, style = MaterialTheme.typography.bodyMedium)
                if (entry.taggedConcerns.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Concerns: ${entry.taggedConcerns.joinToString(", ")}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFEF4444),
                    )
                }
                if (entry.grepHits.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "GREP hits: ${entry.grepHits.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

@Composable
private fun AddEntryDialog(
    currentStage: JourneyStage,
    onDismiss: () -> Unit,
    onSave: (JourneyStage, EntryKind, String, String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(EntryKind.NOTE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = { onSave(currentStage, kind, title, body) },
                enabled = title.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Add a journal entry") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Details") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                )
                Spacer(Modifier.height(8.dp))
                Text("Type", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                EntryKindChips(kind, onPick = { kind = it })
            }
        },
    )
}

private fun JourneyStage.label(): String = when (this) {
    JourneyStage.PRE_DEPARTURE -> "Pre-departure"
    JourneyStage.IN_TRANSIT -> "In transit"
    JourneyStage.ARRIVED -> "Arrived"
    JourneyStage.EMPLOYED -> "Employed"
    JourneyStage.BETWEEN_EMPLOYERS -> "In country, no longer employed"
    JourneyStage.EXIT -> "Exit"
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun EntryKindChips(picked: EntryKind, onPick: (EntryKind) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        EntryKind.entries.forEach { k ->
            androidx.compose.material3.FilterChip(
                selected = picked == k,
                onClick = { onPick(k) },
                label = { Text(k.name.lowercase()) },
            )
        }
    }
}
