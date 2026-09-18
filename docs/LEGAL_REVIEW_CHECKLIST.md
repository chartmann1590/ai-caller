# Telephony Legal Review & Regulatory Compliance Checklist

## 1. Regulatory Overview

Autonomous outbound telephony using conversational AI is governed by federal and state regulations in the United States, including:
- **Telephone Consumer Protection Act (TCPA), 47 U.S.C. § 227**
- **FCC Declaratory Ruling & Order on AI Generated Voices (FCC 24-17 / FCC 23-107)**
- **Federal Trade Commission (FTC) Telemarketing Sales Rule (TSR)**
- **State All-Party (Two-Party) Audio Consent Laws**

---

## 2. Compliance Checklist & Implementation Audit

| Item | Legal Requirement | LocalCallAgent Implementation | Status |
| :--- | :--- | :--- | :--- |
| **1. Mandatory AI Bot Disclosure** | Outbound AI calls must clearly disclose at the outset that an artificial/automated assistant is speaking. | Enforced in `ConversationController.onCallConnected()`. Cannot be disabled or skipped by user. | **COMPLIANT** |
| **2. Caller Identification** | Caller must disclose who the call is on behalf of. | Mandatory caller name included in introductory disclosure (`"Hi, I'm an automated assistant calling on behalf of {callerName}..."`). | **COMPLIANT** |
| **3. Two-Party Consent for Recording** | 12 US states (CA, FL, IL, MA, MD, MI, MT, NV, NH, PA, WA) require all parties to consent to audio recording/transcription. | In-call disclosure explicitly asks `"Is it okay if I continue?"` before proceeding with conversation. Audio is never stored or uploaded. | **COMPLIANT** |
| **4. Instant Human Takeover** | Immediate transfer or cessation if recipient objects. | `[TAKE OVER]` button routes microphone instantly (<50ms). If recipient states "I want to talk to a human", agent transitions immediately to `AgentState.HANDOFF`. | **COMPLIANT** |
| **5. Prohibition on Emergency Dialing** | 911, 112, 999, 988 and emergency numbers must never be called by automated agents. | `DialerValidator` strictly blocks 3-digit and emergency destinations, delegating to the native Android phone app. | **COMPLIANT** |
| **6. No Robocall Telemarketing** | System must not be used for unsolicited automated advertising, lead generation, or spam. | App is strictly single-task, user-directed for inquiry-only business calls (e.g. asking store hours, pricing, stock). Bulk dialing is prohibited in architecture. | **COMPLIANT** |
| **7. No Financial Commitments** | Autonomous purchasing or debt agreement is strictly prohibited. | `SafetyValidator` strictly blocks credit card numbers, deposits, and binding contracts. Zero financial execution tools. | **COMPLIANT** |

---

## 3. Emergency Services (911) Disclaimer

> **IMPORTANT WARNING:**
> LocalCallAgent does **NOT** provide access to emergency services (e.g., 911, 112, 988). In an emergency, always use your mobile device's native cellular dialer.
