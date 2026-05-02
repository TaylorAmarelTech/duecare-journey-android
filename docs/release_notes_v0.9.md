# v0.9.0 — Twenty corridors + 5 corridor-specific GREP rules

Released: 2026-05-02

Doubles bundled coverage from 12 to 20 migration corridors and adds
5 new GREP rules covering kafala, H-2A/B, fishing-vessel, smuggler-fee,
and live-in domestic-work patterns.

## New corridors (8)

| Code | Origin → Destination | Why it matters |
|---|---|---|
| **PH-IT** | Philippines → Italy | Filipino caregivers; Decreto Flussi quota; zero-fee per 2022 PH-IT bilateral |
| **ID-TW** | Indonesia → Taiwan | Major factory + domestic-work corridor; Direct Hiring Service Center bypass option |
| **MM-TH** | Myanmar → Thailand | Highest-incidence forced-labour corridor (Thai fishing fleets) |
| **KH-MY** | Cambodia → Malaysia | Female domestic-worker deployment ban → high irregular-migration risk |
| **ET-LB** | Ethiopia → Lebanon | Highest-incidence Lebanon-side trafficking per Anti-Slavery International 2024 |
| **KE-SA** | Kenya → Saudi Arabia | 2017 BLA's zero-fee policy + recent Kenyan worker deaths (2022-2024) |
| **AF-IR** | Afghanistan → Iran | Post-2021 humanitarian; smuggler fees + brick-kiln child labour |
| **ZW-ZA** | Zimbabwe → South Africa | Post-ZEP termination; informal mining + domestic recruitment |

Each new corridor includes the placement-fee policy + origin/dest
regulator + 2-3 NGO contacts. JournalRepository's corridor heuristic
now recognizes all 20 from journal text.

## New GREP rules (5)

| Rule ID | ILO Indicator | Severity | Patterns matched |
|---|---|---|---|
| **kafala-huroob-absconder** | #5 | CRITICAL | "huroob", "absconder", "tasreeh", "exit visa denied", "kafeel/sponsor refuses to release" |
| **h2a-h2b-fee-violation** | #4 | HIGH | H-2A/H-2B context + fee/charge/deduction; transportation/housing/tool/equipment deductions |
| **fishing-vessel-debt-confinement** | #4 | CRITICAL | Fishing vessel/at-sea + debt/loan/withhold/can't go ashore (Thai/Indonesian/Taiwanese fishing fleets) |
| **smuggler-fee-and-coercion** | #9 | HIGH | "smuggler", "coyote", "guia", USD/EUR XXXX "to cross/take me/get to"; refugee corridors |
| **domestic-work-locked-in-residence** | #5 | HIGH | "live-in" + "must stay in house" + "available 24/7" patterns; ILO C189 violation |

Each rule cites the specific statute (ILO C188 for fishing, ILO C189
for domestic, US DOL 20 CFR 655.135(j) for H-2A, etc.) + a recommended
next step.

## Tests

8 new unit tests:
- `all_twenty_corridors_present`
- `ph_it_zero_fee_decreto_flussi`
- `mm_th_corridor_notes_fishing_industry_risk`
- `et_lb_corridor_kafala_pattern`
- `grep_rule_kafala_huroob_fires_on_canonical_text`
- `grep_rule_h2_visa_fee_violation_fires`
- `grep_rule_fishing_vessel_pattern_fires`
- `grep_rule_smuggler_fee_pattern_fires`
- `grep_rule_domestic_live_in_pattern_fires`

All existing tests still pass.

## Audience reach now spans

Origin countries covered: **Philippines, Indonesia, Nepal, Bangladesh,
Mexico, Venezuela, Ghana, Nigeria, Syria, Ukraine, Italy (out-of-country
Filipinos), Taiwan (out-of-country Indonesians), Myanmar, Cambodia,
Ethiopia, Kenya, Afghanistan, Zimbabwe**.

Destination countries covered: **Hong Kong, Saudi Arabia, Singapore,
United States, Lebanon, Germany, Poland, Italy, Taiwan, Thailand,
Malaysia, Iran, South Africa**.

That's 13 destination countries + 18 origin countries = ~234
possible bilateral relationships, of which the 20 documented are the
highest-volume + highest-risk per ILO + Polaris + ASI 2024 reports.

## Bumped

versionCode 9 → 10, versionName 0.8.0-twelve-corridors → 0.9.0-twenty-corridors-new-rules.

## What didn't make it

- Localized chat UI (still v1.0 target)
- Photo OCR for contract / receipt scans (v1.0)
- Per-file Tink encryption for attachments (v1.0)
- Cantonese / Korean / Vietnamese corridor coverage (next wave)
