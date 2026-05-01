package com.duecare.journey.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.duecare.journey.journal.JourneyStage

/**
 * First-launch onboarding. Two questions:
 *   1. What stage of the OFW process are you in?
 *   2. What corridor (origin -> destination)?
 *
 * Layout fix from v0.3 -> v0.3.1: the corridor list has 10 entries
 * and previously pushed the action button off-screen on small phones,
 * making the screen feel broken. Now the content area is a real
 * LazyColumn that scrolls, with a separate fixed footer holding the
 * Next / Get-started / Skip buttons that's ALWAYS visible.
 *
 * Both answers are saved to DataStore and used to tailor every
 * subsequent chat prompt (the persona prepended for the worker
 * names their current stage + corridor explicitly so Gemma's
 * advice is journey-aware).
 *
 * Privacy: all selections stored ON THE DEVICE only.
 */
@Composable
fun OnboardingScreen(
    onComplete: (JourneyStage, String?) -> Unit,
) {
    var step by remember { mutableStateOf(0) }
    var pickedStage by remember { mutableStateOf<JourneyStage?>(null) }
    var pickedCorridor by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // --- Scrollable content area (takes the available space) ---
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = 20.dp, bottom = 12.dp,
                ),
            ) {
                item {
                    Text(
                        "Welcome to Duecare Journey",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Two quick questions so the advice is yours, not " +
                            "generic. All your answers stay on this phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                }

                if (step == 0) {
                    item {
                        Text(
                            "Where are you in your OFW journey?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                    items(STAGES) { opt ->
                        StageCard(
                            opt = opt,
                            isPicked = pickedStage == opt.stage,
                            onPick = { pickedStage = opt.stage },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                } else {
                    item {
                        Text(
                            "Which corridor?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Origin → destination. Helps the chat give you " +
                                "corridor-specific fee caps and the right " +
                                "NGO/regulator hotline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                    items(CORRIDORS) { (code, label) ->
                        CorridorCard(
                            code = code,
                            label = label,
                            isPicked = pickedCorridor == code,
                            onPick = { pickedCorridor = code },
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            // --- Sticky footer (always visible, never scrolled off) ---
            HorizontalDivider()
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Step ${step + 1} of 2",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            if (step == 0) {
                                if (pickedStage != null) step = 1
                            } else {
                                onComplete(pickedStage!!, pickedCorridor)
                            }
                        },
                        enabled = (step == 0 && pickedStage != null) ||
                            (step == 1 && pickedCorridor != null),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (step == 0) "Next" else "Get started")
                    }
                    if (step == 1) {
                        OutlinedButton(
                            onClick = { onComplete(pickedStage!!, null) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Skip — I'd rather not say my corridor")
                        }
                    }
                }
            }
        }
    }
}

private data class StageOption(
    val stage: JourneyStage,
    val title: String,
    val help: String,
)

private val STAGES = listOf(
    StageOption(JourneyStage.PRE_DEPARTURE,
        "Pre-departure", "Recruitment, contract, fees, training, before I leave home."),
    StageOption(JourneyStage.IN_TRANSIT,
        "In transit", "Departure, layovers, handoffs between agents."),
    StageOption(JourneyStage.ARRIVED,
        "Just arrived", "Onboarding at destination, document handling."),
    StageOption(JourneyStage.EMPLOYED,
        "Employed", "I'm working — wages, conditions, employer issues."),
    StageOption(JourneyStage.BETWEEN_EMPLOYERS,
        "In country, no longer employed",
        "Contract ended, fired, or quit. Still in the destination country. " +
            "Looking for next job, in NGO/embassy shelter, awaiting paperwork, " +
            "or trying to figure out what's next."),
    StageOption(JourneyStage.EXIT,
        "Contract end / exit",
        "End of contract, complaints, repatriation, on the way home."),
)

private val CORRIDORS = listOf(
    "PH-HK" to "Philippines → Hong Kong (domestic)",
    "PH-SA" to "Philippines → Saudi Arabia",
    "ID-HK" to "Indonesia → Hong Kong (domestic)",
    "ID-SA" to "Indonesia → Saudi Arabia",
    "NP-QA" to "Nepal → Qatar",
    "NP-MY" to "Nepal → Malaysia",
    "BD-SA" to "Bangladesh → Saudi Arabia",
    "BD-QA" to "Bangladesh → Qatar",
    "PH-SG" to "Philippines → Singapore (domestic)",
    "PH-AE" to "Philippines → UAE",
)

@Composable
private fun StageCard(
    opt: StageOption,
    isPicked: Boolean,
    onPick: () -> Unit,
) {
    Card(
        onClick = onPick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPicked) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
        border = if (isPicked) androidx.compose.foundation.BorderStroke(
            2.dp, MaterialTheme.colorScheme.primary,
        ) else androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                opt.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                opt.help,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CorridorCard(
    code: String,
    label: String,
    isPicked: Boolean,
    onPick: () -> Unit,
) {
    Card(
        onClick = onPick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPicked) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
        border = if (isPicked) androidx.compose.foundation.BorderStroke(
            2.dp, MaterialTheme.colorScheme.primary,
        ) else androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                code,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
