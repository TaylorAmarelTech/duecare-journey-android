package com.duecare.journey.di

import android.content.Context
import com.duecare.journey.inference.GemmaInferenceEngine
import com.duecare.journey.inference.MediaPipeGemmaEngine
import com.duecare.journey.inference.ModelManager
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import javax.inject.Singleton

/**
 * Hilt module wiring up the four layers.
 *
 * Inference: provides a smart engine that prefers MediaPipe when the
 * Gemma model is downloaded + loadable, and falls back to the stub
 * (canned responses) otherwise. Worker can force the stub via
 * Settings → Use canned responses (planned v0.4 toggle).
 *
 * Journal v2: provides DAOs for the entity set (Party, FeePayment,
 * LegalAssessment, RefundClaim) alongside the original JournalDao.
 * All DAOs are backed by a single SQLCipher-encrypted Room database.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGemmaEngine(
        mediapipe: MediaPipeGemmaEngine,
        stub: StubGemmaEngine,
        modelManager: ModelManager,
    ): GemmaInferenceEngine = SmartGemmaEngine(
        primary = mediapipe,
        fallback = stub,
        modelManager = modelManager,
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
 * Tries MediaPipe first if a model is downloaded; falls back to the
 * stub on any failure. Lets the chat UI work end-to-end whether or
 * not the worker has downloaded the model yet.
 *
 * v0.4 fix: streamGenerate previously wrapped a Flow factory call in
 * try/catch, which is dead code (Flow exceptions surface at collect
 * time, not factory time). Now uses Flow.catch + emitAll so a
 * MediaPipe failure mid-stream actually falls back to the stub
 * instead of propagating an exception to the UI.
 */
internal class SmartGemmaEngine(
    private val primary: MediaPipeGemmaEngine,
    private val fallback: StubGemmaEngine,
    private val modelManager: ModelManager,
) : GemmaInferenceEngine {

    override val isReady: Boolean
        get() = modelManager.isDownloaded || fallback.isReady

    override fun streamGenerate(
        prompt: String,
        maxNewTokens: Int,
        temperature: Float,
        topK: Int,
        topP: Float,
    ): kotlinx.coroutines.flow.Flow<String> = if (modelManager.isDownloaded) {
        primary.streamGenerate(prompt, maxNewTokens, temperature, topK, topP)
            .catch { _ ->
                emitAll(
                    fallback.streamGenerate(
                        prompt, maxNewTokens, temperature, topK, topP,
                    )
                )
            }
    } else {
        fallback.streamGenerate(prompt, maxNewTokens, temperature, topK, topP)
    }

    override suspend fun generate(prompt: String, maxNewTokens: Int): String {
        return if (modelManager.isDownloaded) {
            try {
                primary.generate(prompt, maxNewTokens)
            } catch (e: Throwable) {
                fallback.generate(prompt, maxNewTokens)
            }
        } else {
            fallback.generate(prompt, maxNewTokens)
        }
    }
}
