# v0.9.1 — Adversarial-equivocation GREP rules

Synced from the parent harness (`gemma4_comp`) `GREP_RULES`. Adds the
cross-domain **analogy-laundering** detector to the on-device advice prompt:
five high-precision rules that fire when a benign-domain analogy is used to
normalize a labour-control arrangement.

## What changed

Advice prompt harness GREP rules: **4 → 9**.

| New rule | Fires on the move… | ILO basis |
|---|---|---|
| `equivocation_bond_financial_instrument` | worker bond reframed as a "financial instrument" / "deposit bond" | C029 + P029; C181 Art. 7; Indicator 4 |
| `equivocation_broker_commission_normal` | labour-broker fee normalized as "commission is normal everywhere" | C181 Art. 7; IRIS Standard |
| `equivocation_sponsor_like_event` | kafala sponsor control reframed as "like sponsoring an event" | C029; kafala reform standards |
| `equivocation_safekeeping_analogy` | passport confiscation as "safekeeping, like a vault / cargo hold" | C029 + P029; Indicator 1 |
| `equivocation_charge_standard_business` | worker-charged fee as "just standard business practice" | C181 Art. 7; POEA / BP2MI |

Each rule is `allRequired = true` (the control term AND the analogy phrase), so
benign uses of the same words — a government savings *bond*, *sponsoring* a
football team — do not fire.

## Why

The parent project's representation-loss research identified **semantic
ambiguity / equivocation** as a distinct attack surface: a recruiter does not
need to state an illegal arrangement plainly when they can launder it through a
benign-domain analogy. The on-device app is exactly where a worker encounters
that framing (a recruiter's WhatsApp message), so the detector belongs here, in
the offline advice harness — not only on the server.

Research basis: `gemma4_comp/docs/research/semantic_ambiguity_in_llm_pipelines.md`.

## Version

- `versionCode` 10 → 11
- `versionName` `0.9.0-twenty-corridors-new-rules` → `0.9.1-equivocation-rules`
- `HarnessInfo.PROMPT_GREP_RULE_COUNT` 4 → 9
- `duecare_harness_manifest.json` `grep_rule_count` 4 → 9 (+ 5 ids)
- `build-apk.yml` manifest checks updated to `version_code == 11`, `grep_rule_count == 9`

## Build

The `Build APK` workflow rebuilds and re-verifies the bundled manifest on push.
Download the `duecare-journey-debug-apk` artifact from the Actions run and
sideload with `adb install`.
