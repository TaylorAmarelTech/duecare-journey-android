package com.duecare.journey.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duecare.journey.intel.FeeAggregator
import com.duecare.journey.intel.NgoReportBuilder
import com.duecare.journey.intel.RiskAnalyzer
import com.duecare.journey.intel.TimelineBuilder
import com.duecare.journey.journal.FeePaymentDao
import com.duecare.journey.journal.JournalRepository
import com.duecare.journey.journal.JourneyStage
import com.duecare.journey.journal.PartyDao
import com.duecare.journey.journal.RefundClaim
import com.duecare.journey.journal.RefundClaimDao
import com.duecare.journey.journal.RefundClaimRepository
import com.duecare.journey.onboarding.OnboardingPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the Reports tab. Combines journal entries + fee payments +
 * parties + onboarding context, then runs the report builders to
 * produce live timeline / fees / risks / NGO intake doc.
 *
 * Heavy work (markdown generation) runs once per state change, not on
 * every recomposition — the StateFlow caches.
 */
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val journal: JournalRepository,
    private val feeDao: FeePaymentDao,
    private val partyDao: PartyDao,
    private val claimDao: RefundClaimDao,
    private val claimRepo: RefundClaimRepository,
    private val onboarding: OnboardingPrefs,
    private val timelineBuilder: TimelineBuilder,
    private val feeAggregator: FeeAggregator,
    private val riskAnalyzer: RiskAnalyzer,
    private val reportBuilder: NgoReportBuilder,
) : ViewModel() {

    val state: StateFlow<ReportsUiState> = combine(
        journal.observeAll(),
        feeDao.observeAll(),
        partyDao.observeAll(),
        claimDao.observeAll(),
        combine(onboarding.stage, onboarding.corridor) { s, c -> s to c },
    ) { entries, fees, parties, claims, (stage, corridor) ->
        val tl = timelineBuilder.build(entries)
        val allRisks = riskAnalyzer.analyzeAll(entries)
        val histogram = riskAnalyzer.iloIndicatorHistogram(allRisks)
        val feeReport = feeAggregator.build(fees, parties, entries, corridor)
        ReportsUiState(
            timeline = tl,
            risks = allRisks,
            iloHistogram = histogram,
            feeReport = feeReport,
            refundClaims = claims,
            corridorCode = corridor,
            stage = stage,
            entryCount = entries.size,
            feePaymentCount = fees.size,
            partyCount = parties.size,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReportsUiState(),
    )

    /** Worker tapped "Start refund claim" on an illegal fee line.
     *  Returns the draft claim id (or existing if already drafted)
     *  via [onResult] for navigation. */
    fun startRefundClaim(paymentId: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val draft = claimRepo.draftFor(paymentId)
            onResult(draft?.id)
        }
    }

    fun markClaimFiled(claimId: String, caseNumber: String?) {
        viewModelScope.launch { claimRepo.markFiled(claimId, caseNumber) }
    }

    fun withdrawClaim(claimId: String) {
        viewModelScope.launch { claimRepo.withdraw(claimId) }
    }

    private val _generated = MutableStateFlow<NgoReportBuilder.Report?>(null)
    val generatedReport: StateFlow<NgoReportBuilder.Report?> = _generated.asStateFlow()

    fun generateMarkdownReport() {
        viewModelScope.launch {
            // Re-read everything fresh from DAOs at generate time —
            // the StateFlow we expose to the UI lags 5s on
            // unsubscribe (SharingStarted.WhileSubscribed(5_000)) so
            // a worker who taps Generate immediately after editing
            // an entry could see stale data otherwise. .first()
            // takes one snapshot from each Flow then completes — no
            // long-lived collector and no race with the UI's combine.
            val entries = journal.observeAll().first()
            val fees = feeDao.observeAll().first()
            val parties = partyDao.observeAll().first()
            val stage = onboarding.stage.first()
            val corridor = onboarding.corridor.first()
            _generated.value = reportBuilder.build(
                entries = entries,
                feePayments = fees,
                parties = parties,
                currentStage = stage,
                corridorCode = corridor,
            )
        }
    }

    fun clearGenerated() {
        _generated.value = null
    }
}

data class ReportsUiState(
    val timeline: TimelineBuilder.Timeline = TimelineBuilder.Timeline(
        events = emptyList(),
        byStage = emptyList(),
        totalEvents = 0,
        totalRisks = 0,
        criticalRisks = 0,
        firstEventMillis = null,
        lastEventMillis = null,
    ),
    val risks: List<RiskAnalyzer.Risk> = emptyList(),
    val iloHistogram: Map<Int, Int> = emptyMap(),
    val feeReport: FeeAggregator.FeeReport = FeeAggregator.FeeReport(
        lines = emptyList(),
        totalsByCurrency = emptyList(),
        totalsByRecipient = emptyMap(),
        illegalLines = emptyList(),
        illegalTotalsByCurrency = emptyList(),
    ),
    val refundClaims: List<RefundClaim> = emptyList(),
    val corridorCode: String? = null,
    val stage: JourneyStage = JourneyStage.PRE_DEPARTURE,
    val entryCount: Int = 0,
    val feePaymentCount: Int = 0,
    val partyCount: Int = 0,
)
