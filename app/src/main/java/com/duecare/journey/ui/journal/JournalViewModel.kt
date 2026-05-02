package com.duecare.journey.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duecare.journey.journal.EntryKind
import com.duecare.journey.journal.JournalEntry
import com.duecare.journey.journal.JournalRepository
import com.duecare.journey.journal.JourneyStage
import com.duecare.journey.journal.sample.SampleData
import com.duecare.journey.onboarding.OnboardingPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the Journal tab. Reads entries from the encrypted DB (real),
 * with a one-time bootstrap that seeds the sample data on first run
 * so judges + first-time users see a populated screen even before
 * they've added their own entries.
 *
 * Worker can add their own entries via the FAB → AddEntryScreen.
 */
@HiltViewModel
class JournalViewModel @Inject constructor(
    private val journal: JournalRepository,
    private val onboarding: OnboardingPrefs,
    private val attachments: com.duecare.journey.journal.AttachmentStorage,
) : ViewModel() {

    val state: StateFlow<JournalUiState> = combine(
        journal.observeAll(),
        onboarding.stage,
        onboarding.corridor,
    ) { entries, stage, corridor ->
        JournalUiState(
            entries = entries,
            stage = stage,
            corridor = corridor,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = JournalUiState(),
    )

    /** v0.6 fix: removed automatic sample-data seed on first launch.
     *  Earlier versions seeded SampleData.sampleJournalEntries when
     *  the journal was empty — new users saw two red-flagged entries
     *  they hadn't created, which was misleading. Now the journal
     *  starts empty; demo data is only loaded when the worker
     *  explicitly taps "Load demo entries" in Settings. */

    /** Public so Settings can call it on demand. */
    suspend fun loadDemoData() {
        SampleData.sampleJournalEntries.forEach { e ->
            journal.add(
                stage = e.stage,
                kind = e.kind,
                title = "[Example] ${e.title}",
                body = e.body,
                parties = e.parties,
                taggedConcerns = e.taggedConcerns,
            )
        }
    }

    fun addEntry(
        stage: JourneyStage,
        kind: EntryKind,
        title: String,
        body: String,
        attachmentUri: android.net.Uri? = null,
        contentResolver: android.content.ContentResolver? = null,
    ) {
        viewModelScope.launch {
            val attachmentPath = if (attachmentUri != null && contentResolver != null) {
                attachments.copyIn(attachmentUri, contentResolver)
            } else null
            journal.add(
                stage = stage,
                kind = kind,
                title = title,
                body = body,
                attachmentPath = attachmentPath,
            )
        }
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch {
            journal.delete(id)
        }
    }
}

data class JournalUiState(
    val entries: List<JournalEntry> = emptyList(),
    val stage: JourneyStage = JourneyStage.PRE_DEPARTURE,
    val corridor: String? = null,
)
