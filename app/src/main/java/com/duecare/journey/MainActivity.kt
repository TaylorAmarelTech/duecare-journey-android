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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.duecare.journey.ui.theme.DuecareJourneyTheme
import dagger.hilt.android.AndroidEntryPoint

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

private enum class Tab(val label: String) {
    JOURNAL("Journal"),
    CHAT("Advice"),
    EXPORT("Complaint"),
    SETTINGS("Settings"),
}

@Composable
private fun JourneyAppRoot() {
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
            Tab.JOURNAL -> JournalScreenStub(padding)
            Tab.CHAT -> AdviceScreenStub(padding)
            Tab.EXPORT -> ExportScreenStub(padding)
            Tab.SETTINGS -> SettingsScreenStub(padding)
        }
    }
}

@Composable
private fun JournalScreenStub(padding: PaddingValues) {
    Text(
        text = "Journal — your encrypted timeline\n" +
            "(stub: real entries land here in v1 MVP)",
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
    )
}

@Composable
private fun AdviceScreenStub(padding: PaddingValues) {
    Text(
        text = "Advice — chat with Gemma 4 (on-device)\n" +
            "(stub: chat surface ports here from the desktop UI in v1 MVP, " +
            "with journal context auto-injected into each prompt)",
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
    )
}

@Composable
private fun ExportScreenStub(padding: PaddingValues) {
    Text(
        text = "Complaint packet — generate a PDF from your journal\n" +
            "(stub: ComplaintPacketExporter wires up in v1 MVP)",
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
    )
}

@Composable
private fun SettingsScreenStub(padding: PaddingValues) {
    Text(
        text = "Settings — model, language, app lock, panic wipe",
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
    )
}
