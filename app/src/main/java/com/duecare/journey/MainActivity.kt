package com.duecare.journey

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duecare.journey.journal.JourneyStage
import com.duecare.journey.onboarding.OnboardingPrefs
import com.duecare.journey.ui.advice.AdviceScreen
import com.duecare.journey.ui.journal.JournalScreen
import com.duecare.journey.ui.onboarding.OnboardingScreen
import com.duecare.journey.ui.settings.SettingsScreen
import com.duecare.journey.ui.theme.DuecareJourneyTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DuecareJourneyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    JourneyAppRoot()
                }
            }
        }
    }
}

@HiltViewModel
class RootViewModel @Inject constructor(
    val onboarding: OnboardingPrefs,
) : ViewModel() {

    val isOnboardingComplete: StateFlow<Boolean?> = onboarding.isComplete
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,           // null = "still loading"
        )

    fun completeOnboarding(stage: JourneyStage, corridor: String?) {
        viewModelScope.launch {
            onboarding.complete(stage, corridor)
        }
    }
}

private enum class Tab(val label: String) {
    JOURNAL("Journal"),
    CHAT("Advice"),
    EXPORT("Complaint"),
    SETTINGS("Settings"),
}

@Composable
private fun JourneyAppRoot(rootVm: RootViewModel = hiltViewModel()) {
    val onboardingComplete by rootVm.isOnboardingComplete.collectAsState()

    when (onboardingComplete) {
        null -> Unit                                         // loading splash; keep blank
        false -> OnboardingScreen(onComplete = rootVm::completeOnboarding)
        true -> MainTabNav()
    }
}

@Composable
private fun MainTabNav() {
    var current by remember { mutableStateOf(Tab.JOURNAL) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab,
                        onClick = { current = tab },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    Tab.JOURNAL -> Icons.Outlined.Book
                                    Tab.CHAT -> Icons.Outlined.Forum
                                    Tab.EXPORT -> Icons.Outlined.Description
                                    Tab.SETTINGS -> Icons.Outlined.Settings
                                },
                                contentDescription = tab.label,
                            )
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        when (current) {
            Tab.JOURNAL -> JournalScreen(padding)
            Tab.CHAT -> AdviceScreen(padding)
            Tab.EXPORT -> ExportPlaceholder(padding)
            Tab.SETTINGS -> SettingsScreen(padding)
        }
    }
}

@Composable
private fun ExportPlaceholder(padding: PaddingValues) {
    Text(
        text = "Complaint packet export — generates a PDF from your journal " +
            "with the right NGO/regulator address and a draft narrative. " +
            "Ships in v0.4 (week of 2026-05-19).",
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
    )
}
