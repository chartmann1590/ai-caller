# Free SIP Setup Guide (sipAgent flavor)

This app can register with any standards-compliant SIP provider over **UDP**.
Use a free provider for development and echo tests. **Never commit real passwords.**

## Recommended free providers (verified reachable over UDP/5060)

| Provider | Domain | Outbound proxy (if needed) | Echo / test destination | Notes |
|---|---|---|---|---|
| **SIP2SIP** | `sip2sip.info` | `proxy.sipthor.net` | `4444` (echo), `3333` (AV test) | Sign up at https://sip2sip.info → Account. DNS NAPTR/SRV preferred; proxy is a known working fallback. |
| **iptel.org** | `iptel.org` | `sip.iptel.org` | `echo` or `music` | Lifetime free accounts via https://www.iptel.org (email confirmation). |
| **Linphone** | `sip.linphone.org` | _(usually none)_ | another Linphone account | Create at https://subscribe.linphone.org/register/email |

## Configure in the app

1. Build & install the sipAgent debug APK:
   ```bash
   ./gradlew :app:assembleSipAgentDebug
   adb install -r app/build/outputs/apk/sipAgent/debug/app-sipAgent-debug.apk
   ```
2. On the **Onboarding** or **Settings** screen enter:
   - **Username** — SIP user (before `@`)
   - **Password** — SIP secret (kept in memory / EncryptedSharedPreferences only)
   - **Domain** — e.g. `sip2sip.info`
   - **Port** — `5060` (UDP)
   - **Outbound Proxy** — e.g. `proxy.sipthor.net` when DNS NAPTR is unavailable
   - **Display Name** — caller ID spoken/shown
3. Tap **Register**. Status should move to `REGISTERED`.
4. Place a test call to the provider echo number (SIP2SIP: destination `4444`).

## Secrets policy

- `local.properties` is gitignored — you may put machine-local SDK paths there.
- Do **not** put SIP passwords in source, Gradle files, or docs.
- Debug builds ship with inert placeholders (`1001` / `sip_password` / `sip.example.com`).
- Prefer OS env vars for CI smoke tests, e.g.:
  ```bash
  export SIP_USER=...
  export SIP_PASSWORD=...
  export SIP_DOMAIN=sip2sip.info
  export SIP_PROXY=proxy.sipthor.net
  ```

## Emulator notes

- Mic / telephony audio on the Android Emulator is limited; treat echo tests as
  **signaling + RTP path** validation. Full ASR/LLM/TTS quality needs a physical device.
- Grant `RECORD_AUDIO` when prompted.
- Nested KVM must be available for x86_64 system images.

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|---|
| Stays `REGISTERING` | UDP blocked / wrong proxy | Set outbound proxy; confirm UDP/5060 egress |
| `FAILED` after 401 loop | Bad password or digest URI | Re-check username/password; ensure domain matches realm |
| Registered but no audio | NAT / Contact is 127.0.0.1 | App now uses routable IP discovery; still may need STUN on strict NAT |
| Build flavor has no INTERNET | Wrong flavor | Use `sipAgent`, not `pstnControl` |

## Local lab registrar (optional)

For offline CI, run the included Python mini-registrar:

```bash
python3 tools/sip_test_server.py
# Then point the app at domain=127.0.0.1, user=1001, pass=secret (host-only).
# Emulator must use 10.0.2.2 instead of 127.0.0.1 to reach the host.
```
