package com.duecare.journey.onboarding

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.duecare.journey.journal.JourneyStage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "duecare_onboarding")

/**
 * Persists the worker's onboarding answers (stage + corridor +
 * completion flag) to a small DataStore-backed preferences file.
 *
 * NOT encrypted: these are non-sensitive coarse selections (the
 * worker's stage of journey + corridor) that the app uses to
 * tailor advice. The encrypted journal DB (SQLCipher) is a
 * separate concern.
 *
 * Privacy posture: even these coarse selections never leave the
 * device unless the worker explicitly invokes the export layer.
 */
@Singleton
class OnboardingPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val isCompleteKey = booleanPreferencesKey("onboarding_complete")
    private val stageKey = stringPreferencesKey("worker_stage")
    private val corridorKey = stringPreferencesKey("corridor")

    val isComplete: Flow<Boolean> = context.dataStore.data
        .map { it[isCompleteKey] ?: false }

    val stage: Flow<JourneyStage> = context.dataStore.data.map {
        JourneyStage.valueOf(it[stageKey] ?: JourneyStage.PRE_DEPARTURE.name)
    }

    val corridor: Flow<String?> = context.dataStore.data.map { it[corridorKey] }

    suspend fun complete(stage: JourneyStage, corridor: String?) {
        context.dataStore.edit { prefs ->
            prefs[isCompleteKey] = true
            prefs[stageKey] = stage.name
            prefs[corridorKey] = corridor ?: ""
        }
    }

    suspend fun reset() {
        context.dataStore.edit { it.clear() }
    }
}
