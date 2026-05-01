package com.duecare.journey.di

import android.content.Context
import com.duecare.journey.inference.GemmaInferenceEngine
import com.duecare.journey.inference.StubGemmaEngine
import com.duecare.journey.journal.JournalDao
import com.duecare.journey.journal.JournalDatabase
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

    @Provides
    @Singleton
    fun provideJournalDao(db: JournalDatabase): JournalDao = db.journalDao()
}
