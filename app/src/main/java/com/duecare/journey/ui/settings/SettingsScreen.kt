package com.duecare.journey.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duecare.journey.inference.MediaPipeGemmaEngine
import com.duecare.journey.inference.ModelManager
import com.duecare.journey.journal.JournalRepository
import com.duecare.journey.onboarding.OnboardingPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val modelManager: ModelManager,
    private val mediaPipe: MediaPipeGemmaEngine,
    private val journal: JournalRepository,
    private val onboarding: OnboardingPrefs,
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = _state.value.copy(
            modelDownloaded = modelManager.isDownloaded,
            modelSizeMB = if (modelManager.isDownloaded)
                modelManager.modelFile().length() / 1024 / 1024 else 0L,
        )
    }

    fun downloadModel() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isDownloading = true,
                downloadProgress = 0,
                downloadError = null,
            )
            try {
                modelManager.download().collect { p ->
                    _state.value = _state.value.copy(
                        downloadProgress = p.percent,
                        isDownloading = !p.done,
                    )
                    if (p.done) refresh()
                }
            } catch (e: Throwable) {
                _state.value = _state.value.copy(
                    isDownloading = false,
                    downloadError = e.message ?: e::class.simpleName ?: "Download failed",
                )
            }
        }
    }

    fun dismissDownloadError() {
        _state.value = _state.value.copy(downloadError = null)
    }

    fun deleteModel() {
        viewModelScope.launch {
            mediaPipe.unload()
            modelManager.deleteCachedModel()
            refresh()
        }
    }

    fun importLocalModel(uri: android.net.Uri,
                          contentResolver: android.content.ContentResolver) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isDownloading = true,
                downloadProgress = 0,
                downloadError = null,
            )
            val ok = modelManager.importLocalFile(uri, contentResolver)
            _state.value = _state.value.copy(
                isDownloading = false,
                downloadError = if (ok) null else "Import failed (file too small or unreadable)",
            )
            refresh()
        }
    }

    fun panicWipe() {
        viewModelScope.launch {
            journal.panicWipe()
            onboarding.reset()
            mediaPipe.unload()
            modelManager.deleteCachedModel()
            _state.value = _state.value.copy(panicWiped = true)
        }
    }

    data class State(
        val modelDownloaded: Boolean = false,
        val modelSizeMB: Long = 0L,
        val isDownloading: Boolean = false,
        val downloadProgress: Int = 0,
        val downloadError: String? = null,
        val panicWiped: Boolean = false,
    )
}

@Composable
fun SettingsScreen(
    padding: PaddingValues,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val filePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            vm.importLocalModel(uri, context.contentResolver)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionHeader("On-device model")
        ModelCard(s, vm, onPickFile = {
            // Use a permissive MIME type — we accept any binary file
            // and rely on MediaPipe to reject it if it's not a valid
            // .task / .bin model.
            filePicker.launch(arrayOf("application/octet-stream", "*/*"))
        })

        SectionHeader("Privacy")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "Everything on this device stays on this device. " +
                        "No telemetry. No account. The journal is " +
                        "encrypted at rest with a key in your phone's " +
                        "secure storage. The only outbound network call " +
                        "this app makes is the one-time model download " +
                        "above (over your chosen Wi-Fi).",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        SectionHeader("Danger zone")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "Panic wipe — erases ALL journal entries, the model, " +
                        "and your onboarding answers. Cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { vm.panicWipe() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Erase everything") }
                if (s.panicWiped) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Done. Close + reopen the app to start fresh.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        SectionHeader("About")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Duecare Journey v0.4.0", fontWeight = FontWeight.Medium)
                Text(
                    "github.com/TaylorAmarelTech/duecare-journey-android",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "MIT licensed. Built with Google Gemma per the Gemma Terms of Use.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ModelCard(
    s: SettingsViewModel.State,
    vm: SettingsViewModel,
    onPickFile: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "Gemma 2 (2B INT4, CPU)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "MediaPipe LLM Inference. ~1.4 GB. Runs entirely " +
                    "on your phone after install.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Note: Google's Gemma model files are gated and may " +
                    "require Kaggle Models or HuggingFace login to download. " +
                    "If the auto-download fails, download the .task / .bin " +
                    "file yourself from kaggle.com/models/google/gemma or " +
                    "huggingface.co/litert-community then use 'Use my own " +
                    "model file' below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            )
            Spacer(Modifier.height(12.dp))
            // Surface any prior download error first
            s.downloadError?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            "Last download failed: $err",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = { vm.dismissDownloadError() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Dismiss") }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            when {
                s.isDownloading -> {
                    Text(
                        "Downloading… ${s.downloadProgress}%",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { s.downloadProgress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                s.modelDownloaded -> {
                    Text(
                        "Status: downloaded (${s.modelSizeMB} MB)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.deleteModel() }) {
                            Text("Delete model")
                        }
                    }
                }
                else -> {
                    Text(
                        "Status: not downloaded — chat is using fallback " +
                            "(canned legal-citation responses) until a model " +
                            "is available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { vm.downloadModel() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Try auto-download (~1.4 GB)")
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onPickFile,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Use my own model file")
                    }
                }
            }
        }
    }
}
