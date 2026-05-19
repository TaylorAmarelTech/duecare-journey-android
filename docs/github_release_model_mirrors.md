# GitHub Release Model Mirrors

Do not commit Gemma model weights to normal git history. Use GitHub
Releases only as an optional mirror or smoke-test source; Hugging Face
Hub or Cloudflare R2 should remain the production source for multi-GB
Gemma 4 files.

The app already has fallback URLs under this release tag:

```text
https://github.com/TaylorAmarelTech/duecare-journey-android/releases/download/models-v1/gemma4-e2b-web.task
https://github.com/TaylorAmarelTech/duecare-journey-android/releases/download/models-v1/gemma4-e2b.litertlm
https://github.com/TaylorAmarelTech/duecare-journey-android/releases/download/models-v1/gemma4-e4b-web.task
https://github.com/TaylorAmarelTech/duecare-journey-android/releases/download/models-v1/gemma4-e4b.litertlm
```

To populate the mirror after authenticating `gh`:

```powershell
gh auth login -h github.com
gh release create models-v1 --title "Model mirrors v1" --notes "Optional DueCare Android model mirrors."
gh release upload models-v1 .\gemma-4-E2B-it-web.task --clobber
```

For a downloader smoke test, upload a generated probe asset to the same
release instead of putting it in the repository:

```powershell
$path = ".\duecare-download-probe-128mb.bin"
$bytes = New-Object byte[] (128MB)
$rng = [System.Random]::new(42)
$rng.NextBytes($bytes)
[System.IO.File]::WriteAllBytes($path, $bytes)
Get-FileHash $path -Algorithm SHA256
gh release upload models-v1 $path --clobber
```

Then paste the release asset URL into Settings > Use a custom download
URL only for network testing. For a real model test, use a valid
`.task` or `.litertlm` file because the app rejects undersized or
non-model artifacts before MediaPipe loads them.
