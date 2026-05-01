# Cross-Repo Relationship — duecare-journey-android ↔ gemma4_comp

This repo is a **separate-but-related** sibling of the main Duecare
project at [`gemma4_comp`](https://github.com/TaylorAmarelTech/gemma4_comp).
The split is intentional. This doc explains what lives where, why,
and how updates flow.

## Why a separate repo

Three reasons:

1. **Workflow divergence.** Android development uses Gradle, Kotlin,
   Android Studio, Android SDK manager, AVD images, KSP, and
   Hilt-codegen — none of which the parent project's Python +
   FastAPI + Kaggle workflow needs. A monorepo would slow Python
   CI by ~5 minutes per build (Android cache warm) and slow Android
   IDE indexing by hundreds of irrelevant Python files.
2. **Distribution channel divergence.** Android apps publish via
   F-Droid, Play Store (eventually), or sideload APK. The parent
   project publishes via PyPI, Kaggle Datasets, HF Spaces, GitHub
   Pages. Different release pipelines, different signing keys,
   different versioning cadences.
3. **Contributor audience divergence.** A mobile dev should be able
   to `git clone` and `./gradlew assembleDebug` without first
   understanding the trafficking benchmark / rubric / harness
   research. Conversely, a Python contributor adding a new GREP rule
   should not need to know Kotlin.

## Source-of-truth boundary

| Asset | Source-of-truth repo | Mirror in this repo |
|---|---|---|
| Architecture vision + 4-layer design | this repo (`docs/architecture.md`) | parent has stub linking back |
| GREP rules (37) | parent (`packages/duecare-llm-chat/src/duecare/chat/harness/__init__.py`) | Kotlin port at `app/src/main/java/com/duecare/journey/harness/GrepRules.kt` (regenerated via codegen) |
| RAG corpus (26 docs) | parent (same file as above) | Kotlin port at `harness/RagCorpus.kt` |
| Tool catalogs (corridor caps, NGO hotlines, etc.) | parent (same file) | Kotlin port at `harness/Tools.kt` |
| 5-tier rubrics + required-element rubrics | parent (`harness/_rubrics_5tier.json`, `_rubrics_required.json`) | NOT mirrored — the Android app doesn't grade itself; the rubrics are server/notebook concerns |
| 394 example prompts | parent (`harness/_examples.json`) | NOT mirrored — Android workflow doesn't use them |
| `legal_citation_quality` rubric criteria | parent | NOT mirrored — same reason |
| LiteRT model file (`gemma-4-e2b-it.task`) | published to HF Hub by parent's `kaggle/bench-and-tune/` notebook | downloaded on first launch by `LiteRTGemmaEngine.ensureModelDownloaded()` |
| Mobile-responsive web chat CSS | parent (`packages/duecare-llm-chat/src/duecare/chat/static/index.html`) | NOT mirrored — that's the web companion, the Android app has native Compose UI |

## Update flow: when a new GREP rule is added

The most common cross-repo update — a Python contributor adds a new
rule to the parent's harness:

1. Edit `packages/duecare-llm-chat/src/duecare/chat/harness/__init__.py`
   in the parent repo, append the new rule to `GREP_RULES`.
2. PR + merge to parent's master. Parent CI runs the rubric
   validator + harness lift report.
3. Run the codegen (planned for v1 MVP):

   ```bash
   # in this repo
   python scripts/sync_harness_from_parent.py \
       --parent ../gemma4_comp
   ```

   This script reads `GREP_RULES` from the parent's Python source
   and writes the equivalent Kotlin object literal into
   `app/src/main/java/com/duecare/journey/harness/GrepRules.kt`,
   preserving all severity / citation / indicator fields.

4. PR + merge to this repo. CI builds a new APK with the new rule.

The codegen approach keeps the parent's Python the source of truth
(only one place to edit a rule) without forcing the Android repo to
import Python at runtime.

## Update flow: when the LiteRT model changes

1. Parent's `kaggle/bench-and-tune/` notebook runs (Unsloth SFT/DPO
   on Gemma 4 E4B → quant → AI Edge Torch conversion → publish to
   HF Hub).
2. Push a new tag (e.g., `litert-v0.2.0`) to HF Hub at
   `taylorscottamarel/Duecare-Gemma-4-E2B-LiteRT-v0.2.0`.
3. Bump `LITERT_MODEL_VERSION` constant in
   `app/src/main/java/com/duecare/journey/inference/LiteRTGemmaEngine.kt`.
4. PR + merge. New APK build downloads the new model on next
   first-launch (cached locally; small delta for incremental updates
   tracked as a v2 enhancement).

## What's intentionally NOT shared

- **No transitive dependencies.** This repo doesn't depend on the
  parent's PyPI packages. It can build and run with the parent
  repo deleted from disk.
- **No shared CI.** Parent's CI runs Python validators + corpus
  checks. This repo's CI runs `./gradlew assembleDebug`. They never
  call into each other.
- **No shared issue tracker (yet).** File Android-specific bugs in
  this repo's Issues; file research / corpus / harness bugs in the
  parent.
- **No shared release cadence.** Parent moves at hackathon /
  research pace; this repo moves at mobile-app pace (weekly internal
  builds, monthly external releases once v1 lands).

## What if I'm working on both at once?

Day-to-day during initial v1 MVP development, the typical setup is:

```
~/Documents/
├── gemma4_comp/                   <- parent: clone this when working on rules/RAG
└── duecare-journey-android/       <- this repo: clone this when working on the app
```

Set them as siblings. The codegen script above expects this layout
by default (`--parent ../gemma4_comp`). VS Code multi-root workspace
covers both:

```jsonc
// duecare-fullstack.code-workspace
{
  "folders": [
    { "path": "../gemma4_comp" },
    { "path": "." }
  ]
}
```

## Migration plan if we ever want to merge them back

The split is reversible. To merge this repo back into `gemma4_comp/android/`:

```bash
cd gemma4_comp
git remote add android ../duecare-journey-android
git fetch android
git read-tree --prefix=android/ -u android/main
git commit -m "Merge duecare-journey-android into monorepo"
```

Don't do this lightly — the workflow divergence reasons above are
real. But the option exists.
