package com.duecare.journey.ui.reports

import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AssignmentLate
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.duecare.journey.intel.DomainKnowledge
import com.duecare.journey.intel.DomainKnowledge.IloForcedLabourIndicators
import com.duecare.journey.intel.RiskAnalyzer
import com.duecare.journey.journal.JourneyStage
import com.duecare.journey.journal.RefundClaim
import com.duecare.journey.journal.RefundStatus
import com.duecare.journey.ui.fees.AddFeeDialog

/**
 * Reports tab — surfaces structured analysis of the worker's journal:
 *
 *   - Top-line counts (entries, fee lines, risk flags)
 *   - ILO forced-labour indicator coverage
 *   - Detailed risk findings with statute + next-step
 *   - Fee table with legal/illegal flags + recovery total
 *   - "Generate report" → markdown + share action for an NGO
 */
@Composable
fun ReportsScreen(
    padding: PaddingValues,
    vm: ReportsViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsState()
    val report by vm.generatedReport.collectAsState()
    val context = LocalContext.current
    var showAddFee by remember { mutableStateOf(false) }
    var openClaimSnackbar by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { TopSummary(s) }
        item { IloHistogramCard(s) }
        if (s.risks.isNotEmpty()) {
            item { SectionHeader("Detailed risk findings (${s.risks.size})") }
            items(s.risks, key = { it.ruleId + it.sourceEntryId }) { r ->
                RiskCard(r)
            }
        }
        item { SectionHeader("Fees paid (${s.feeReport.lines.size})") }
        item {
            AddFeeButtonCard(onTap = { showAddFee = true })
        }
        if (s.feeReport.lines.isNotEmpty()) {
            item {
                FeeReportCard(
                    s = s,
                    onStartRefund = { paymentId ->
                        vm.startRefundClaim(paymentId) { id ->
                            openClaimSnackbar = id?.let { "Refund-claim draft created" }
                                ?: "No payment found for that line"
                        }
                    },
                )
            }
        }
        if (s.refundClaims.isNotEmpty()) {
            item { SectionHeader("Refund claims (${s.refundClaims.size})") }
            items(s.refundClaims, key = { it.id }) { claim ->
                RefundClaimCard(
                    claim = claim,
                    onMarkFiled = { vm.markClaimFiled(claim.id, null) },
                    onWithdraw = { vm.withdrawClaim(claim.id) },
                    onShare = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT,
                                "Refund claim — ${claim.currency} ${"%.2f".format(claim.amountClaimedMinorUnits / 100.0)}")
                            putExtra(Intent.EXTRA_TEXT,
                                claim.coverLetterText + "\n\n---\n" +
                                    claim.draftDeliveryMessage)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share refund claim"))
                    },
                )
            }
        }
        item { SectionHeader("Generate NGO intake document") }
        item {
            GenerateReportCard(
                onGenerate = { vm.generateMarkdownReport() },
                hasReport = report != null,
                onView = { /* dialog opens via [report != null] below */ },
            )
        }
        item { Spacer(Modifier.height(60.dp)) }
    }

    if (showAddFee) {
        AddFeeDialog(
            currentStage = s.stage,
            onDismiss = { showAddFee = false },
            onSaved = { result ->
                showAddFee = false
                openClaimSnackbar = if (result.assessment != null)
                    "Fee saved + flagged ILLEGAL — see Refund claims below"
                else "Fee saved"
            },
        )
    }
    openClaimSnackbar?.let { msg ->
        AlertDialog(
            onDismissRequest = { openClaimSnackbar = null },
            confirmButton = {
                TextButton(onClick = { openClaimSnackbar = null }) { Text("OK") }
            },
            text = { Text(msg) },
        )
    }

    report?.let { rep ->
        AlertDialog(
            onDismissRequest = { vm.clearGenerated() },
            confirmButton = {
                Button(onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/markdown"
                        putExtra(Intent.EXTRA_SUBJECT, "Migrant worker case intake")
                        putExtra(Intent.EXTRA_TEXT, rep.markdown)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share report"))
                }) {
                    Icon(Icons.Outlined.Share, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Share")
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.clearGenerated() }) { Text("Close") }
            },
            title = { Text("NGO intake document") },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        Text(
                            text = rep.markdown,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun TopSummary(s: ReportsUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "Case overview",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                StatCell(s.entryCount.toString(), "entries",
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                StatCell(s.feeReport.lines.size.toString(), "fee lines",
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                StatCell(s.timeline.totalRisks.toString(), "risk flags",
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                StatCell(s.timeline.criticalRisks.toString(), "critical",
                    color = if (s.timeline.criticalRisks > 0) Color(0xFFEF4444)
                    else MaterialTheme.colorScheme.onPrimaryContainer)
            }
            if (s.corridorCode != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Corridor: ${s.corridorCode}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun StatCell(value: String, label: String, color: Color) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun IloHistogramCard(s: ReportsUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "ILO C029 forced-labour indicators",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            if (s.iloHistogram.isEmpty()) {
                Text(
                    "No ILO indicators have fired yet against your journal. " +
                        "If your situation matches one of the 11 indicators, " +
                        "the next entry that mentions it will surface here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                IloForcedLabourIndicators.ALL.forEach { ind ->
                    val count = s.iloHistogram[ind.number] ?: 0
                    if (count > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.size(32.dp, 24.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        count.toString(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                            Spacer(Modifier.size(8.dp))
                            Text(
                                "${ind.number}. ${ind.name}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RiskCard(r: RiskAnalyzer.Risk) {
    val sevColor = when (r.severity) {
        DomainKnowledge.GrepRules.Severity.CRITICAL -> Color(0xFFEF4444)
        DomainKnowledge.GrepRules.Severity.HIGH -> Color(0xFFF59E0B)
        DomainKnowledge.GrepRules.Severity.MEDIUM -> Color(0xFF3B82F6)
        DomainKnowledge.GrepRules.Severity.LOW -> Color(0xFF6B7280)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, sevColor),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PriorityHigh, contentDescription = null,
                    tint = sevColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(r.displayName, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.size(8.dp))
                Surface(color = sevColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)) {
                    Text(r.severity.name, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall, color = sevColor)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("From entry: \"${r.sourceEntryTitle}\"",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (r.matchedSnippet.isNotBlank()) {
                Text("\"${r.matchedSnippet}\"",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 2.dp))
            }
            Spacer(Modifier.height(6.dp))
            HorizontalDivider()
            Spacer(Modifier.height(6.dp))
            Text("ILO indicator #${r.iloIndicator}",
                style = MaterialTheme.typography.labelSmall, color = sevColor)
            Text("Statute: ${r.statuteCitation}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(r.whatItMeans, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text("Recommended next step:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold)
            Text(r.nextStep, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AddFeeButtonCard(onTap: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Record a fee payment",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                Text(
                    "Captures recipient + amount + purpose. Auto-runs " +
                        "the legality check against your corridor's fee " +
                        "cap and pre-stages a refund claim if recoverable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(8.dp))
            Button(onClick = onTap) {
                Icon(Icons.Outlined.Add, contentDescription = null,
                    modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(4.dp))
                Text("Add fee")
            }
        }
    }
}

@Composable
private fun RefundClaimCard(
    claim: RefundClaim,
    onMarkFiled: () -> Unit,
    onWithdraw: () -> Unit,
    onShare: () -> Unit,
) {
    val statusColor = when (claim.status) {
        RefundStatus.DRAFT -> Color(0xFFF59E0B)
        RefundStatus.FILED, RefundStatus.IN_REVIEW -> Color(0xFF3B82F6)
        RefundStatus.GRANTED -> Color(0xFF10B981)
        RefundStatus.PARTIALLY_RECOVERED -> Color(0xFF10B981)
        RefundStatus.DENIED, RefundStatus.WITHDRAWN -> Color(0xFF6B7280)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, statusColor.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Gavel, contentDescription = null,
                    tint = statusColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${claim.currency} ${"%,.2f".format(claim.amountClaimedMinorUnits / 100.0)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Status: ${claim.status.name.lowercase().replace('_', ' ')}",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                claim.coverLetterText.take(280) + if (claim.coverLetterText.length > 280) "…" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onShare, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Share, contentDescription = null,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Share")
                }
                if (claim.status == RefundStatus.DRAFT) {
                    OutlinedButton(onClick = onMarkFiled) { Text("Mark filed") }
                    OutlinedButton(onClick = onWithdraw) { Text("Withdraw") }
                }
            }
        }
    }
}

@Composable
private fun FeeReportCard(s: ReportsUiState, onStartRefund: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            if (s.feeReport.totalsByCurrency.isNotEmpty()) {
                Text("Totals by currency",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold)
                s.feeReport.totalsByCurrency.forEach {
                    Text("${it.currency}: ${it.displayTotal}",
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (s.feeReport.illegalTotalsByCurrency.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            "Likely recoverable (illegal fees)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        s.feeReport.illegalTotalsByCurrency.forEach {
                            Text("${it.currency}: ${it.displayTotal}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("All fee lines",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold)
            s.feeReport.lines.take(20).forEach { line ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Row {
                        if (line.isProbablyIllegal) {
                            Icon(Icons.Outlined.AssignmentLate, contentDescription = "illegal",
                                tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${line.recipientName} — ${line.purposeLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                "${line.displayAmount}" +
                                    (line.illegalityReason?.let { "  ·  $it" } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (line.isProbablyIllegal) Color(0xFFEF4444)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (line.isProbablyIllegal && line.feePaymentId != null) {
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { onStartRefund(line.feePaymentId!!) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.Gavel, contentDescription = null,
                                modifier = Modifier.size(14.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Start refund claim")
                        }
                    }
                }
            }
            if (s.feeReport.lines.size > 20) {
                Text(
                    "+ ${s.feeReport.lines.size - 20} more (visible in generated report)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GenerateReportCard(
    onGenerate: () -> Unit,
    hasReport: Boolean,
    onView: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "Build a structured intake document an NGO can act on. " +
                    "The document includes: corridor + stage, ILO indicator " +
                    "coverage, detailed risk findings with statute citations, " +
                    "fee table with legality flags, full chronological timeline, " +
                    "and recommended NGO + regulator contacts. Markdown — " +
                    "shareable to any messenger, email, or NGO case-management " +
                    "system.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onGenerate, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Description, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Generate intake document")
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}
