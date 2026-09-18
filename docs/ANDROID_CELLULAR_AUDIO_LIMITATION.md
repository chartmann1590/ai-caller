# Android Cellular Audio Architecture & Limitations

## Executive Summary

The primary architectural constraint for autonomous telephony agents on Android is the platform's security sandbox surrounding cellular baseband audio. Standard (third-party) Android applications distributed through the Google Play Store **cannot capture downlink audio from, nor inject synthetic uplink audio into, normal cellular (PSTN/VoLTE/VoNR) telephone calls**.

To deliver an autonomous AI calling agent with high-fidelity, low-latency, bidirectional audio, **LocalCallAgent** adopts an **app-owned SIP/VoIP media architecture**. This document provides the rigorous technical rationale for this decision, analyzes the Android audio framework internals, and details OEM/system-level extension points.

---

## 1. Android Telephony & Audio Architecture

In modern Android devices (including Google Pixel with Tensor SoCs and Qualcomm Snapdragon devices), cellular telephony audio is handled by a dedicated hardware audio path between the baseband cellular modem (Radio Interface Layer / RIL) and the Audio Digital Signal Processor (DSP / HAL).

```text
┌────────────────────────────────────────────────────────┐
│                   Cellular Network                     │
│                  (VoLTE / VoNR / 3G)                   │
└──────────────────────────┬─────────────────────────────┘
                           │ Baseband RF
┌──────────────────────────▼─────────────────────────────┐
│                 Cellular Modem (RIL)                   │
└──────────────────────────┬─────────────────────────────┘
                           │ Hardware TDM / SoundWire / SLIMbus
┌──────────────────────────▼─────────────────────────────┐
│          Audio DSP / Hardware Audio HAL                │
│       (Voice processing, ECNS, Vocoder)                │
└────────────┬─────────────────────────────┬─────────────┘
             │ Downlink PCM                │ Uplink PCM
             ▼                             ▲
┌──────────────────────────┐  ┌──────────────────────────┐
│ Audio HAL (Earpiece/Spk) │  │  Audio HAL (Microphone)  │
└──────────────────────────┘  └──────────────────────────┘
```

The application processor (AP) running the Android Linux kernel and Android Open Source Project (AOSP) framework interacts with this path via `AudioFlinger` and `AudioPolicyService`.

---

## 2. Technical Limitations on Cellular Audio Access

### 2.1 Audio Capture Restrictions (`CAPTURE_AUDIO_OUTPUT`)

To capture audio from an ongoing voice call, an application must request one of the following audio sources in `AudioRecord` or `MediaRecorder`:
- `MediaRecorder.AudioSource.VOICE_CALL` (uplink + downlink)
- `MediaRecorder.AudioSource.VOICE_DOWNLINK` (incoming remote audio)
- `MediaRecorder.AudioSource.VOICE_UPLINK` (local outgoing audio)

In AOSP `AudioPolicyManager.cpp` and `AudioService.java`:
```cpp
// frameworks/av/services/audiopolicy/service/AudioPolicyInterfaceImpl.cpp
if (source == AUDIO_SOURCE_VOICE_UPLINK ||
    source == AUDIO_SOURCE_VOICE_DOWNLINK ||
    source == AUDIO_SOURCE_VOICE_CALL) {
    if (!captureAudioOutputAllowed(client.attributionSource)) {
        return PERMISSION_DENIED;
    }
}
```

The underlying permission required is:
```xml
<uses-permission android:name="android.permission.CAPTURE_AUDIO_OUTPUT" />
```

**Permission Protection Level:**
`CAPTURE_AUDIO_OUTPUT` is declared in AOSP as:
```xml
android:protectionLevel="signature|privileged"
```
- **Third-Party Apps (User Apps):** Cannot obtain this permission under any circumstance.
- **System Apps:** Must be placed in `/system/priv-app/` or `/product/priv-app/`, allowlisted in `/etc/permissions/privapp-permissions.xml`, and signed by the device platform key.

Attempting to initialize `AudioRecord` with `VOICE_CALL` or `VOICE_DOWNLINK` in a standard third-party app throws `java.lang.SecurityException` or fails with status `EPERM` / `PERMISSION_DENIED` at native HAL initialization.

### 2.2 Audio Injection Restrictions

Even if audio capture were possible, there is **no public Android API to inject synthetic PCM audio directly into the cellular uplink stream**.
- Playing audio via `AudioTrack` with `AudioAttributes.USAGE_VOICE_COMMUNICATION` or `USAGE_MEDIA` routes audio to the **local speaker, earpiece, or Bluetooth headset**.
- It does **not** feed into the baseband uplink encoder (AMR-WB / EVS vocoder).
- The only software mechanism for routing audio into the cellular mic uplink without hardware acoustic coupling requires modifying the Audio HAL route (`AUDIO_DEVICE_OUT_TELEPHONY_TX`) or patching the DSP mixer, both of which require root or platform system privileges.

### 2.3 `AudioPlaybackCaptureConfiguration` (Android 10+)

Android 10 (API 29) introduced `AudioPlaybackCaptureConfiguration` for media projection and screen recording. However, AOSP explicitly blocks capturing communication audio:
- Apps may opt out using `android:allowAudioPlaybackCapture="false"`.
- `USAGE_VOICE_COMMUNICATION` and `USAGE_CALL` audio streams are **strictly non-capturable** by `AudioPlaybackCapture` to protect conversational privacy.

### 2.4 `InCallService` and `CallScreeningService`

Android Telecom framework allows third-party apps to implement:
- `CallScreeningService`: Allows screening incoming calls (allow, silence, reject, skip call log). Does **not** provide audio access.
- `InCallService`: Allows replacement dialer apps to manage call state (hold, mute, swap calls, DTMF generation). DTMF generation (`playDtmfTone`) produces tones on the local speaker or instructs the modem to send out-of-band cellular DTMF bursts, but does **not** expose the raw audio buffer.

---

## 3. The Solution: App-Owned SIP/VoIP Media Pipeline

Because cellular carrier media is inaccessible to non-system apps, **LocalCallAgent uses an app-owned VoIP/SIP architecture**.

```text
┌────────────────────────────────────────────────────────────────────────┐
│                           LocalCallAgent                               │
│                                                                        │
│  ┌───────────────────────────┐         ┌────────────────────────────┐  │
│  │     Signaling (SIP)       │         │       Media (RTP)          │  │
│  │  - UDP / TCP / TLS (5060) │         │  - UDP RTP Socket          │  │
│  │  - Digest MD5 Auth        │         │  - G.711 PCMU / PCMA       │  │
│  │  - RFC 3261 Transactions  │         │  - RFC 2833 DTMF Events    │  │
│  └─────────────┬─────────────┘         └─────────────┬──────────────┘  │
│                │                                     │                 │
│                ▼                                     ▼                 │
│  ┌───────────────────────────┐         ┌────────────────────────────┐  │
│  │ Telecom ConnectionService │         │      Jitter Buffer         │  │
│  │ (CAPABILITY_SELF_MANAGED) │         └─────────────┬──────────────┘  │
│  └───────────────────────────┘                       │                 │
│                                                      ▼                 │
│                                        ┌────────────────────────────┐  │
│                                        │  Local VAD (Energy/Hangover)│  │
│                                        └─────────────┬──────────────┘  │
│                                                      │                 │
│                                                      ▼                 │
│                                        ┌────────────────────────────┐  │
│                                        │ Streaming ASR (Local ASR)  │  │
│                                        └─────────────┬──────────────┘  │
│                                                      │                 │
│                                                      ▼                 │
│                                        ┌────────────────────────────┐  │
│                                        │  Gemma 4 E2B / LiteRT-LM   │  │
│                                        └─────────────┬──────────────┘  │
│                                                      │                 │
│                                                      ▼                 │
│                                        ┌────────────────────────────┐  │
│                                        │ Local Neural TTS (<150ms)  │  │
│                                        └─────────────┬──────────────┘  │
│                                                      │                 │
│                                                      ▼                 │
│                                        ┌────────────────────────────┐  │
│                                        │ Outgoing RTP Packetizer    │  │
│                                        └────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

### Advantages of App-Owned SIP/VoIP:
1. **Full Media Ownership:** The app receives incoming RTP packets directly over UDP, decodes G.711 (PCMU/PCMA) or Opus into 16-bit linear PCM, and routes it directly into local VAD and ASR.
2. **Deterministic Uplink Injection:** The app synthesizes speech via local TTS, encodes PCM to RTP payload, and transmits packets directly to the SIP registrar/gateway.
3. **Sub-150ms Barge-in:** When the business begins speaking while the AI is outputting TTS, local VAD detects energy onset, immediately flushes the RTP transmitter queue, stops TTS synthesis, and resumes listening.
4. **Android Telecom Integration:** The app registers a `ConnectionService` with `CAPABILITY_SELF_MANAGED`. This informs Android Telecom of ongoing calls, integrates with Android's audio focus system, shows call status in notifications and status bar, and enables seamless routing to Bluetooth headsets.
5. **No Telephony Privileges Required:** Operates strictly within standard Android permissions (`INTERNET`, `RECORD_AUDIO`, `MANAGE_OWN_CALLS`).

---

## 4. Product Flavors and Separation of Concerns

To strictly enforce privacy boundaries and prevent architectural confusion, the project is divided into two distinct product flavors:

| Dimension / Capability | `sipAgent` Flavor | `pstnControl` Flavor |
| :--- | :--- | :--- |
| **Primary Use Case** | Autonomous AI phone calling | Call management & screening |
| **Telephony Transport** | App-owned SIP / RTP | Cellular `InCallService` |
| **Audio Media Access** | Direct RTP audio stream | None (`supportsProgrammaticMedia = false`) |
| **`android.permission.INTERNET`** | **Yes** (VoIP transport only) | **No** (Guaranteed zero network) |
| **`CAPTURE_AUDIO_OUTPUT`** | Forbidden | Forbidden |
| **AI Autonomous Calling** | **Fully functional** | Disabled (Throws `UnsupportedOperationException`) |

---

## 5. Privileged Carrier OEM Extension Bridge

For device manufacturers (OEMs), carrier system integrations, or rooted research devices where `CAPTURE_AUDIO_OUTPUT` is available, `telephony-api` defines:
```kotlin
package com.example.localcallagent.telephony.api

interface PrivilegedCarrierMediaBridge {
    fun isCarrierMediaSupported(): Boolean
    fun attachCarrierAudioStreams(callId: String): CarrierMediaSession
}
```

### Requirements for Carrier Media Bridge Activation:
1. APK installed to `/system/priv-app/LocalCallAgent/LocalCallAgent.apk`
2. Permissions allowlist in `/system/etc/permissions/privapp-permissions-localcallagent.xml`:
   ```xml
   <permissions>
       <privapp-permissions package="com.example.localcallagent">
           <permission name="android.permission.CAPTURE_AUDIO_OUTPUT"/>
           <permission name="android.permission.MODIFY_PHONE_STATE"/>
           <permission name="android.permission.CONTROL_INCALL_EXPERIENCE"/>
       </privapp-permissions>
   </permissions>
   ```
3. Platform Key signature matching the device ROM build.
4. Audio HAL patched to support loopback uplink injection (`AUDIO_DEVICE_OUT_TELEPHONY_TX`).

In standard retail distribution, `PrivilegedCarrierMediaBridge.isCarrierMediaSupported()` returns `false`, and the app defaults to the robust, privacy-compliant SIP/VoIP engine.
