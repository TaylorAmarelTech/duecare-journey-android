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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import com.duecare.journey.inference.CloudModelPrefs
import com.duecare.journey.inference.MediaPipeGemmaEngine
import com.duecare.journey.inference.ModelManager
import com.duecare.journey.journal.FeePaymentDao
import com.duecare.journey.journal.JournalRepository
import com.duecare.journey.journal.PartyDao
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
    val cloudPrefs: CloudModelPrefs,
    private val mediaPipe: MediaPipeGemmaEngine,
    private val journal: JournalRepository,
    private val onboarding: OnboardingPrefs,
    private val partyDao: PartyDao,
    private val feeDao: FeePaymentDao,
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            val cfg = cloudPrefs.snapshot()
            _state.value = _state.value.copy(
                cloudUrl = cfg.url,
                cloudApiKey = cfg.apiKey,
                cloudModelName = cfg.modelName,
                cloudFormat = cfg.format,
                cloudConfigured = cfg.isConfigured,
                activeVariant = modelManager.activeVariant(),
            )
        }
    }

    fun refresh() {
        _state.value = _state.value.copy(
            modelDownloaded = modelManager.isDownloaded,
            modelSizeMB = if (modelManager.isDownloaded)
                modelManager.modelFile().length() / 1024 / 1024 else 0L,
            activeVariant = modelManager.activeVariant(),
        )
    }

    fun selectVariant(v: ModelManager.ModelVariant) {
        viewModelScope.launch {
            modelManager.setVariant(v)
            refresh()
        }
    }

    fun downloadModel(allowCellular: Boolean = false) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isDownloading = true,
                downloadProgress = 0,
                downloadError = null,
                isVerifying = false,
            )
            try {
                modelManager.download(requireUnmetered = !allowCellular).collect { p ->
                    _state.value = _state.value.copy(
                        downloadProgress = p.percent,
                        isDownloading = !p.done,
                        isVerifying = p.verifying,
                    )
                    if (p.done) refresh()
                }
            } catch (e: Throwable) {
                _state.value = _state.value.copy(
                    isDownloading = false,
                    isVerifying = false,
                    downloadError = e.message ?: e::class.simpleName ?: "Download failed",
                )
            }
        }
    }

    fun setCustomUrl(url: String, sha256: String) {
        viewModelScope.launch {
            modelManager.setCustomUrl(url, sha256)
        }
    }

    fun saveCloudConfig(
        url: String, apiKey: String, modelName: String, format: CloudModelPrefs.Format,
    ) {
        viewModelScope.launch {
            cloudPrefs.save(url, apiKey, modelName, format)
            _state.value = _state.value.copy(
                cloudUrl = url, cloudApiKey = apiKey, cloudModelName = modelName,
                cloudFormat = format, cloudConfigured = url.isNotBlank(),
            )
        }
    }

    fun clearCloudConfig() {
        viewModelScope.launch {
            cloudPrefs.clear()
            _state.value = _state.value.copy(
                cloudUrl = "", cloudApiKey = "", cloudModelName = "gemma4:e2b",
                cloudFormat = CloudModelPrefs.Format.OLLAMA, cloudConfigured = false,
            )
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
            cloudPrefs.clear()
            _state.value = _state.value.copy(panicWiped = true)
        }
    }

    /** v0.6: explicit demo-data load. Replaces the auto-seed that
     *  was misleading new users into thinking the red-flagged sample
     *  entries were their own. Each sample entry's title is prefixed
     *  with "[Example]" so it's obvious. Idempotent: re-seeds only
     *  if no [Example] entries already exist.
     *
     *  Loads ALL three layers of sample data: journal entries (so
     *  Reports → ILO indicators show coverage), parties (so fee
     *  payments resolve to real names), and fee payments (so the
     *  Reports → Fees section shows the legality flag against
     *  the corridor fee cap). */
    fun loadDemoData() {
        viewModelScope.launch {
            val existing = journal.recent(50)
            if (existing.any { it.title.startsWith("[Example]") }) return@launch
            val sample = com.duecare.journey.journal.sample.SampleData
            // 1. Parties first (FK from FeePayment)
            sample.sampleParties.forEach { partyDao.upsert(it) }
            // 2. Journal entries (auto-tagged via JournalRepository.add)
            sample.sampleJournalEntries.forEach { e ->
                journal.add(
                    stage = e.stage,
                    kind = e.kind,
                    title = "[Example] ${e.title}",
                    body = e.body,
                    parties = e.parties,
                    taggedConcerns = e.taggedConcerns,
                )
            }
            // 3. Fee payment (so Reports tab shows totals + legality)
            feeDao.upsert(sample.trainingFeePayment)
            _state.value = _state.value.copy(demoLoaded = true)
        }
    }

    fun deleteDemoEntries() {
        viewModelScope.launch {
            val all = journal.recent(1000)
            all.filter { it.title.startsWith("[Example]") }.forEach { e ->
                journal.delete(e.id)
            }
            // Also remove the demo fee payment (best-effort; ignore if
            // the worker has already deleted it manually)
            try { feeDao.delete("fp_001") } catch (_: Throwable) {}
            _state.value = _state.value.copy(demoLoaded = false)
        }
    }

    data class State(
        val modelDownloaded: Boolean = false,
        val modelSizeMB: Long = 0L,
        val isDownloading: Boolean = false,
        val downloadProgress: Int = 0,
        val isVerifying: Boolean = false,
        val downloadError: String? = null,
        val panicWiped: Boolean = false,
        val demoLoaded: Boolean = false,
        val activeVariant: ModelManager.ModelVariant =
            ModelManager.ModelVariant.GEMMA4_E2B_INT8_LITERTLM,
        val cloudUrl: String = "",
        val cloudApiKey: String = "",
        val cloudModelName: String = "gemma4:e2b",
        val cloudFormat: CloudModelPrefs.Format = CloudModelPrefs.Format.OLLAMA,
        val cloudConfigured: Boolean = false,
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionHeader("On-device model")
        ModelVariantPicker(s, onPick = { vm.selectVariant(it) })
        ModelCard(s, vm, onPickFile = {
            filePicker.launch(arrayOf("application/octet-stream", "*/*"))
        })

        SectionHeader("Cloud model (alternative — for testing or when on-device fails)")
        CloudModelCard(s, onSave = { url, key, name, fmt ->
            vm.saveCloudConfig(url, key, name, fmt)
        }, onClear = { vm.clearCloudConfig() })

        SectionHeader("Demo data")
        DemoDataCard(s, onLoad = { vm.loadDemoData() }, onDelete = { vm.deleteDemoEntries() })

        SectionHeader("Privacy")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "Everything on this device stays on this device by default. " +
                        "No telemetry. No account. The journal is encrypted at " +
                        "rest with a key in your phone's secure storage. The only " +
                        "outbound network calls this app makes are: (1) the " +
                        "one-time on-device model download above (over your " +
                        "chosen Wi-Fi), and (2) optional cloud-model routing if " +
                        "you have configured it above (each chat message is " +
                        "sent to your configured cloud endpoint).",
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
                    "Panic wipe — erases ALL journal entries, the model, your " +
                        "onboarding answers, and any cloud-model config. " +
                        "Cannot be undone.",
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
                Text("Duecare Journey v0.6.0", fontWeight = FontWeight.Medium)
                Text(
                    "github.com/TaylorAmarelTech/duecare-journey-android",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "MIT licensed. Built with Google Gemma per the Gemma " +
                        "Terms of Use (Gemma 2) or Apache 2.0 (Gemma 3 / 4).",
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
private fun ModelVariantPicker(
    s: SettingsViewModel.State,
    onPick: (ModelManager.ModelVariant) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "Choose a model variant",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Each variant has multiple mirror URLs. If the primary " +
                    "fails, the app tries each mirror in order before giving " +
                    "up. Switching variants doesn't delete the previous " +
                    "download — switch back any time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            ModelManager.ModelVariant.entries.forEach { v ->
                val selected = v == s.activeVariant
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                ) {
                    RadioButton(selected = selected, onClick = { onPick(v) })
                    Spacer(Modifier.width(2.dp))
                    Column {
                        Text(v.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.SemiBold
                            else FontWeight.Normal)
                        Text(v.familyDescription,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${v.urls.size} mirror URL(s) configured",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
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
                s.activeVariant.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "MediaPipe LLM Inference. ${s.activeVariant.familyDescription}. " +
                    "Runs entirely on your phone after install.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Note: some Gemma model files are gated and may require " +
                    "Kaggle Models or HuggingFace login to download. If the " +
                    "auto-download fails for every mirror, download a .task " +
                    "or .litertlm file yourself from kaggle.com/models/google " +
                    "or huggingface.co/litert-community then use 'Use my own " +
                    "model file' below — OR configure a cloud model.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            )
            Spacer(Modifier.height(12.dp))
            s.downloadError?.let { err ->
                val isCellularRefusal = err.contains("metered") || err.contains("cellular")
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
                        if (isCellularRefusal) {
                            Button(
                                onClick = {
                                    vm.dismissDownloadError()
                                    vm.downloadModel(allowCellular = true)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Start anyway on cellular") }
                            Spacer(Modifier.height(6.dp))
                        }
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
                        if (s.isVerifying)
                            "Verifying file integrity… (sha256)"
                        else
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
                    val sizeMB = s.activeVariant.expectedSizeBytes / 1024 / 1024
                    Text(
                        "Status: not downloaded — chat is using fallback " +
                            "(canned legal-citation responses or cloud model " +
                            "if configured) until a model is available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "~$sizeMB MB. Free on Wi-Fi. Will try " +
                                "${s.activeVariant.urls.size} mirror(s) in order.",
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { vm.downloadModel() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Download via Wi-Fi (~$sizeMB MB)")
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onPickFile,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Use my own model file")
                    }
                    Spacer(Modifier.height(8.dp))
                    CustomUrlSection(vm)
                }
            }
        }
    }
}

@Composable
private fun CustomUrlSection(vm: SettingsViewModel) {
    var expanded by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var sha by remember { mutableStateOf("") }
    androidx.compose.material3.TextButton(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (expanded) "Hide custom URL" else "Use a custom download URL")
    }
    if (expanded) {
        androidx.compose.material3.OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Direct download URL") },
            placeholder = { Text("https://huggingface.co/...resolve/main/...litertlm") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        androidx.compose.material3.OutlinedTextField(
            value = sha,
            onValueChange = { sha = it },
            label = { Text("SHA-256 (optional)") },
            placeholder = { Text("hex digest — leave blank to skip verify") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = {
                    vm.setCustomUrl(url, sha)
                    expanded = false
                },
                enabled = url.isNotBlank(),
            ) { Text("Save") }
            OutlinedButton(
                onClick = {
                    url = ""
                    sha = ""
                    vm.setCustomUrl("", "")
                },
            ) { Text("Reset to default") }
        }
    }
}

@Composable
private fun CloudModelCard(
    s: SettingsViewModel.State,
    onSave: (String, String, String, CloudModelPrefs.Format) -> Unit,
    onClear: () -> Unit,
) {
    var url by remember(s.cloudUrl) { mutableStateOf(s.cloudUrl) }
    var key by remember(s.cloudApiKey) { mutableStateOf(s.cloudApiKey) }
    var name by remember(s.cloudModelName) { mutableStateOf(s.cloudModelName) }
    var format by remember(s.cloudFormat) { mutableStateOf(s.cloudFormat) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("Route chat to a cloud Gemma 4 endpoint",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Useful when on-device download fails (HF 404, gated, " +
                    "low storage) OR when you want to test the app immediately " +
                    "without waiting for a 1.5 GB download. Each chat message " +
                    "is POSTed to your configured endpoint. Privacy: configure " +
                    "this only against an endpoint you trust (your own laptop, " +
                    "your NGO's server, or a service you have a contract with).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (s.cloudConfigured) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Text("✓ Cloud routing active — ${s.cloudFormat.displayName}",
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
                Spacer(Modifier.height(8.dp))
            }
            Text("Format", style = MaterialTheme.typography.labelMedium)
            CloudModelPrefs.Format.entries.forEach { f ->
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()) {
                    RadioButton(selected = format == f, onClick = { format = f })
                    Spacer(Modifier.width(2.dp))
                    Text(f.displayName, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.OutlinedTextField(
                value = url, onValueChange = { url = it },
                label = { Text("Endpoint URL") },
                placeholder = { Text(when (format) {
                    CloudModelPrefs.Format.OLLAMA -> "http://192.168.1.50:11434"
                    CloudModelPrefs.Format.OPENAI -> "https://api.together.xyz/v1"
                    CloudModelPrefs.Format.HF -> "https://api-inference.huggingface.co/models/google/gemma-4-2b-it"
                }) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Model name") },
                placeholder = { Text(when (format) {
                    CloudModelPrefs.Format.OLLAMA -> "gemma4:e2b"
                    CloudModelPrefs.Format.OPENAI -> "google/gemma-4-2b-it"
                    CloudModelPrefs.Format.HF -> "(ignored — URL is the model)"
                }) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.OutlinedTextField(
                value = key, onValueChange = { key = it },
                label = { Text("API key (optional Bearer token)") },
                placeholder = { Text("leave blank for unauthenticated local Ollama") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSave(url, key, name, format) },
                    enabled = url.isNotBlank(),
                ) { Text("Save & enable") }
                OutlinedButton(onClick = onClear) { Text("Clear") }
            }
        }
    }
}

@Composable
private fun DemoDataCard(
    s: SettingsViewModel.State,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("Sample journal entries", fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Load a few realistic example entries — useful for " +
                    "demonstrating the app to NGOs, exploring the Reports " +
                    "tab without typing your own entries first, or showing " +
                    "what an at-risk situation looks like. Each example " +
                    "entry is prefixed [Example] so it can't be confused " +
                    "with your own data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onLoad, enabled = !s.demoLoaded) {
                    Text(if (s.demoLoaded) "Loaded ✓" else "Load demo entries")
                }
                OutlinedButton(onClick = onDelete) {
                    Text("Delete all [Example] entries")
                }
            }
        }
    }
}
