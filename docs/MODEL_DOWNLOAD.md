# On-device model download

LocalCallAgent keeps **all ASR / LLM / TTS weights on device**. Model bytes may be
fetched once during onboarding; call audio and transcripts never leave the phone.

## What gets installed

| Install name | Source | Role |
|---|---|---|
| `asr.onnx` | Asset `models/asr_pipeline_v1.bin` (LCAM pipeline) or production transducer | Streaming ASR |
| `tts.onnx` | Asset `models/tts_pipeline_v1.bin` | Streaming TTS |
| `llm.litertlm` | Asset `models/llm_pipeline_v1.bin` | On-device dialogue |
| `gemma.tflite` / `gemma.bin` (optional) | `model.download.base.url` in `local.properties` | Full Gemma 4 E2B via LiteRT-LM |

Pipeline packages are tiny (~100–200 bytes) LCAM blobs used to **exercise the real
listen → reason → talk path** on emulators and CI. They are SHA-256 verified on unpack
(`OnDeviceModelManager`). Full Gemma weights are multi‑GB and require arm64 + NPU/GPU;
x86_64 emulators use the pipeline package and `DeterministicFallbackModel` for dialogue.

## Configure optional Gemma URL

In `local.properties` (never commit secrets or huge binaries):

```properties
model.download.base.url=https://example.invalid/gemma-4-e2b.tflite
```

`BuildConfig.MODEL_DOWNLOAD_BASE_URL` is injected at build time. If unset, only asset
unpack runs (offline-friendly).

## Integrity

Each catalog entry includes `sha256`. Partial files use `*.partial` and are renamed
atomically after verification. Corruption → `MODEL_DOWNLOAD_FAILED` (no silent use).

## UX

Onboarding shows progress + **Download On-Device Models**. `MainViewModel.downloadModels()`
also auto-starts when models are missing.

## Emulator hardware limits

- Nested KVM: start emulator with `-accel off -no-window -gpu swiftshader_indirect`.
- Full Gemma LiteRT-LM is not expected to run on x86_64 emu; pipeline + deterministic
  on-device dialogue still proves SIP RTP → ASR → LLM → TTS → `sendAudio`.
