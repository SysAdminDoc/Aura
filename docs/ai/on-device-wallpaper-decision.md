# On-device Wallpaper Generation Decision Gate

## Decision

Status: hold.

Aura should not implement on-device wallpaper generation until the criteria in
`docs/ai/on-device-wallpaper-decision.json` are met with evidence. Stability
BYO-key generation remains the supported wallpaper generation path.

## Current Evidence

- Local Dream proves that Stable Diffusion can run on Android with Snapdragon
  NPU acceleration, but its current README narrows NPU support: SD1.5 requires
  Snapdragon Hexagon V68 or newer, SDXL requires Snapdragon 8 Gen 3 or newer,
  and unsupported chips fall back to CPU/GPU.
- Qualcomm demonstrated Stable Diffusion 1.5 on Snapdragon 8 Gen 2 in 2023
  after quantization, compilation, and hardware acceleration, with a 512x512
  20-step generation under 15 seconds.
- Qualcomm's later NPU material shows much faster demo results on newer
  Snapdragon platforms, but also emphasizes that software access and real
  application measurements matter more than raw TOPS.
- Google's LiteRT Android docs now expose a `CompiledModel` API for CPU/GPU/NPU
  inference and list Android API 23 support for LiteRT 2.1.5, but the NPU guide
  requires API 31+, arm64-v8a, Play delivery for AI packs/runtime libraries,
  and per-vendor compatibility handling.
- Android AICore/Gemini Nano keeps prompts local and provides system-level
  safety/privacy properties, but it is not an image-generation API replacement
  for Aura's wallpaper feature.

## Required Evidence Before Implementation

- Device baseline: API level, ABI, RAM/storage floor, SoC/NPU matrix, and
  user-visible unsupported-device behavior.
- Model delivery: artifact sizes, on-demand delivery behavior, free-space guard,
  cache eviction, and backup exclusion.
- Performance: latency, battery, thermal, cancellation, and repeated-generation
  results from at least three real devices.
- Licensing: model redistribution, native runtime, notice, and FOSS-channel
  review.
- Safety: prompt/output filtering, generated-content reporting, unsafe-output
  deletion, and retention behavior.
- User choice: explicit hosted/on-device mode selection with no automatic large
  download and no hidden Stability credit spend.

## Enforcement

`tools/on_device_ai_decision_check.py` validates this decision packet and scans
production build/source paths for early on-device generation dependencies or
model artifacts while the decision remains on hold.

## Sources

- Local Dream: https://github.com/xororz/local-dream
- Qualcomm Stable Diffusion Android demo: https://www.qualcomm.com/news/onq/2023/02/worlds-first-on-device-demonstration-of-stable-diffusion-on-android
- Qualcomm on-device generative AI whitepaper: https://www.qualcomm.com/content/dam/qcomm-martech/dm-assets/documents/Unlocking-on-device-generative-AI-with-an-NPU-and-heterogeneous-computing.pdf
- Google LiteRT Android: https://developers.google.com/edge/litert/android
- Google LiteRT NPU acceleration: https://developers.google.com/edge/litert/next/npu
- Android Gemini Nano/AICore: https://developer.android.com/ai/gemini-nano
