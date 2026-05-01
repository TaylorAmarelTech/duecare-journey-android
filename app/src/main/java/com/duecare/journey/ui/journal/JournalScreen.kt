package com.duecare.journey.ui.journal

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.sp
import com.duecare.journey.journal.AssessmentVerdict
import com.duecare.journey.journal.EntryKind
import com.duecare.journey.journal.FeePayment
import com.duecare.journey.journal.JournalEntry
import com.duecare.journey.journal.JourneyStage
import com.duecare.journey.journal.LegalAssessment
import com.duecare.journey.journal.Party
import com.duecare.journey.journal.sample.SampleData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

/**
 * The flagship Journal screen for v0.2.0.
 *
 * Shows two real entries (recruiter message + fee payment) on the
 * PH→HK domestic-worker corridor, demonstrating the harm-reduction
 * north star: the fee-payment row is flagged as ILLEGAL but
 * remains recorded faithfully, with a "Start refund claim" CTA the
 * worker chooses to act on or not.
 *
 * Sample data is populated via [SampleData] until v1 MVP wires up
 * the real journal-entry creation UI. The plumbing (JournalEntry +
 * FeePayment + LegalAssessment + Party + JournalRepository) is
 * already real — this screen reads the same shapes the real DB
 * will use.
 */
@Composable
fun JournalScreen(padding: PaddingValues) {
    var detailEntry by remember { mutableStateOf<DetailContent?>(null) }
    val entries = remember { SampleData.sampleJournalEntries }
    val partiesById = remember {
        SampleData.sampleParties.associateBy { it.id }
    }
    val payment = remember { SampleData.trainingFeePayment }
    val assessment = remember { SampleData.trainingFeeAssessment }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        StageHeader(JourneyStage.PRE_DEPARTURE, corridor = "PH-HK")
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(entries, key = { it.id }) { entry ->
                JournalEntryCard(
                    entry = entry,
                    partiesById = partiesById,
                    feePayment = if (entry.kind == EntryKind.EXPENSE) payment else null,
                    assessment = if (entry.kind == EntryKind.EXPENSE) assessment else null,
                    onClick = {
                        detailEntry = DetailContent(
                            entry = entry,
                            payment = if (entry.kind == EntryKind.EXPENSE) payment else null,
                            assessment = if (entry.kind == EntryKind.EXPENSE) assessment else null,
                            party = partiesById[entry.parties.firstOrNull()],
                        )
                    },
                )
            }
            item {
                Spacer(Modifier.height(40.dp))
                Text(
                    text = "v0.2.0 — sample data. Real journal entry " +
                        "creation, photo capture, and chat-driven event " +
                        "recording land in the v1 MVP build (week of " +
                        "2026-05-19).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    detailEntry?.let { content ->
        EntryDetailDialog(content, onDismiss = { detailEntry = null })
    }
}

@Composable
private fun StageHeader(stage: JourneyStage, corridor: String) {
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
                    text = "Corridor: $corridor",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun JournalEntryCard(
    entry: JournalEntry,
    partiesById: Map<String, Party>,
    feePayment: FeePayment?,
    assessment: LegalAssessment?,
    onClick: () -> Unit,
) {
    val borderColor = when (assessment?.workerVerdict) {
        AssessmentVerdict.ILLEGAL -> Color(0xFFEF4444)
        AssessmentVerdict.GREY -> Color(0xFFF59E0B)
        AssessmentVerdict.LEGAL -> Color(0xFF10B981)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
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
            entry.parties.firstOrNull()?.let { partyId ->
                partiesById[partyId]?.let { party ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = " ${party.name}" +
                            (party.licenseNumber?.let { "  (License: $it)" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (feePayment != null && assessment != null) {
                Spacer(Modifier.height(10.dp))
                AssessmentBanner(assessment, payment = feePayment)
            }
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
private fun AssessmentBanner(assessment: LegalAssessment, payment: FeePayment) {
    val (bannerColor: Color, bannerLabel: String) = when (assessment.workerVerdict) {
        AssessmentVerdict.ILLEGAL -> Color(0xFFEF4444) to "Flagged ILLEGAL by harness"
        AssessmentVerdict.GREY -> Color(0xFFF59E0B) to "Legally GREY — review"
        AssessmentVerdict.LEGAL -> Color(0xFF10B981) to "Assessed LEGAL"
        AssessmentVerdict.UNVERIFIED -> Color(0xFF6B7280) to "Not yet assessed"
    }
    Surface(
        color = bannerColor.copy(alpha = 0.10f),
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
                tint = bannerColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = bannerLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = bannerColor,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${assessment.controllingStatute} — tap to review",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EntryDetailDialog(content: DetailContent, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            if (content.payment != null && content.assessment?.workerVerdict == AssessmentVerdict.ILLEGAL) {
                Button(
                    onClick = { /* v1: launch RefundClaim flow */ onDismiss() },
                ) { Text("Start refund claim") }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            if (content.payment != null && content.assessment?.workerVerdict == AssessmentVerdict.ILLEGAL) {
                OutlinedButton(onClick = onDismiss) { Text("Not now") }
            }
        },
        title = {
            Text(
                content.entry.title,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column {
                Text(
                    DateFmt.format(Date(content.entry.timestampMillis)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Text(content.entry.body, style = MaterialTheme.typography.bodyMedium)
                content.party?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Party: ${it.name}" +
                            (it.licenseNumber?.let { ln -> "\nLicense: $ln (${it.licenseStatus})" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (content.payment != null && content.assessment != null) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Legal assessment",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Verdict: ${content.assessment.workerVerdict.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFEF4444),
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "Statute: ${content.assessment.controllingStatute}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Convention: ${content.assessment.controllingConvention}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        content.assessment.harnessReasoning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Source: ${content.assessment.source.name} — " +
                            "you can override this in the v1 MVP if you " +
                            "disagree (the harness verdict stays in the audit log).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

private data class DetailContent(
    val entry: JournalEntry,
    val payment: FeePayment?,
    val assessment: LegalAssessment?,
    val party: Party?,
)

private fun JourneyStage.label(): String = when (this) {
    JourneyStage.PRE_DEPARTURE -> "Pre-departure"
    JourneyStage.IN_TRANSIT -> "In transit"
    JourneyStage.ARRIVED -> "Arrived"
    JourneyStage.EMPLOYED -> "Employed"
    JourneyStage.EXIT -> "Exit"
}
