package com.duecare.journey.ui.fees

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duecare.journey.journal.FeePaymentRepository
import com.duecare.journey.journal.JourneyStage
import com.duecare.journey.journal.PartyKind
import com.duecare.journey.journal.PaymentMethod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * v0.7: structured fee-payment add flow. Used by the Journal and
 * Reports tabs. Creates a Party inline if the worker hasn't recorded
 * the recipient before, then writes the FeePayment via
 * [FeePaymentRepository] which auto-runs the StructuredFeeAssessor —
 * so a recoverable fee gets a LegalAssessment row attached at write
 * time and shows up in the Reports tab as ILLEGAL with a "Start
 * refund claim" button.
 */
@HiltViewModel
class AddFeeViewModel @Inject constructor(
    private val feeRepo: FeePaymentRepository,
) : ViewModel() {

    fun submit(
        recipientName: String,
        recipientKind: PartyKind,
        amountMajor: Double,
        currency: String,
        purposeLabel: String,
        purposeAsClaimed: String?,
        paymentMethod: PaymentMethod,
        stage: JourneyStage,
        workerNotes: String?,
        onResult: (FeePaymentRepository.AddResult) -> Unit,
    ) {
        val minor = (amountMajor * 100).toLong()
        viewModelScope.launch {
            val result = feeRepo.addWithNewParty(
                partyName = recipientName,
                partyKind = recipientKind,
                partyCountry = null,
                partyLicenseNumber = null,
                amountMinorUnits = minor,
                currency = currency,
                purposeLabel = purposeLabel,
                purposeAsClaimedByPayer = purposeAsClaimed,
                paymentMethod = paymentMethod,
                stage = stage,
                workerNotes = workerNotes,
            )
            onResult(result)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFeeDialog(
    currentStage: JourneyStage,
    onDismiss: () -> Unit,
    onSaved: (FeePaymentRepository.AddResult) -> Unit,
    vm: AddFeeViewModel = hiltViewModel(),
) {
    var recipientName by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("PHP") }
    var purposeLabel by remember { mutableStateOf("training fee") }
    var purposeAsClaimed by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf(PaymentMethod.BANK_TRANSFER) }
    var recipientKind by remember { mutableStateOf(PartyKind.RECRUITMENT_AGENCY) }
    var notes by remember { mutableStateOf("") }
    var paymentMethodMenuOpen by remember { mutableStateOf(false) }
    var recipientKindMenuOpen by remember { mutableStateOf(false) }
    var currencyMenuOpen by remember { mutableStateOf(false) }

    val amount = amountText.replace(",", "").toDoubleOrNull()
    val canSave = recipientName.isNotBlank() && (amount ?: 0.0) > 0 &&
        currency.isNotBlank() && purposeLabel.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    vm.submit(
                        recipientName = recipientName.trim(),
                        recipientKind = recipientKind,
                        amountMajor = amount ?: 0.0,
                        currency = currency.uppercase(),
                        purposeLabel = purposeLabel.trim(),
                        purposeAsClaimed = purposeAsClaimed.takeIf { it.isNotBlank() }?.trim(),
                        paymentMethod = paymentMethod,
                        stage = currentStage,
                        workerNotes = notes.takeIf { it.isNotBlank() }?.trim(),
                        onResult = { result ->
                            onSaved(result)
                        },
                    )
                },
                enabled = canSave,
            ) { Text("Save fee payment") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Record a fee payment") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Captures who got the money, for what, and how much. " +
                        "Auto-runs the legality check against your corridor's " +
                        "fee cap. Shows up in the Reports tab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = recipientName,
                    onValueChange = { recipientName = it },
                    label = { Text("Recipient name") },
                    placeholder = { Text("e.g. Pacific Coast Manpower Inc.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                EnumDropdown(
                    label = "Recipient type",
                    options = PartyKind.entries.toList(),
                    selected = recipientKind,
                    onPick = { recipientKind = it },
                    expanded = recipientKindMenuOpen,
                    onExpand = { recipientKindMenuOpen = it },
                    display = { it.name.lowercase().replace('_', ' ') },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                        label = { Text("Amount") },
                        placeholder = { Text("50000") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(0.6f),
                    )
                    EnumDropdown(
                        label = "Currency",
                        options = CURRENCIES,
                        selected = currency,
                        onPick = { currency = it },
                        expanded = currencyMenuOpen,
                        onExpand = { currencyMenuOpen = it },
                        display = { it },
                    )
                }
                OutlinedTextField(
                    value = purposeLabel,
                    onValueChange = { purposeLabel = it },
                    label = { Text("Purpose label") },
                    placeholder = { Text("training fee / placement fee / medical / processing") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = purposeAsClaimed,
                    onValueChange = { purposeAsClaimed = it },
                    label = { Text("Recipient's wording (optional)") },
                    placeholder = { Text("how the recruiter described the fee") },
                    singleLine = false,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                EnumDropdown(
                    label = "Payment method",
                    options = PaymentMethod.entries.toList(),
                    selected = paymentMethod,
                    onPick = { paymentMethod = it },
                    expanded = paymentMethodMenuOpen,
                    onExpand = { paymentMethodMenuOpen = it },
                    display = { it.name.lowercase().replace('_', ' ') },
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Your notes (optional)") },
                    minLines = 2, maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    label: String,
    options: List<T>,
    selected: T,
    onPick: (T) -> Unit,
    expanded: Boolean,
    onExpand: (Boolean) -> Unit,
    display: (T) -> String,
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { onExpand(!expanded) },
    ) {
        OutlinedTextField(
            value = display(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpand(false) },
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(display(opt)) },
                    onClick = {
                        onPick(opt)
                        onExpand(false)
                    },
                )
            }
        }
    }
}

private val CURRENCIES = listOf(
    "PHP", "HKD", "SGD", "USD", "IDR", "INR", "NPR", "BDT",
    "MYR", "THB", "VND", "SAR", "AED", "QAR", "KWD",
)
