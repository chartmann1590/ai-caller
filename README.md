# LocalCallAgent (AI Caller)

A privacy-first, fully local, on-device autonomous AI phone-calling client for Android.

LocalCallAgent executes complete conversational telephone calls—including Voice Activity Detection (VAD), Streaming Automatic Speech Recognition (ASR), Language Understanding & Policy Reasoning (Gemma 4 E2B via LiteRT-LM), and Low-Latency Neural Text-to-Speech (TTS)—**100% locally on your smartphone hardware**. No voice data, transcripts, or personal credentials ever leave your device.

---

## Architecture Overview

```text
                                PHYSICAL PHONE (Pixel 8 Pro)
 ┌─────────────────────────────────────────────────────────────────────────────┐
 │                                                                             │
 │  SIP / VoIP Gateway ◄═════► App-Owned SIP Engine (RFC 3261 / RFC 3550 RTP)  │
 │                                    │                                        │
 │                         G.711 / Jitter Buffer                               │
 │                                    │                                        │
 │                                    ▼                                        │
 │                        Local Voice Activity Detector                        │
 │                        (RMS Energy + Barge-In Guard)                        │
 │                                    │                                        │
 │                                    ▼                                        │
 │                         Streaming Conformer ASR                             │
 │                        (Local Transducer 8/16kHz)                           │
 │                                    │                                        │
 │                                    ▼                                        │
 │                      Gemma 4 E2B / LiteRT-LM Engine                         │
 │                         (NPU / GPU Acceleration)                            │
 │                                    │                                        │
 │                                    ▼                                        │
 │                        Deterministic Safety Validator                       │
 │                    (Zero Unauthorized Financial Actions)                    │
 │                                    │                                        │
 │                                    ▼                                        │
 │                        Streaming Neural TTS Engine                          │
 │                       (<150ms Barge-in Cancellation)                        │
 │                                    │                                        │
 │                                    ▼                                        │
 │                        Encrypted Local Storage                              │
 │                      (AES-256-GCM / Titan M2 / Redactor)                    │
 │                                                                             │
 └─────────────────────────────────────────────────────────────────────────────┘
```

---

## Key Modules

- **`:core-model`**: Domain data contracts (`PcmFrame`, `AppCallState`, `CallObjective`, `StructuredCallResult`, `AgentDecision`, `SipAccountConfig`).
- **`:core-privacy`**: Zero-trust privacy primitives (`Redactor` with Luhn credit card validation, `PrivacyAudit`, `NetworkPolicyEnforcer`, `LocalEncryptedTranscriptStore` with AES-GCM).
- **`:telephony-api`**: Common telephony transport abstractions (`CallTransport`, `PrivilegedCarrierMediaBridge`).
- **`:telephony-pstn`**: Cellular carrier call control (`InCallService`, `CallScreeningService`, `DialerRoleManager`). Explicitly declares `supportsProgrammaticMedia = false`.
- **`:telephony-sip`**: Self-contained SIP/RTP calling stack (`SipEngine`, RFC 3261 Digest MD5, G.711 PCMU/PCMA, RFC 2833 DTMF, `JitterBuffer`, Telecom `ConnectionService`).
- **`:audio-core`**: Audio DSP utilities (`PcmResampler`, `VadDetector`, `BoundedAudioQueue`, `AudioRouter` for bot vs human takeover).
- **`:asr-local`**: Local streaming speech recognition (`StreamingAsr`, `LocalTransducerAsr`, `RuleSlotExtractor`, `CriticalSlotValidator`).
- **`:tts-local`**: Local streaming neural speech synthesis (`LocalStreamingNeuralTts`, `AndroidOfflineTtsFallback`).
- **`:llm-litert`**: On-device language model reasoning (`LiteRtLmGemmaModel` for Gemma 4 E2B, `DeterministicFallbackModel`).
- **`:agent-orchestrator`**: Central call coordination (`ConversationController`, `SafetyValidator`, instant human takeover).
- **`:benchmark`**: Device hardware qualification suite (`BenchmarkRunner`, `DeviceCapabilityRepository`, CSV reporting).
- **`:test-fixtures`**: Comprehensive test harness with 320 deterministic business calling scenarios.
- **`:app`**: Jetpack Compose application featuring Onboarding, Task Creation, Live In-Call Dashboard, and Result Summaries.

---

## Product Flavors

| Flavor | Primary Transport | Permissions | Purpose |
| :--- | :--- | :--- | :--- |
| **`sipAgent`** | SIP / RTP / ConnectionService | `INTERNET`, `RECORD_AUDIO`, `MANAGE_OWN_CALLS` | Complete autonomous AI calling over standard VoIP |
| **`pstnControl`** | Cellular `InCallService` | Call screening & dialer permissions (**NO `INTERNET`**) | Cellular call management & caller screening |

---

## Build & Verification

### Build Flavors
```bash
# Build SIP Autonomous Agent APK
./gradlew :app:assembleSipAgentDebug

# Build PSTN Call Screening APK (Guaranteed Zero Network)
./gradlew :app:assemblePstnControlDebug
```

### Privacy Manifest Audit
```bash
./gradlew :app:verifyPrivacyManifest
```
Fails the build if `CAPTURE_AUDIO_OUTPUT` is detected anywhere in the merged manifests, or if `pstnControl` contains `android.permission.INTERNET`.

### Run Test Suite (320 Scenarios)
```bash
./gradlew :test-fixtures:test
```

### Deploy to Connected Device
```bash
adb install -r app/build/outputs/apk/sipAgent/debug/app-sipAgent-debug.apk
```

---

## Documentation

- [Android Cellular Audio Limitations](docs/ANDROID_CELLULAR_AUDIO_LIMITATION.md)
- [Privacy Architecture & Security Model](docs/PRIVACY_MODEL.md)
- [Threat Model & Security Architecture](docs/THREAT_MODEL.md)
- [Hardware & Device Qualification Matrix](docs/DEVICE_SUPPORT.md)
- [Model Licenses & Attributions](docs/MODEL_LICENSES.md)
- [Telephony Legal Review Checklist](docs/LEGAL_REVIEW_CHECKLIST.md)
