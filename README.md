# Duecare Journey — Android

The on-device companion to [Duecare](https://github.com/TaylorAmarelTech/gemma4_comp).
A pocket-sized, zero-connectivity legal companion for migrant workers,
running Gemma 4 E2B entirely on-device via LiteRT.

> **Status:** v0.1 skeleton. Build a debug APK via the included
> GitHub Actions workflow (no local Android Studio required) or
> open the project in Android Studio Hedgehog (2023.1+). v1 MVP
> targets the week of 2026-05-19 (post Gemma 4 Good Hackathon).

## What it is

A migrant worker's journey is a sequence of decisions, each one
exploitable. Today the worker has two options for legal advice:
ask a frontier-API LLM (which sends every detail to a third party)
or use a mobile-responsive web UI (which requires connectivity their
employer often controls).

**Duecare Journey is the third option.** Gemma 4 runs on the worker's
phone. The journal of what happened — recruiter messages, fee
receipts, contract photos, incident notes — lives encrypted on the
worker's phone, with the encryption key in Android Keystore. When
something goes wrong, one tap generates a structured complaint
packet PDF the worker can send to POEA, BMET, MfMW HK, IJM, or
their embassy attaché.

The four layers (full architecture in
[`docs/architecture.md`](./docs/architecture.md)):

1. **Inference** — LiteRT Gemma 4 E2B + the bundled GREP/RAG/Tools
   harness from the parent project (37 GREP rules, 26 RAG docs,
   4 lookups).
2. **Journal** — SQLCipher-encrypted Room DB of stage-tagged events.
3. **Advice** — Compose chat UI that injects journal context into
   every prompt so Gemma's answers are journey-aware.
4. **Export** — One-tap "Generate complaint packet" → PDF +
   recommended NGO + draft delivery message + Android share intent.

## How to get a working APK without installing anything locally

This repo includes a GitHub Actions workflow that builds the debug
APK on every push. **You don't need a local Android Studio install.**

1. Push this repo to GitHub:

   ```bash
   git remote add origin https://github.com/TaylorAmarelTech/duecare-journey-android.git
   git push -u origin main
   ```

2. Wait ~6-8 minutes for the **Build APK** workflow to finish
   (Actions tab on the repo page).

3. From the workflow run page, scroll to the **Artifacts** section
   and download `duecare-journey-debug-apk`.

4. Sideload to a connected Android device (see
   [`docs/local_setup.md`](./docs/local_setup.md) for `adb` install
   on Windows):

   ```bash
   unzip duecare-journey-debug-apk.zip
   adb install app-debug.apk
   ```

5. Open **Duecare Journey** from the launcher. The skeleton shows
   a 4-tab nav with placeholder content — confirms the build path
   works. v1 MVP fills in the real screens.

Subsequent CI builds take ~2-3 minutes (dep cache warm).

## Local development (optional)

If you want to iterate faster than the CI loop:

- Install **Android Studio Hedgehog** (2023.1+) — bundles JDK 17,
  Android SDK 34, Gradle 8.5. Free, ~3 GB.
- Open `duecare-journey-android/` in Studio.
- Connect a phone via USB (Developer Options → USB debugging),
  or launch an emulator (Tools → Device Manager).
- Click **Run** ▶ — Studio installs to the connected device.

Step-by-step setup with screenshots: [`docs/local_setup.md`](./docs/local_setup.md).

## Cross-repo relationship

Duecare Journey is intentionally a **separate repo** from the parent
[gemma4_comp](https://github.com/TaylorAmarelTech/gemma4_comp), but
shares meaningful design + data with it. See
[`docs/cross_repo_relationship.md`](./docs/cross_repo_relationship.md)
for the boundary, what's source-of-truth where, and how rule / corpus
updates flow between repos.

## Repo layout

```
duecare-journey-android/
├── README.md                    <- this file
├── settings.gradle.kts
├── build.gradle.kts             <- root build file
├── gradle.properties
├── gradlew, gradlew.bat         <- wrapper scripts
├── gradle/wrapper/              <- wrapper config
├── .github/workflows/
│   └── build-apk.yml            <- CI: build debug APK on every push
├── docs/
│   ├── architecture.md          <- full design (4-layer breakdown)
│   ├── local_setup.md           <- Android Studio install + adb
│   ├── cross_repo_relationship.md  <- boundary with gemma4_comp
│   └── adr/                     <- architectural decision records
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/duecare/journey/
        │   ├── MainActivity.kt
        │   ├── DuecareJourneyApp.kt          <- @HiltAndroidApp
        │   ├── di/AppModule.kt               <- Hilt bindings
        │   ├── inference/                    <- GemmaInferenceEngine + LiteRT impl
        │   ├── journal/                      <- Room + SQLCipher
        │   ├── advice/                       <- chat + journal context injection
        │   ├── export/                       <- complaint-packet PDF
        │   ├── harness/                      <- GREP/RAG/Tools port from Python
        │   └── ui/theme/
        └── res/
            ├── values/
            └── xml/
```

## License

MIT. Same license as the parent project.

## Built with

[Google Gemma 4](https://huggingface.co/google/gemma-4-e2b-it).
Used in accordance with the [Gemma Terms of Use](https://ai.google.dev/gemma/terms).
