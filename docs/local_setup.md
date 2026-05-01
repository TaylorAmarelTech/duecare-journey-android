# Local Setup — Building & Testing the APK

Two paths: **CI build** (no local install required) and **local
Android Studio** (faster iteration loop). Pick one.

---

## Path A — CI build (recommended for first-time testers)

Push the repo to GitHub. The included Actions workflow at
`.github/workflows/build-apk.yml` builds an unsigned debug APK on
every push and uploads it as a downloadable artifact.

### One-time GitHub setup

```bash
cd C:/Users/amare/OneDrive/Documents/duecare-journey-android
git init -b main
git add .
git commit -m "Initial Duecare Journey skeleton"

# Create the remote on GitHub (web UI or gh CLI):
gh repo create TaylorAmarelTech/duecare-journey-android \
    --public --source . --remote origin --push
```

The first push triggers the workflow. Watch it at
`https://github.com/TaylorAmarelTech/duecare-journey-android/actions`.

### Each subsequent change

```bash
git add .
git commit -m "your change"
git push
# wait ~2-3 min for CI (~6-8 min on the first run)
```

### Download the APK

1. Open the Actions tab on GitHub.
2. Click the most recent successful **Build APK** run.
3. Scroll to **Artifacts** at the bottom.
4. Download `duecare-journey-debug-apk.zip`.
5. Unzip — inside is `app-debug.apk`.

### Sideload the APK to your phone

#### One-time: enable USB debugging

- On the phone: **Settings → About phone → Build number** (tap 7
  times). Then **Settings → Developer options → USB debugging** (on).
- Plug the phone into your laptop. Approve the "Allow USB debugging?"
  prompt.

#### One-time: install adb

`adb` is part of the Android Platform Tools. You can either:

- **Option 1 (lighter, recommended):** download just the platform
  tools from
  https://developer.android.com/studio/releases/platform-tools — a
  ~30 MB zip. Extract to `C:\platform-tools\` and add that to your
  PATH.
- **Option 2:** if you install Android Studio later, `adb` lives at
  `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.

Verify install:

```bash
adb version
adb devices    # should list your phone after USB-debug approval
```

#### Each install

```bash
adb install -r app-debug.apk     # -r = replace if already installed
```

(`-r` so reinstalls don't fail with INSTALL_FAILED_ALREADY_EXISTS.)

Open **Duecare Journey** from the phone's launcher.

---

## Path B — Local Android Studio (faster iteration)

If you'll be making more than 2-3 changes, install Android Studio
and skip the CI loop.

### One-time install

1. Download Android Studio Hedgehog (or later) from
   https://developer.android.com/studio. The bundled installer
   includes JDK 17, Gradle 8.5, Android SDK Manager, and an
   x86_64 emulator. Free, ~3 GB on disk.
2. Run the installer. Accept the default install location
   (`C:\Program Files\Android\Android Studio`).
3. First-launch wizard: pick **Standard** install. Studio will
   download the Android 34 SDK + build-tools (another ~2 GB).
4. **File → Open** → point at
   `C:\Users\amare\OneDrive\Documents\duecare-journey-android` →
   **OK**.
5. Studio will run a Gradle sync (~3-5 minutes the first time —
   downloading all the deps from `app/build.gradle.kts`).

### Each subsequent build

- **Run ▶** — installs and launches the app on the connected device
  or emulator.
- **Build → Build Bundle(s) / APK(s) → Build APK(s)** — produces an
  APK at `app\build\outputs\apk\debug\app-debug.apk`.

### Emulator (no physical device needed)

1. **Tools → Device Manager → Create Device.**
2. Pick **Pixel 7** (or any modern phone profile).
3. Pick **System Image: API 34 (Android 14)**. Download if needed.
4. Click **Finish**, then ▶ on the new emulator entry.
5. Click **Run ▶** in Studio — the emulator picks up the install.

---

## Useful tools beyond the basics

### `scrcpy` — mirror your phone to your laptop screen

Invaluable for video capture (the hackathon submission needs a
30-sec mobile demo clip per `gemma4_comp/docs/video_script.md`).
Mirrors your real Pixel screen to your laptop in a window you can
record with OBS.

```bash
# Windows (via scoop)
scoop install scrcpy
# Then with the phone connected via USB:
scrcpy
```

### `adb logcat` — see app logs while the phone runs

```bash
adb logcat | grep duecare
```

Useful when something silently breaks.

### `adb uninstall com.duecare.journey` — clean reinstall

If the app gets in a weird state (e.g., during early MVP work where
the journal schema changes), wipe + reinstall:

```bash
adb uninstall com.duecare.journey
adb install app-debug.apk
```

---

## Troubleshooting

**"INSTALL_FAILED_USER_RESTRICTED"** — Some phones (Xiaomi, Huawei)
require enabling "Install via USB" in Developer Options separately
from "USB debugging". Look for a toggle with that label.

**"INSTALL_FAILED_OLDER_SDK"** — The phone's Android version is
older than `minSdk = 26` (Android 8.0). Upgrade or test on a newer
device.

**Gradle sync fails with "Could not resolve all artifacts"** —
Network proxy issue. Confirm `gradle.properties` doesn't have stale
proxy settings, and that you can reach `dl.google.com` and
`repo1.maven.org`.

**KSP / Hilt code-gen fails on first build** — Try **File →
Invalidate Caches / Restart** in Android Studio. Hilt's annotation
processor occasionally needs a clean run after dep changes.

**APK installs but app crashes on launch** — In v0.1 skeleton this
shouldn't happen, but check `adb logcat` for the stack trace and
file an issue with the trace pasted in.
