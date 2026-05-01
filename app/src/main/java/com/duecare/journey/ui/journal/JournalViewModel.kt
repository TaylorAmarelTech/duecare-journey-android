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

    init {
        // Seed sample data once if the journal is empty AND
        // onboarding has been completed. New installs after panic-wipe
        // re-seed; that's intentional (worker can immediately delete
        // the sample entries).
        viewModelScope.launch {
            if (journal.recent(1).isEmpty()) {
                seedSampleData()
            }
        }
    }

    private suspend fun seedSampleData() {
        SampleData.sampleJournalEntries.forEach { e ->
            journal.add(
                stage = e.stage,
                kind = e.kind,
                title = e.title,
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
    ) {
        viewModelScope.launch {
            journal.add(stage = stage, kind = kind, title = title, body = body)
        }
    }
}

data class JournalUiState(
    val entries: List<JournalEntry> = emptyList(),
    val stage: JourneyStage = JourneyStage.PRE_DEPARTURE,
    val corridor: String? = null,
)
