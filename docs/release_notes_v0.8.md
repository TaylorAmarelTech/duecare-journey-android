# v0.8.0 — Twelve corridors (LATAM + West Africa + refugee routes)

Released: 2026-05-02

Doubles the bundled migration-corridor coverage from 6 to 12 by
adding Latin America (Mexico→US, Venezuela→Colombia), West Africa
to Lebanon (Ghana→Lebanon, Nigeria→Lebanon, both kafala-system),
and post-2022 refugee corridors (Syria→Germany, Ukraine→Poland).

## What this enables

A worker, NGO, lawyer, or regulator anywhere along these
corridors now gets statute-grounded responses:

- **MX-US** (H-2A / H-2B): zero-fee policy per US DOL 20 CFR
  655.135(j); Mexican STPS as origin regulator; Centro de los
  Derechos del Migrante + Polaris + Farmworker Justice as NGO
  contacts.
- **VE-CO**: humanitarian / Permiso por Protección Temporal
  framework via Migración Colombia; R4V + Fundación Renacer
  surfaced as cross-border / anti-trafficking contacts.
- **GH-LB / NG-LB** (kafala): Ghanaian Labour Department + NAPTIP
  as origin regulators; ARM Beirut + KAFA as Lebanon-side NGOs;
  reflects high-incidence trafficking patterns documented by
  Anti-Slavery International.
- **SY-DE**: BAMF + Pro Asyl + Caritas + KOK; honest framing that
  the origin side has no functional regulator post-2011.
- **UA-PL**: Polish PIP + La Strada Poland + Helsinki Foundation
  + Right to Protection (Ukraine-side); reflects post-2022
  temporary-protection status.

The corridor inference heuristic in JournalRepository now detects
all twelve corridors from journal text (case-insensitive).

## Tests

Added 5 new unit tests covering the corridor expansion (all 12
present, MX-US zero-fee status, UA-PL zero-fee status, refugee
corridors have destination regulator, new corridors each have
≥ 2 NGO contacts). Existing tests still pass.

## Migration

No data migration needed. The corridor list is read from
`DomainKnowledge.CorridorKnowledge.ALL` at runtime; existing
journals continue to work. If your onboarding picked a corridor
that wasn't in v0.7's list, it will now resolve correctly in v0.8.

## Bumped

versionCode 8 → 9, versionName 0.7.0-quality-and-claims →
0.8.0-twelve-corridors.

## What didn't make it

- **Localized chat UI** for non-English-speaking workers — still
  v0.9 target. Chat surface accepts any Gemma 4 language; only
  the button labels are English.
- **Photo OCR** for contract / receipt / passport scans — still
  v0.9 target.
- **Per-file Tink encryption** for journal attachments — still
  v0.9 target (currently relies on Android sandbox + allowBackup=false).
