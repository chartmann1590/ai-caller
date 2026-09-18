# Hardware Compatibility & Device Qualification Matrix

## 1. Hardware Requirements

LocalCallAgent executes full conversational AI (Speech Recognition, Gemma 4 E2B Language Model, and Neural Speech Synthesis) on-device without cloud compute offloading. The device must meet the following hardware criteria:

| Hardware Attribute | Minimum Requirement | Recommended Specification |
| :--- | :--- | :--- |
| **CPU Architecture** | ARM64 (`arm64-v8a`) only | Modern ARMv9 64-bit cores |
| **Operating System** | Android 12 (API 31) | Android 14+ (Verified on Android 17 / Pixel 8 Pro) |
| **Physical RAM** | **8 GB RAM** | **12 GB+ LPDDR5X RAM** |
| **Available Storage** | **6 GB Free Storage** | **10 GB+ UFS 3.1/4.0 Storage** |
| **Hardware Accelerator** | OpenCL / Vulkan GPU | Dedicated NPU (Tensor TPU / Hexagon NPU) |
| **Battery Guard** | > 15% charge or charging | > 30% charge |
| **Network** | Wi-Fi or LTE/5G data | Low-latency Wi-Fi or 5G connection for SIP |

---

## 2. Validated Devices & Chipsets

### 2.1 Tier 1: Fully Qualified (Optimal Experience)
- **Google Pixel 8 Pro / Pixel 9 Pro:**
  - **SoC:** Google Tensor G3 / G4
  - **Memory:** 12 GB / 16 GB LPDDR5X
  - **Inference Engine:** Tensor TPU / Mali-G715 Immortalis GPU via LiteRT-LM
  - **Benchmark Composite Score:** 92/100
  - **Barge-in Latency:** < 110ms
- **Samsung Galaxy S24 Ultra / S23 Ultra:**
  - **SoC:** Qualcomm Snapdragon 8 Gen 3 / Gen 2 for Galaxy
  - **Memory:** 12 GB RAM
  - **Inference Engine:** Hexagon NPU / Adreno 750 via LiteRT-LM & QNN
  - **Benchmark Composite Score:** 94/100

### 2.2 Tier 2: Qualified (Standard Performance)
- **Google Pixel 8 / Pixel 7 Pro:**
  - **SoC:** Google Tensor G3 / G2 (8 GB / 12 GB RAM)
  - **Composite Score:** 84/100
- **OnePlus 12 / 11:**
  - **SoC:** Snapdragon 8 Gen 3 / Gen 2
  - **Composite Score:** 88/100

### 2.3 Disqualified Devices
- Any 32-bit ARM (`armeabi-v7a`) or x86 device.
- Devices with less than 6 GB physical RAM (insufficient memory to map model weights alongside Android OS framework).
- Devices with less than 4 GB available storage.

---

## 3. On-Device Performance Benchmark Thresholds

During onboarding, the app executes `BenchmarkRunner` to determine real-world hardware suitability:

```text
┌────────────────────────────────────────────────────────────┐
│                    Benchmark Metrics                       │
├───────────────────────┬──────────────┬─────────────────────┤
│ Metric                │ Minimum Pass │ Target              │
├───────────────────────┼──────────────┼─────────────────────┤
│ Time to First Token   │ < 800ms      │ < 400ms             │
│ LLM Generation Speed  │ > 12 tok/s   │ > 22 tok/s          │
│ ASR Real-Time Factor  │ < 0.45 RTF   │ < 0.25 RTF          │
│ TTS Chunk Latency     │ < 220ms      │ < 140ms             │
│ Composite Score       │ >= 75 / 100  │ >= 85 / 100         │
└───────────────────────┴──────────────┴─────────────────────┘
```
If the composite score falls below 70/100, autonomous calling is gated to prevent poor conversational latency, and the user is advised to use assisted/guided calling mode.


## Emulator / x86_64 note

Android Emulator (`x86_64`) can run the SIP lab and the **pipeline** on-device models
(ASR/TTS/LLM LCAM packages). Full Gemma 4 E2B LiteRT-LM inference targets **arm64-v8a**
devices with NPU/GPU. On emu, dialogue uses the on-device pipeline +
`DeterministicFallbackModel` while still driving real SIP audio send/receive.
