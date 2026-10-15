# Graph Report - ai-caller  (2026-09-19)

## Corpus Check
- Corpus is ~39,423 words - fits in a single context window. You may not need a graph.

## Summary
- 725 nodes · 1308 edges · 43 communities (25 shown, 18 thin omitted)
- Extraction: 97% EXTRACTED · 3% INFERRED · 0% AMBIGUOUS · INFERRED: 42 edges (avg confidence: 0.83)
- Token cost: 176,899 input · 0 output

## Community Hubs (Navigation)
- G.711 Codec / Jitter Buffer
- Safety Validator & Benchmark Tests
- Conversation Controller & Audio Router
- Call State & CallTransport Interface
- MainViewModel & App UI Actions
- ASR Slot Validation & On-Device Test
- Docs: Legal, Device Support, Cellular Limits
- Agent State Machine
- Encrypted Transcript Store & Keystore Test
- Device Capability Probing
- ConversationController Call Lifecycle
- UI Navigation & Screens
- MainViewModel Fake CallTransport (startCall)
- Telecom ConnectionService (SIP)
- Local Transducer ASR & Slot Extraction
- Scripted Test Scenario Categories
- Local Model Descriptor & Status
- OnDevice Test Fake CallTransport/TTS
- VAD Detector
- Bounded Audio Queue
- PCM Frame & PrivilegedCarrierMediaBridge
- SIP Account Config (DTMF/Transport)
- PCM Resampler
- OnDevice Test Fake StreamingASR
- Local Call Screening Service (PSTN)
- Local TTS
- Gradle Wrapper Script
- CallTransportType (Carrier/SIP)

## God Nodes (most connected - your core abstractions)
1. `PcmFrame` - 61 edges
2. `ConversationController` - 32 edges
3. `MainViewModel` - 32 edges
4. `SipEngine` - 31 edges
5. `AppCallState` - 27 edges
6. `CallObjective` - 20 edges
7. `AgentState` - 18 edges
8. `DialogueContext` - 18 edges
9. `DeterministicFallbackModel` - 18 edges
10. `CallTransport` - 17 edges

## Surprising Connections (you probably didn't know these)
- `Serena Project Config (ai-caller)` --conceptually_related_to--> `LocalCallAgent README`  [AMBIGUOUS]
  .serena/project.yml → README.md
- `Deep Research Report: On-Device Android AI Phone-Calling Agent` --conceptually_related_to--> `Telephone Consumer Protection Act (TCPA)`  [INFERRED]
  Plan/deep-research-report.md → docs/LEGAL_REVIEW_CHECKLIST.md
- `AgentDecision Structured Output Schema` --conceptually_related_to--> `SafetyValidator (Deterministic Safety Contract)`  [INFERRED]
  Plan/deep-research-report.md → docs/THREAT_MODEL.md
- `MainViewModel` --references--> `AgentState`  [EXTRACTED]
  app/src/main/java/com/example/localcallagent/ui/MainViewModel.kt → agent-orchestrator/src/main/java/com/example/localcallagent/agent/orchestrator/AgentState.kt
- `ConversationController` --calls--> `VadDetector`  [EXTRACTED]
  agent-orchestrator/src/main/java/com/example/localcallagent/agent/orchestrator/ConversationController.kt → audio-core/src/main/java/com/example/localcallagent/audio/core/VadDetector.kt

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Local Privacy Architecture Components** — docs_privacy_model_redactor, docs_privacy_model_privacy_audit, docs_privacy_model_network_policy_enforcer, docs_privacy_model_local_encrypted_transcript_store [EXTRACTED 0.90]
- **sipAgent/pstnControl Flavor Separation Group** — docs_android_cellular_audio_limitation_product_flavors, readme_telephony_sip_module, readme_telephony_pstn_module [EXTRACTED 0.85]
- **STRIDE Mitigation Components** — docs_threat_model_stride_methodology, docs_threat_model_safety_validator, docs_privacy_model_redactor, docs_privacy_model_local_encrypted_transcript_store [INFERRED 0.70]

## Communities (43 total, 18 thin omitted)

### Community 0 - "G.711 Codec / Jitter Buffer"
Cohesion: 0.05
Nodes (17): bytebuffer, locale, messagedigest, priorityqueue, G711Codec, ByteArray, ShortArray, JitterBuffer (+9 more)

### Community 1 - "Safety Validator & Benchmark Tests"
Cohesion: 0.05
Nodes (28): SafetyValidator, AgentOrchestratorTest, BenchmarkReport, AgentAction, ABORT, CLARIFY, FINISH, HANDOFF (+20 more)

### Community 2 - "Conversation Controller & Audio Router"
Cohesion: 0.05
Nodes (35): Job, StateFlow, assharedflow, asstateflow, AudioRouter, CallAudioMode, BOT_CALLING, HUMAN_TAKEOVER (+27 more)

### Community 3 - "Call State & CallTransport Interface"
Cohesion: 0.05
Nodes (26): Active, AppCallState, Dialing, Disconnected, Holding, Idle, Ringing, CallTransport (+18 more)

### Community 4 - "MainViewModel & App UI Actions"
Cohesion: 0.09
Nodes (31): alignment, animatecolorasstate, Job, MainViewModel, ModelDownloadState, QualificationState, LiveCallScreen(), OnboardingScreen() (+23 more)

### Community 5 - "ASR Slot Validation & On-Device Test"
Cohesion: 0.08
Nodes (22): androidjunit4, StateFlow, CriticalSlotValidator, AsrResult, Flow, StreamingAsr, assertarrayequals, assertequals (+14 more)

### Community 6 - "Docs: Legal, Device Support, Cellular Limits"
Cohesion: 0.07
Nodes (43): CAPTURE_AUDIO_OUTPUT Permission Restriction, Android Cellular Audio Architecture & Limitations, PrivilegedCarrierMediaBridge (OEM Extension Point), sipAgent / pstnControl Product Flavor Separation, App-Owned SIP/VoIP Media Pipeline, BenchmarkRunner (On-Device Performance Test), Device Qualification Matrix & Hardware Requirements, Hardware Compatibility & Device Qualification Matrix (+35 more)

### Community 7 - "Agent State Machine"
Cohesion: 0.06
Nodes (24): AgentState, ASKING, CLARIFYING, COMPLETE, DIALING, DISCLOSURE, FAILED, GOODBYE (+16 more)

### Community 8 - "Encrypted Transcript Store & Keystore Test"
Cohesion: 0.07
Nodes (14): OnDevicePrivacyAndCallTest, cipher, StructuredCallResult, ByteArray, LocalEncryptedTranscriptStore, TranscriptStore, NetworkPolicyEnforcer, PrivacyAudit (+6 more)

### Community 9 - "Device Capability Probing"
Cohesion: 0.07
Nodes (21): activitymanager, DeviceCapabilityRepository, build, Bundle, context, DeviceCapabilities, SupportLevel, EXCELLENT (+13 more)

### Community 10 - "ConversationController Call Lifecycle"
Cohesion: 0.17
Nodes (5): ConversationController, Speaker, BOT, REMOTE_BUSINESS, USER

### Community 11 - "UI Navigation & Screens"
Cohesion: 0.10
Nodes (18): MainActivity, Screen, LIVE_CALL, ONBOARDING, RESULT_SUMMARY, SETTINGS, TASK_CREATION, LocalCallAgentTheme() (+10 more)

### Community 12 - "MainViewModel Fake CallTransport (startCall)"
Cohesion: 0.11
Nodes (7): CallTransport, LocalTts, StateFlow, StreamingAsr, CallTransport, LocalTts, StreamingAsr

### Community 13 - "Telecom ConnectionService (SIP)"
Cohesion: 0.20
Nodes (8): Connection, ConnectionRequest, ConnectionService, disconnectcause, PhoneAccountHandle, AgentConnectionService, StateFlow, SipTelecomConnection

### Community 14 - "Local Transducer ASR & Slot Extraction"
Cohesion: 0.17
Nodes (5): Flow, StreamingAsr, LocalTransducerAsr, RuleSlotExtractor, AsrLocalTest

### Community 15 - "Scripted Test Scenario Categories"
Cohesion: 0.12
Nodes (16): ScenarioCategory, AMBIGUOUS_ANSWER, APPOINTMENT_CHECK, CONFLICTING_ANSWER, INVENTORY_CHECK, IVR_TREE, OPENING_HOURS, PARKING_ACCESSIBILITY (+8 more)

### Community 16 - "Local Model Descriptor & Status"
Cohesion: 0.14
Nodes (13): LocalModelDescriptor, ModelStatus, CORRUPTED, DOWNLOADING, ERROR, NOT_DOWNLOADED, READY, VERIFYING (+5 more)

### Community 17 - "OnDevice Test Fake CallTransport/TTS"
Cohesion: 0.17
Nodes (5): CallTransport, Flow, LocalTts, CallTransport, LocalTts

### Community 18 - "VAD Detector"
Cohesion: 0.19
Nodes (8): ShortArray, VadDetector, VadState, IN_SPEECH, SILENCE, SPEECH_ENDING, SPEECH_STARTING, sqrt

### Community 19 - "Bounded Audio Queue"
Cohesion: 0.20
Nodes (3): arraydeque, BoundedAudioQueue, AudioCoreTest

### Community 20 - "PCM Frame & PrivilegedCarrierMediaBridge"
Cohesion: 0.27
Nodes (4): arrays, PcmFrame, Flow, PrivilegedCarrierMediaBridge

### Community 21 - "SIP Account Config (DTMF/Transport)"
Cohesion: 0.20
Nodes (9): DtmfMode, INBAND, RFC2833, SIP_INFO, SipTransportType, TCP, TLS, UDP (+1 more)

### Community 24 - "Local Call Screening Service (PSTN)"
Cohesion: 0.60
Nodes (3): CallScreeningService, Call, LocalCallScreeningService

### Community 26 - "Gradle Wrapper Script"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 27 - "CallTransportType (Carrier/SIP)"
Cohesion: 0.50
Nodes (3): CallTransportType, CARRIER_PSTN, SIP_VOIP

## Ambiguous Edges - Review These
- `Serena Project Config (ai-caller)` → `LocalCallAgent README`  [AMBIGUOUS]
  .serena/project.yml · relation: conceptually_related_to

## Knowledge Gaps
- **96 isolated node(s):** `PRE_CALL`, `DIALING`, `WAITING_FOR_ANSWER`, `DISCLOSURE`, `ASKING` (+91 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 253 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **18 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **What is the exact relationship between `Serena Project Config (ai-caller)` and `LocalCallAgent README`?**
  _Edge tagged AMBIGUOUS (relation: conceptually_related_to) - confidence is low._
- **Why does `PcmFrame` connect `PCM Frame & PrivilegedCarrierMediaBridge` to `G.711 Codec / Jitter Buffer`, `Safety Validator & Benchmark Tests`, `Conversation Controller & Audio Router`, `Call State & CallTransport Interface`, `ASR Slot Validation & On-Device Test`, `Agent State Machine`, `Device Capability Probing`, `MainViewModel Fake CallTransport (startCall)`, `Local Transducer ASR & Slot Extraction`, `OnDevice Test Fake CallTransport/TTS`, `VAD Detector`, `Bounded Audio Queue`, `PCM Resampler`, `OnDevice Test Fake StreamingASR`, `Local TTS`?**
  _High betweenness centrality (0.257) - this node is a cross-community bridge._
- **Why does `AppCallState` connect `Call State & CallTransport Interface` to `G.711 Codec / Jitter Buffer`, `Conversation Controller & Audio Router`, `ASR Slot Validation & On-Device Test`, `Agent State Machine`, `MainViewModel Fake CallTransport (startCall)`, `Telecom ConnectionService (SIP)`, `OnDevice Test Fake CallTransport/TTS`?**
  _High betweenness centrality (0.114) - this node is a cross-community bridge._
- **Why does `SipEngine` connect `G.711 Codec / Jitter Buffer` to `Conversation Controller & Audio Router`, `Call State & CallTransport Interface`, `PCM Frame & PrivilegedCarrierMediaBridge`, `MainViewModel & App UI Actions`?**
  _High betweenness centrality (0.112) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `ConversationController` (e.g. with `.testFullConversationFlowToSuccess()` and `.testHumanTakeoverHaltsBot()`) actually correct?**
  _`ConversationController` has 2 INFERRED edges - model-reasoned connections that need verification._
- **What connects `PRE_CALL`, `DIALING`, `WAITING_FOR_ANSWER` to the rest of the system?**
  _96 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `G.711 Codec / Jitter Buffer` be split into smaller, more focused modules?**
  _Cohesion score 0.05086071987480438 - nodes in this community are weakly interconnected._