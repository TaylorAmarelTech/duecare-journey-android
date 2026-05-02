package com.duecare.journey.di

import android.content.Context
import com.duecare.journey.inference.CloudModelPrefs
import com.duecare.journey.inference.GemmaInferenceEngine
import com.duecare.journey.inference.MediaPipeGemmaEngine
import com.duecare.journey.inference.ModelManager
import com.duecare.journey.inference.OllamaGemmaEngine
import com.duecare.journey.inference.StubGemmaEngine
import com.duecare.journey.journal.FeePaymentDao
import com.duecare.journey.journal.JournalDao
import com.duecare.journey.journal.JournalDatabase
import com.duecare.journey.journal.LegalAssessmentDao
import com.duecare.journey.journal.PartyDao
import com.duecare.journey.journal.RefundClaimDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import javax.inject.Singleton

/**
 * Hilt module wiring up the four layers.
 *
 * v0.6 inference routing:
 *   1. Cloud (OllamaGemmaEngine) when the worker has saved a Cloud model
 *      URL in Settings — covers the case where on-device download
 *      doesn't work (HF 404, gated, no Wi-Fi, low storage).
 *   2. MediaPipe (on-device Gemma) when the model file is downloaded
 *      and loadable.
 *   3. Stub (canned legal-citation responses) — last-resort fallback so
 *      the chat UI is always functional during onboarding/demo.
 *
 * Each tier falls through to the next on Flow exception (network error,
 * load failure, etc.) so the UI never sees a thrown exception in normal
 * operation — the worst case is a stub response.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGemmaEngine(
        cloud: OllamaGemmaEngine,
        mediapipe: MediaPipeGemmaEngine,
        stub: StubGemmaEngine,
        modelManager: ModelManager,
        cloudPrefs: CloudModelPrefs,
    ): GemmaInferenceEngine = SmartGemmaEngine(
        cloud = cloud,
        primary = mediapipe,
        fallback = stub,
        modelManager = modelManager,
        cloudPrefs = cloudPrefs,
    )

    @Provides
    @Singleton
    fun provideJournalDatabase(@ApplicationContext ctx: Context): JournalDatabase =
        JournalDatabase.create(ctx)

    @Provides fun provideJournalDao(db: JournalDatabase): JournalDao = db.journalDao()
    @Provides fun providePartyDao(db: JournalDatabase): PartyDao = db.partyDao()
    @Provides fun provideFeePaymentDao(db: JournalDatabase): FeePaymentDao =
        db.feePaymentDao()
    @Provides fun provideLegalAssessmentDao(db: JournalDatabase): LegalAssessmentDao =
        db.legalAssessmentDao()
    @Provides fun provideRefundClaimDao(db: JournalDatabase): RefundClaimDao =
        db.refundClaimDao()
}

/**
 * v0.6 routing: Cloud → MediaPipe → Stub.
 *
 * `streamGenerate` uses Flow.catch + emitAll so a tier's mid-stream
 * failure transparently falls through to the next tier instead of
 * propagating an exception to the UI. `generate` (one-shot) uses
 * old-fashioned try/catch — same fallback semantics, simpler code.
 */
internal class SmartGemmaEngine(
    private val cloud: OllamaGemmaEngine,
    private val primary: MediaPipeGemmaEngine,
    private val fallback: StubGemmaEngine,
    private val modelManager: ModelManager,
    private val cloudPrefs: CloudModelPrefs,
) : GemmaInferenceEngine {

    override val isReady: Boolean
        get() = cloudConfiguredCached() || modelManager.isDownloaded || fallback.isReady

    /** Snapshot read of the cloud-configured flag — the actual
     *  authoritative value lives in DataStore but we cache via
     *  runBlocking on Dispatchers.IO inside OllamaGemmaEngine.refresh
     *  — and even if stale we re-check at call time inside the
     *  engine. */
    private fun cloudConfiguredCached(): Boolean = try {
        runBlocking { cloudPrefs.isConfigured() }
    } catch (_: Throwable) { false }

    override fun streamGenerate(
        prompt: String,
        maxNewTokens: Int,
        temperature: Float,
        topK: Int,
        topP: Float,
    ): Flow<String> {
        val cloudReady = cloudConfiguredCached()
        val onDeviceReady = modelManager.isDownloaded
        return when {
            cloudReady -> cloud.streamGenerate(prompt, maxNewTokens, temperature, topK, topP)
                .catch { _ ->
                    if (onDeviceReady) {
                        emitAll(primary.streamGenerate(prompt, maxNewTokens, temperature, topK, topP)
                            .catch { emitAll(fallback.streamGenerate(prompt, maxNewTokens, temperature, topK, topP)) })
                    } else {
                        emitAll(fallback.streamGenerate(prompt, maxNewTokens, temperature, topK, topP))
                    }
                }
            onDeviceReady -> primary.streamGenerate(prompt, maxNewTokens, temperature, topK, topP)
                .catch { emitAll(fallback.streamGenerate(prompt, maxNewTokens, temperature, topK, topP)) }
            else -> fallback.streamGenerate(prompt, maxNewTokens, temperature, topK, topP)
        }
    }

    override suspend fun generate(prompt: String, maxNewTokens: Int): String {
        if (cloudConfiguredCached()) {
            try { return cloud.generate(prompt, maxNewTokens) } catch (_: Throwable) {}
        }
        if (modelManager.isDownloaded) {
            try { return primary.generate(prompt, maxNewTokens) } catch (_: Throwable) {}
        }
        return fallback.generate(prompt, maxNewTokens)
    }
}
