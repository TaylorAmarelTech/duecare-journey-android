# v0.7.0 — Quality + Refund Claims + Tests

Released: 2026-05-01

This release closes the gaps the audit found in v0.6 and builds out
the refund-claim chain that NGOs explicitly want.

## 1. Structured fee payments + auto-legality + refund-claim drafting

Previously workers entered fees as free text in the intake wizard;
extraction-via-regex was best-effort. v0.7 adds:

- **Add fee payment** dialog accessible from the Reports tab —
  recipient name + party kind + amount + currency + purpose label +
  recipient's wording + payment method + worker notes. Creates a
  Party row inline if the recipient hasn't been recorded yet.
- **Auto legality assessment** — every saved FeePayment runs through
  the new `StructuredFeeAssessor` which checks the corridor's
  placement-fee cap. Recoverable fees get a `LegalAssessment` row
  attached automatically with controlling statute + ILO convention +
  explanatory reasoning.
- **Start refund claim** button on every illegal fee line in the
  Reports tab. Creates a draft `RefundClaim` with:
    - Pre-filled cover letter naming the recipient, the statute, the
      amount, and the worker's payment method
    - Draft delivery message ready for the OS share sheet
    - Editable status workflow (DRAFT → FILED → IN_REVIEW → GRANTED /
      DENIED / WITHDRAWN / PARTIALLY_RECOVERED)
- **Refund claims list** in the Reports tab — share, mark filed, or
  withdraw each claim.

This wires the previously-orphaned `LegalAssessmentDao` and
`RefundClaimDao` end-to-end. v0.6 had the data model but no UI path;
v0.7 closes the loop.

## 2. Photo attachments

`JournalEntry.attachmentPath` has existed since v0.1 but nothing
populated it. v0.7:

- **Add Entry dialog** has an "Attach photo" button (image picker via
  ActivityResultContracts.GetContent).
- New `AttachmentStorage` service copies the picked image into private
  internal storage (`filesDir/attachments/<uuid>.<ext>`), persisting
  the relative path on the JournalEntry.
- Panic wipe clears the attachments dir alongside the journal +
  model + onboarding + cloud config.
- Storage location is sandboxed and excluded from backup. Per-file
  Tink encryption is the v0.8 hardening.

## 3. Clear chat history

In-memory chat history persisted across navigation forever in v0.6
with no way to reset short of relaunching the app. v0.7 adds a small
trash-icon button in the Advice tab's input row that clears the
chat history.

## 4. JVM unit tests

The Android repo had no `app/src/test/` directory until v0.7. Added
JUnit tests for the intel/ layer:

- `DomainKnowledgeTest` — GREP rules fire on canonical exploitation
  language; ILO indicators cover 1-11; corridor profiles all carry
  regulator + NGO contacts.
- `RiskAnalyzerTest` — analyze + analyzeAll + iloIndicatorHistogram.
- `FeeAggregatorTest` — structured + extracted lines, illegality
  flagging, totals-by-currency.
- `StructuredFeeAssessorTest` — zero-fee corridor, NPR cap-exceed,
  no-corridor case, statute lookup.
- `IntakeWizardTest` — unique IDs, blank-answer rejection, draft
  shape, category coverage.
- `TimelineBuilderTest` — chronological ordering, stage grouping,
  critical-vs-total counts.

These run with `./gradlew test` and require no Android SDK / emulator.

## 5. Doc + comment cleanup

- AndroidManifest.xml — comment about removed LiteRTGemmaEngine
  rewritten to describe the actual v0.6+ INTERNET use (model download
  + opt-in cloud routing).
- strings.xml — `tab_export = "Complaint"` → `tab_reports = "Reports"`;
  added new `reports_*`, `advice_clear`, BETWEEN_EMPLOYERS stage
  strings; updated privacy banner.
- proguard-rules.pro — added MediaPipe + Duecare model classes (Room
  entities, intel/, journal/, inference/, harness/) to keep set so
  release builds don't strip them.
- MediaPipeGemmaEngine.kt — fixed comment that said v0.4 streaming
  was coming (now correctly says v0.7+).
- CloudModelPrefs.kt — historical note about v0.5's hard-coded URL
  rewritten more clearly.

## 6. Race condition fix

`ReportsViewModel.generateMarkdownReport()` was reading both the
combined `state.value` AND re-reading each DAO via `.first()` — the
former lagged by 5s due to `SharingStarted.WhileSubscribed(5_000)`,
which could produce stale reports if the worker tapped Generate
immediately after editing an entry. Now it reads only the DAOs at
generate time.

## 7. Bumped

versionCode 7 → 8, versionName 0.6.0-cloud-and-knowledge →
0.7.0-quality-and-claims.

## What didn't make it

- Per-file Tink encryption for attachments — v0.8.
- Real per-token streaming from MediaPipe (currently chunks the
  one-shot response client-side) — v0.7 has a comment update only;
  actual switch to `LlmInference.ResponseListener` is v0.8.
- Hierarchical Examples modal in the chat surface — still backlog.
