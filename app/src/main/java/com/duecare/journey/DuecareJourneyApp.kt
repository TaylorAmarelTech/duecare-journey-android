package com.duecare.journey

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Hilt-instrumented so DI is wired before the
 * first Activity is created. The on-disk SQLCipher key + LiteRT model
 * cache are initialized lazily when their respective layers are first
 * touched, NOT at app startup -- a worker who never opens the journal
 * never materializes the encrypted DB.
 */
@HiltAndroidApp
class DuecareJourneyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Privacy invariant: no analytics SDK init here. No crash
        // reporter init here. Logs go to local rotating file only,
        // surfaced in the debug menu for the worker to share manually.
    }
}
