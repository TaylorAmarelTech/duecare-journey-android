# v0.6.0 — Cloud Gemma + Domain Knowledge + NGO Reports

Released: 2026-04-30

This release closes the loop on "the app actually works end-to-end and
produces something an NGO can act on." Three big additions:

## 1. Cloud Gemma 4 fallback (so the app works while on-device download is broken)

The v0.5 on-device download URL was returning 404 against
`litert-community/gemma-4-E2B-it-litert-lm` (HF apparently renamed the
file). Rather than block the whole app on getting the right URL, v0.6
adds a Cloud model option in **Settings → Cloud model**:

- Three formats supported: **Ollama** (`/api/generate`), **OpenAI-
  compatible** (`/v1/chat/completions`), and **Hugging Face Inference
  Endpoint** (`/models/...`).
- Each takes an endpoint URL, an optional Bearer API key, and a model
  name. Saves to encrypted DataStore.
- The chat surface routes through the new **SmartGemmaEngine**:
  `Cloud → MediaPipe (on-device) → Stub`. Whichever tier is configured
  and reachable wins.
- Privacy: cloud routing means each chat message leaves the device.
  Settings explains this in plain language before saving. The on-device
  path remains the recommended default.

Plug-and-play with `ollama serve` on a laptop on the same Wi-Fi.

## 2. Multi-variant on-device + multiple fallback URLs

Settings → On-device model now lets the worker pick from **six**
variants:

| Variant | Quant | Size | Notes |
|---|---|---:|---|
| Gemma 4 E2B | INT4 | ~750 MB | smallest, low-RAM phones |
| **Gemma 4 E2B** | **INT8** | **~1.5 GB** | **v0.6 default** |
| Gemma 4 E4B | INT4 | ~2.0 GB | needs 6 GB+ RAM |
| Gemma 4 E4B | INT8 | ~3.5 GB | needs 8 GB+ RAM |
| Gemma 3 1B | INT4 | ~600 MB | fastest first-token latency |
| Gemma 2 2B | INT4 | ~1.35 GB | gated, sideload preferred |

Each variant carries a **list of mirror URLs**. The download tries each
in order; on 404 / connect failure, falls through to the next. Mirrors
include the litert-community HF repo (primary), an alternate HF path,
and a placeholder for the GitHub Releases mirror we'll publish next.

A worker who hits a 404 sees `All N mirror(s) failed for <variant>` with
explicit workarounds: switch variant, paste a custom URL, sideload a
local file, or configure cloud routing.

## 3. Domain knowledge layer + NGO report generation

The app now ships its own **GREP rules**, **ILO indicators**, **corridor
fee caps**, and **NGO contact directory** as on-device knowledge — a
condensed Kotlin port of the `gemma4_comp` safety harness:

- **DomainKnowledge.GrepRules** — 11 rules covering passport
  withholding, wage routing to lender, extortionate APR, fee
  camouflage, restriction of movement, physical violence, isolation,
  wage theft, contract substitution, excessive overtime, and threats
  of deportation. Each rule maps to an ILO indicator + statute citation
  + recommended next step.
- **IloForcedLabourIndicators** — all 11 ILO C029 indicators as a
  canonical taxonomy.
- **CorridorKnowledge** — 6 corridors (PH-HK, ID-HK, PH-SA, NP-SA,
  BD-SA, ID-SG) each with the legal placement-fee cap, the controlling
  origin + destination regulator, and 2-3 trusted NGO contacts.

This drives a new **Reports** tab that replaces the placeholder
Complaint tab:

- **Case overview**: entries, fee lines, risk flags, critical risks,
  corridor.
- **ILO indicator coverage**: histogram of which of the 11 indicators
  have fired against the worker's journal.
- **Detailed risk findings**: each fired rule shows source entry,
  matched phrase, ILO indicator, statute, what it means, and next step.
- **Fee table**: aggregated from structured FeePayment rows AND from
  free-text journal entries (regex extraction). Each line is tagged
  legal/illegal vs the corridor cap, with totals by currency and
  separate "likely recoverable" totals.
- **Generate intake document** — produces a markdown doc combining
  everything above plus chronological timeline + recommended NGO
  contacts. Shareable via the OS share sheet.

This is the deliverable an NGO caseworker can read in two minutes
to understand the situation.

## 4. Quick guided intake wizard

The Journal tab now shows a **Quick guided intake** call-to-action
above the entry list. Tapping it opens a 10-question wizard that walks
the worker through recruiter, fees, contract, documents, destination,
communication, and pressure/threats — the most evidence-rich
exploitation-pattern categories.

Each non-empty answer becomes a real journal entry that flows through
the **auto-risk-tagging** pipeline (new in v0.6: every JournalRepository
add runs RiskAnalyzer and populates `taggedConcerns` + `grepHits`).

The wizard is deterministic — no model required — but the answers are
used by both Reports and the Advice (chat) tab.

## 5. Honest demo-data toggle (replaces auto-seed)

The v0.5 app silently seeded two red-flagged sample entries on first
launch, which led at least one tester to think they had been recorded
incorrectly. v0.6:

- Removes the auto-seed.
- Adds **Settings → Demo data** with explicit "Load demo entries" and
  "Delete all [Example] entries" buttons.
- Sample entries now carry an `[Example]` prefix so they can't be
  confused with the worker's own data.
- Loading also seeds the corresponding parties + fee payments so the
  Reports tab demo is realistic out of the box.

## 6. Housekeeping

- **Removed**: `LiteRTGemmaEngine.kt` (unused stub — MediaPipe is the
  active path) and `ComplaintPacketExporter.kt` (superseded by
  NgoReportBuilder + ReportsScreen).
- **Added**: `PartyDao.observeAll()` so the Reports tab can resolve
  fee-payment recipient names without per-fee lookups.
- **Bumped**: `versionCode 6 → 7`, `versionName 0.5.0-gemma4-sha-verify-custom-url
  → 0.6.0-cloud-and-knowledge`.
- **Privacy**: Panic wipe now also clears the cloud-model config (URL +
  API key + model name + format) alongside the journal + model + onboarding.
