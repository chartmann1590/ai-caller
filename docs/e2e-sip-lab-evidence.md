# SIP lab e2e evidence (sip_test_api35)

Broadcast DEBUG_SIP_E2E with lab loopback (in-process LabSipRegistrar / tools/sip_test_server.py).

Observed logcat tags SipDebugE2E / SipEngine:
- REGISTERED
- INVITE_SENT dest=1002
- CALL_ACTIVE
- DONE

Public SIP2SIP may still fail without NetworkAgent — lab e2e is the required proof.

No credentials committed.

## Listen / talk pipeline (SipDebugE2E)

Expected tags after loopback 1001→1002 with models installed:

- `MODEL_READY`
- `REGISTERED`
- `INVITE_SENT`
- `CALL_ACTIVE`
- `ASR_PARTIAL` / `ASR_FINAL`
- `LLM_REPLY`
- `TTS_SENT`
- `DONE`

No credentials in logs or git.
