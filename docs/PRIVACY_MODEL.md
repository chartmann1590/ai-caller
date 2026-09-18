# Privacy Architecture & Security Model

## Core Principle: Zero Cloud Telephony AI

LocalCallAgent is engineered with a strict **Local-First, Privacy-Zero-Trust** security philosophy. Conversational audio, speech recognition transcripts, language model reasoning, and speech synthesis are computed **100% locally on the device hardware**.

```text
                                 DEVICE BOUNDARY
 ┌─────────────────────────────────────────────────────────────────────────┐
 │                                                                         │
 │   Incoming Audio (RTP) ──► Local VAD ──► Streaming ASR (Local)          │
 │                                                  │                      │
 │                                                  ▼                      │
 │                                          Gemma 4 E2B (LiteRT-LM)        │
 │                                                  │                      │
 │                                                  ▼                      │
 │   Outgoing Audio (RTP) ◄── Outgoing Buffer ◄── Local Neural TTS         │
 │                                                                         │
 │   Encrypted Storage (AES-256-GCM) ◄── Redacted Transcripts (PII Filter) │
 │                                                                         │
 └────────────────────────────────────┬────────────────────────────────────┘
                                      │
                         ALLOWED      │      BLOCKED
                         ────────     │      ───────
                     SIP Gateway Only │      Cloud AI Endpoints (OpenAI, etc.)
                     (Signaling/RTP)  │      Audio Telemetry / Logging
                                      │      Third-Party Analytics SDKs
```

---

## 1. Cryptographic Safeguards

### 1.1 Encrypted Persistence (AES-256-GCM)
All call transcripts, structured extraction results, and call metadata are encrypted prior to persistence using the `LocalEncryptedTranscriptStore`:
- **Cipher:** `AES/GCM/NoPadding`
- **Key Length:** 256 bits
- **Key Storage:** Android Hardware Keystore (`AndroidKeyStore`), backed by the device's Secure Element / Titan M2 security chip on Google Pixel devices.
- **Authentication:** 128-bit authentication tag verification on every decryption operation.
- **Key Invalidation:** Keystore keys can be configured to require user biometric authentication (`setUserAuthenticationRequired(true)`).

### 1.2 Automated In-Memory PII Redaction
Prior to persisting transcripts or displaying them in unprotected UI contexts, raw text passes through `Redactor.redact()`:
- **Credit Card Numbers:** Full 13-19 digit credit card numbers matching Visa, Mastercard, Amex, Discover, and Diners Club formats are validated via the Luhn checksum algorithm and sanitized to `[REDACTED_CREDIT_CARD]`.
- **Social Security Numbers (SSN):** Formats matching `XXX-XX-XXXX` are sanitized to `[REDACTED_SSN]`.
- **Phone Numbers:** Direct phone sequences matching standard E.164 and NANP patterns are masked to `[REDACTED_PHONE]`.
- **Email Addresses:** RFC 5322 compliant addresses are masked to `[REDACTED_EMAIL]`.

---

## 2. Telemetry & Analytics Isolation

### 2.1 Complete Absence of Conversational Telemetry
- No audio frames, intermediate embeddings, token sequences, or transcription strings are ever logged to Android system logcat or external crash-reporting services.
- The `PrivacyAudit` component inspects all telemetry events and rejects any event containing forbidden keys:
  ```kotlin
  val FORBIDDEN_KEYS = setOf(
      "transcript", "audio", "pcm", "text", "prompt", 
      "response", "phone", "card", "ssn", "user_input"
  )
  ```

### 2.2 Strict Network Policy Enforcement
- In the `sipAgent` flavor, network activity is strictly constrained by `NetworkPolicyEnforcer`:
  - Socket connections are permitted **only** to the user-configured SIP registrar host and port.
  - Any outbound socket request targeting non-SIP endpoints is actively blocked.
- In the `pstnControl` flavor:
  - The `android.permission.INTERNET` permission is omitted entirely from `AndroidManifest.xml`.
  - Android OS networking APIs are physically inaccessible, guaranteeing zero exfiltration.

---

## 3. Threat Matrix & Residual Risks

| Threat Scenario | Mitigation Strategy | Verification Mechanism |
| :--- | :--- | :--- |
| **Cloud AI Exfiltration** | Zero external LLM/ASR API clients exist in codebase. LiteRT-LM executes local `.bin` / `.tflite` model files on Tensor NPU/GPU. | Network security config + code audit. |
| **Physical Device Theft** | Call history stored in AES-256-GCM encrypted database keyed in hardware Titan M2 Keystore. | Encrypted persistence tests. |
| **RTP Audio Interception** | VoIP SIP/RTP transport supports TLS signaling and SRTP (RFC 3711) media encryption. | SIP TLS/SRTP cipher negotiation. |
| **Adversarial Audio Prompt Injection** | ConversationController strictly validates LLM output against safety invariant contracts (rejecting financial transactions and appointments). | Deterministic SafetyValidator test suite. |
