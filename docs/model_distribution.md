# Model Distribution — best practices and v0.5 plan

Research findings on how production Android apps distribute large
local LLM models (~1-2 GB), and the concrete v0.5 plan for Duecare
Journey based on those findings.

> Researched 2026-05-01 against Android, MediaPipe, Hugging Face,
> Cloudflare R2, and reference repos (Google AI Edge Gallery,
> PocketPal, MLC LLM, Pocket LLM). All citations linked inline.

## TL;DR

1. **Don't bundle the model in the APK.** Google's MediaPipe LLM
   guide explicitly says so. Play Store caps + alignment quirks make
   it the wrong choice at every level.
2. **Switch from Gemma 2 to Gemma 4 E2B.** Apache 2.0 (no terms-of-
   use plumbing), ungated, anonymous HF Hub download.
3. **Host on Hugging Face Hub primary, Cloudflare R2 fallback.**
   Both anonymous, both free at our scale.
4. **Use WorkManager + OkHttp + Range-resume + SHA-256 verify +
   foreground notification.** Mirror Google AI Edge Gallery's
   `DownloadWorker.kt`.
5. **First-launch UX:** disclose data cost upfront (₱350 on cellular
   for OFW audience), Wi-Fi-only by default, atomic write with
   verification before MediaPipe init.

## A. Why not bundle

Google's [MediaPipe LLM Inference Android guide](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android)
states: *"The model is too large to be bundled in an APK. For
deployment, host the model on a server and download it at runtime."*

Play Store mechanics confirm:

| Constraint | Limit |
|---|---|
| Legacy APK | 100 MB |
| AAB base module | 200 MB |
| Install-time asset pack (single) | 1.5 GB |
| All install-time asset packs combined | 1 GB cumulative |
| On-demand asset pack (each) | 1.5 GB |
| Mobile-data warning shown to user | 200 MB |

Even if a 1.4 GB model fit a single on-demand asset pack, that path
locks us into Play Store distribution (no F-Droid, no APK sideload),
makes every model update a full Play release, and APK assets aren't
4-byte aligned (compression breaks alignment, MediaPipe rejects).

## B. Where to host (the real comparison)

| Option | 1.4 GB? | Egress cost | User auth | Notes |
|---|---|---|---|---|
| **Hugging Face Hub (litert-community)** | ✓ | free | none if ungated | Gemma 4 E2B at [`litert-community/gemma-4-E2B-it-litert-lm`](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm) is **ungated, Apache 2.0**. Anonymous direct download via `resolve/main/<file>`. |
| **Cloudflare R2** | ✓ | $0 egress, $0.015/GB-mo storage | none | Free tier: 10 GB storage + 10M ops/mo. Custom domain via `r2.dev`. Best for self-hosting our own re-quantized variant. |
| **GitHub Releases** | ✓ (2 GB/file cap) | free, no bandwidth limit | none for public repos | Per [GH docs](https://docs.github.com/en/repositories/working-with-files/managing-large-files/about-large-files-on-github), no bandwidth limit. Acceptable but at file-size edge for future variants. |
| **Backblaze B2 + Cloudflare** | ✓ | free egress (Bandwidth Alliance) | none | Cheaper storage than R2. |
| **S3 / GCS** | ✓ | $90/TB egress | none | Don't. Egress costs sink an NGO budget. |
| **Kaggle Models** | ✓ | free | yes (account + dataset accept) | Bad UX for OFWs; account creation friction kills first-launch. |

**Pick: HF Hub primary + R2 fallback.** HEAD requests at first-launch
to pick the live mirror.

## C. Gemma terms-of-use — the actual rules

### Gemma 2 (current Duecare default)

Ships under the [Gemma Terms of Use](https://ai.google.dev/gemma/terms).
Section 1.1(b) defines *"Distribution"* broadly. Section 3.1
requires:

- (a) Ship a `Notice` text file alongside any distribution: *"Gemma is
  provided under and subject to the Gemma Terms of Use found at
  ai.google.dev/gemma/terms."*
- (b) Provide third-party recipients with a copy of the agreement.
- (c) Embed Section 3.2 use restrictions in any agreement governing
  use/distribution.

The terms do **not** require each end user to individually click-
through-accept Gemma TOU when downloading from your CDN. But you must
ship the Notice file alongside the model + embed §3.2 in your app's
EULA + link Gemma TOU in-app.

### Gemma 4 (Duecare v0.5 target)

Ships under [Apache 2.0](https://ai.google.dev/gemma/apache_2). Per
[Google's announcement](https://opensource.googleblog.com/2026/03/gemma-4-expanding-the-gemmaverse-with-apache-20.html),
no use restrictions, no Notice requirement beyond standard Apache
attribution. **Eliminates the entire TOU plumbing problem.**

If we move to Gemma 4 E2B in v0.5 we delete a meaningful chunk of
v0.4 boilerplate (TOU consent flow, Notice file, EULA §3.2 embed,
in-app TOU link).

## D. UX patterns for a 1.4 GB first-launch download

The minimum viable flow:

1. **Pre-install disclosure.** Play Store description first paragraph
   names the 1.4 GB download. F-Droid description likewise. Play
   already auto-warns on cellular for any APK >200 MB; pre-disclosure
   prevents 1-star "tricked me into using my data" reviews.
2. **Storage check** (`StatFs.getAvailableBytes()`) requiring ~3 GB
   free (1.4 GB model + ~1.4 GB headroom for KV cache + atomic
   write).
3. **Wi-Fi-only by default**, opt-in cellular toggle.

   ```kotlin
   Constraints.Builder()
       .setRequiredNetworkType(NetworkType.UNMETERED)
       .setRequiresStorageNotLow(true)
       .setRequiresBatteryNotLow(true)
       .build()
   ```

4. **Background-tolerant download via WorkManager + OkHttp + Range
   resume.** Don't use `DownloadManager` — it's in maintenance, no
   progress observable from Compose, can't update mid-download. The
   [AI Edge Gallery DownloadWorker](https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/worker/DownloadWorker.kt)
   is the reference implementation. Khushpanchal's [Ketch](https://github.com/khushpanchal/Ketch)
   library is a drop-in alternative.
5. **Atomic write.** Download to `model.litertlm.part`, SHA-256
   verify against a hash shipped in the APK, then `File.renameTo()`.
   A partial file at the canonical path causes MediaPipe to fail
   with the obscure "Invalid Flatbuffer" / "buffer not 4-byte
   aligned" errors documented in
   [mediapipe#6093](https://github.com/google-ai-edge/mediapipe/issues/6093)
   and [#5825](https://github.com/google-ai-edge/mediapipe/issues/5825).
6. **Foreground service notification** with progress bar (mandatory
   on Android 14+ for long-running downloads, otherwise WorkManager
   gets killed).
7. **Resume on next launch** if user backgrounded — WorkManager
   handles this for free if the worker is `setExpedited` + `KEEP`
   policy.
8. **Show data cost upfront.** "1.4 GB download — about ₱350 on Globe
   prepaid, free on Wi-Fi" is a load-bearing disclosure for the OFW
   audience.
9. **Update strategy.** Ship a `model_manifest.json` next to the
   binary on the host (`{"version": "0.5.0", "sha256": "...", "url":
   "..."}`). Check on launch via WorkManager periodic worker. Old
   model stays usable until new one verifies; only then atomic swap +
   `delete()` the old.

## E. MediaPipe format gotchas

| Format | What it is | When to use |
|---|---|---|
| `.task` | MediaPipe self-contained bundle (LiteRT model + tokenizer + metadata) | Current default for Gemma 2/3 |
| `.litertlm` | Newer LiteRT-LM bundle | Replacing `.task` for Gemma 4. MediaPipe LLM 0.10.24+ accepts both. |
| `.bin` | Raw weight file from older Gemma | Not directly loadable; needs `ai-edge-torch` conversion |

**INT4 vs INT8 vs FP16 on Android:** Gemma 3 1B INT4 hits 2585 tok/s
prefill on flagships via [KleidiAI/XNNPACK](https://learn.arm.com/learning-paths/mobile-graphics-and-gaming/kleidiai-on-android-with-mediapipe-and-xnnpack/3-benchmark-gemma-i8mm/).
For 2B-class models on mid-range OFW phones (8 GB RAM), expect
10-20 tok/s decode. **INT4 mandatory for usability on the OFW target
hardware.** INT8 doubles size with marginal quality gain. FP16 is
unusable on phones.

**Failure modes are silent.** MediaPipe surfaces "Invalid
Flatbuffer" for a corrupted / partial / wrong-format file with no
structured error code. Always SHA-256 verify before calling
`LlmInference.createFromOptions()`.

**4-byte alignment**: the file on disk must be 4-byte aligned in
length. Asset packs and OkHttp resume both preserve this; APK assets
do NOT (compression breaks alignment).

## F. Three real-world apps that ship this exact pattern

1. **[Google AI Edge Gallery](https://github.com/google-ai-edge/gallery)**
   (Apache 2.0, Google-published reference). APK ~50 MB, downloads
   `.task`/`.litertlm` from Hugging Face. Uses `DownloadWorker.kt`
   (WorkManager + foreground service). For Gemma models, downloads
   from `litert-community` are anonymous post-OAuth-handshake.

2. **[PocketPal AI](https://github.com/a-ghorbani/pocketpal-ai)**
   (MIT). React Native, llama.cpp backend. Downloads from Hugging
   Face Hub directly; HF token only needed for gated models.
   Background downloads, in-app model browser, supports multiple
   concurrent models with explicit storage management.

3. **[MLC Chat](https://github.com/mlc-ai/mlc-llm)** (Apache 2.0).
   112 MB APK, model list shown on first launch, downloads from
   MLC's own HF Hub repos (`mlc-ai/...`). No auth. Models are TVM-
   compiled `.so` + tokenizer artifacts — same hosting pattern as
   `litert-community` for MediaPipe.

Honorable mention: **[local-llms-on-android](https://github.com/dineshsoudagar/local-llms-on-android)**
— closer architectural analog to Duecare since it's specifically
LiteRT + ONNX + Gemma 4, single-developer.

## v0.5 architecture (concrete plan)

1. **Move model target to Gemma 4 E2B** (~1.5 GB INT4 `.litertlm`).
   Eliminates Gemma TOU plumbing — Apache 2.0, ungated, available at
   `litert-community/gemma-4-E2B-it-litert-lm`.
2. **Distribution channels:** APK on GitHub Releases (NGO direct
   install) + Play Store (OFW reach) + F-Droid (privacy-conscious).
3. **Model host:**
   - Primary: `https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/<file>`
   - Fallback: `https://models.duecare.org/...` on Cloudflare R2
     (custom domain, $0 egress).
4. **Download stack:** WorkManager + OkHttp + Range resume +
   foreground notification + SHA-256 verify + atomic rename.
   ~300 lines, model after `DownloadWorker.kt` from AI Edge Gallery.
5. **First-launch UX:** consent screen → storage check → "1.4 GB
   Wi-Fi download, takes ~10 min on home WiFi, ~₱350 on cellular"
   → start download → app fully usable in journal-only mode while
   download runs.
6. **Update channel:** `model_manifest.json` on R2, checked weekly
   via WorkManager periodic worker.

## Sources

- [MediaPipe LLM Inference Android guide](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android)
- [Google Play app size limits](https://support.google.com/googleplay/android-developer/answer/9859372)
- [Play Asset Delivery docs](https://developer.android.com/guide/playcore/asset-delivery)
- [Gemma Terms of Use](https://ai.google.dev/gemma/terms)
- [Gemma 4 Apache 2.0 license](https://ai.google.dev/gemma/apache_2)
- [Google Open Source blog: Gemma 4 under Apache 2.0](https://opensource.googleblog.com/2026/03/gemma-4-expanding-the-gemmaverse-with-apache-20.html)
- [litert-community on Hugging Face](https://huggingface.co/litert-community)
- [Gemma 4 E2B litert-lm model card (ungated, Apache 2.0)](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm)
- [Gemma 2 2B IT model card (gated, Gemma TOU)](https://huggingface.co/litert-community/Gemma2-2B-IT)
- [Hugging Face Hub rate limits](https://huggingface.co/docs/hub/rate-limits)
- [GitHub: large files docs (no bandwidth limit on releases)](https://docs.github.com/en/repositories/working-with-files/managing-large-files/about-large-files-on-github)
- [Cloudflare R2 docs (zero egress)](https://developers.cloudflare.com/r2/)
- [Google AI Edge Gallery repo](https://github.com/google-ai-edge/gallery)
- [AI Edge Gallery DownloadWorker.kt](https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/worker/DownloadWorker.kt)
- [PocketPal AI repo](https://github.com/a-ghorbani/pocketpal-ai)
- [MLC LLM repo](https://github.com/mlc-ai/mlc-llm)
- [Pocket LLM repo](https://github.com/dineshsoudagar/local-llms-on-android)
- [Ketch (WorkManager downloader library)](https://github.com/khushpanchal/Ketch)
- [Convert HuggingFace Safetensors to MediaPipe Task format](https://ai.google.dev/gemma/docs/conversions/hf-to-mediapipe-task)
- [MediaPipe issue #6093: Invalid Flatbuffer](https://github.com/google-ai-edge/mediapipe/issues/6093)
- [MediaPipe issue #5825: LLMInference Task crash](https://github.com/google-ai-edge/mediapipe/issues/5825)
- [Arm KleidiAI Gemma benchmarks](https://learn.arm.com/learning-paths/mobile-graphics-and-gaming/kleidiai-on-android-with-mediapipe-and-xnnpack/3-benchmark-gemma-i8mm/)
