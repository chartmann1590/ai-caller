# Model Licenses & Attributions

## 1. Local AI Models

### 1.1 Gemma 4 E2B (Language & Dialogue Model)
- **Model:** Gemma 4 E2B (Edge-to-Bespoke)
- **Provider:** Google LLC
- **License:** Gemma Terms of Use & Gemma Open Model License
- **License Summary:**
  - Free for research and commercial use in accordance with Google's Gemma Prohibited Use Policy.
  - Distribution of model weights requires including the Gemma Terms of Use and retaining original copyright notices.
  - Weights are packaged locally in GGUF or LiteRT-LM format (`.bin` / `.tflite`).

### 1.2 Local Transducer / ASR Acoustic Model
- **Architecture:** Conformer / Emformer Streaming Speech Recognition Transducer (8kHz/16kHz)
- **Runtime:** ONNX Runtime / LiteRT Mobile
- **License:** Apache License 2.0

### 1.3 Streaming Neural TTS
- **Architecture:** Local Low-Latency Neural Acoustic Vocoder
- **Runtime:** ONNX Runtime / Android System TTS Fallback
- **License:** Apache License 2.0

---

## 2. Core Libraries & Dependencies

| Library / Runtime | Provider | License | Purpose |
| :--- | :--- | :--- | :--- |
| **LiteRT-LM (`litertlm-android`)** | Google LLC | Apache License 2.0 | On-device LLM inference acceleration on Tensor NPU/GPU |
| **ONNX Runtime Mobile (`onnxruntime-android`)** | Microsoft Corp. | MIT License | Neural TTS & ASR inference |
| **AndroidX Core & Compose BOM** | Google LLC / AOSP | Apache License 2.0 | Modern Android declarative UI & framework bindings |
| **Kotlin Coroutines & Serialization** | JetBrains s.r.o. | Apache License 2.0 | Asynchronous flow processing and JSON parsing |
| **AndroidX Security Crypto** | Google LLC | Apache License 2.0 | AES-256-GCM Keystore-backed storage |
