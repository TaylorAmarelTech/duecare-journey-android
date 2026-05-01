package com.duecare.journey.di

import android.content.Context
import com.duecare.journey.inference.GemmaInferenceEngine
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
import javax.inject.Singleton

/**
 * Hilt module wiring up the four layers.
 *
 * Inference: bound to [StubGemmaEngine] for the v0.1 skeleton build.
 * v1 MVP swaps in [com.duecare.journey.inference.LiteRTGemmaEngine]
 * once the LiteRT model bundle is published.
 *
 * Journal v2: provides DAOs for the new entity set (Party,
 * FeePayment, LegalAssessment, RefundClaim) alongside the original
 * JournalDao. All DAOs are backed by a single SQLCipher-encrypted
 * Room database (see [JournalDatabase]).
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGemmaEngine(stub: StubGemmaEngine): GemmaInferenceEngine = stub

    @Provides
    @Singleton
    fun provideJournalDatabase(@ApplicationContext ctx: Context): JournalDatabase =
        JournalDatabase.create(ctx)

    // ---- DAOs ----
    @Provides fun provideJournalDao(db: JournalDatabase): JournalDao = db.journalDao()
    @Provides fun providePartyDao(db: JournalDatabase): PartyDao = db.partyDao()
    @Provides fun provideFeePaymentDao(db: JournalDatabase): FeePaymentDao =
        db.feePaymentDao()
    @Provides fun provideLegalAssessmentDao(db: JournalDatabase): LegalAssessmentDao =
        db.legalAssessmentDao()
    @Provides fun provideRefundClaimDao(db: JournalDatabase): RefundClaimDao =
        db.refundClaimDao()
}
