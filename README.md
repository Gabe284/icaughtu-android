# iCaughtU Android

An owner-controlled Android anti-theft/security app inspired by iCaughtU Pro. It watches for failed system unlock attempts through Android's Device Administration / Device Policy APIs, records incidents, can attach last-known location, can deliver incident JSON to an HTTPS webhook, can sound a lost-device alarm, and supports a small authenticated SMS command set.

The project targets Android 16 / API 36, has a minimum SDK of 28, and is intentionally designed around supported Android security boundaries rather than trying to replace or bypass the system keyguard.

## What is implemented

- Failed system unlock detection through `DeviceAdminReceiver` + `watch-login`.
- Configurable incident threshold (1–20 failed attempts).
- Front-camera JPEG capture after a failed unlock **when the app is provisioned as Device Owner** and camera permission is granted.
- Manual front-camera test while the app is visible, even in ordinary Device Admin mode.
- Last-known location attachment when location permission is available.
- Local incident log in device-protected app storage.
- HTTPS-only webhook delivery with optional HMAC-SHA256 `X-ICU-Signature`.
- Local security notifications.
- Remote authenticated SMS commands: `STATUS`, `LOCATE`, `LOCK`, `ARM ON`, `ARM OFF`, `ALARM`, `STOPALARM`.
- Device lock through Device Policy when Device Admin is active.
- Lost-device alarm foreground service.

## Deliberate Android differences

The original iOS tweak could hook SpringBoard/lock-screen internals and offered features such as fake unlock/fake power-off and other system-level interception. A normal modern Android app cannot safely replace the system keyguard or reliably suppress the hardware power UI. This project does not attempt those behaviors.

Automatic front-camera capture from a failed system-unlock event is restricted on current Android when the app is merely a normal background app. The implementation therefore enables that path only when the app is a Device Policy Controller running as **Device Owner**. In ordinary Device Admin mode, failed attempts are still logged and can trigger location/webhook processing, but no automatic background camera capture is attempted.

The app is not hidden. Camera capture uses a foreground service and remains subject to Android's privacy indicators. Remote SMS does **not** expose a `PHOTO` command.

## Build

Requirements:

- JDK 17
- Android SDK Platform 36
- Internet access for Gradle/Maven dependencies on the first build

From Linux/macOS:

```bash
./gradlew :app:assembleDebug
```

From Windows:

```bat
gradlew.bat :app:assembleDebug
```

The included launcher bootstraps Gradle 8.13 into `.gradle-bootstrap/` and verifies the official Gradle 8.13 binary-distribution SHA-256 before extraction. The debug APK will be written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

You can also push the project to GitHub and run **Build Android APK** under Actions; the workflow uploads `icaughtu-android-debug` as an artifact.

## Install: ordinary Device Admin mode

1. Build and install the APK:

   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

2. Open **iCaughtU Android**.
3. Tap **Activate Device Admin** and approve the requested `watch-login` / `force-lock` policies.
4. Grant camera and location permissions. If you want last-known location available while the UI is not open, also grant background location from the app's Android settings page.
5. Configure the failed-attempt threshold, webhook, and/or SMS command options, then tap **Save settings**.

This mode detects failed unlocks and supports logging, location/webhook processing, manual camera tests, and Device Admin locking. Automatic lock-screen camera capture is disabled unless the app is Device Owner.

## Install: full Device Owner mode

Use a fresh/test device or emulator that is eligible for Device Owner provisioning. Install the APK before completing normal user provisioning, then run:

```bash
adb shell dpm set-device-owner com.example.icaughtuandroid/.admin.GuardAdminReceiver
```

Verify:

```bash
adb shell dpm list-owners
```

Then open the app and grant camera/location permissions. The status block should show:

```text
Mode: DEVICE OWNER (full)
```

On a normally provisioned personal device, Android generally requires reprovisioning/factory reset before a new Device Owner can be set. Do not factory-reset a device until its data is backed up.

## Webhook format

Example request body:

```json
{
  "event": "failed_unlock",
  "source": "failed_unlock",
  "attempts": 3,
  "timestamp": 1786352400000,
  "device": "Example Device / Android 16 (API 36)",
  "latitude": 37.123,
  "longitude": -93.456,
  "mapUrl": "https://maps.google.com/?q=37.123,-93.456",
  "photoMime": "image/jpeg",
  "photoBase64": "..."
}
```

When a webhook secret is configured, the request also includes:

```text
X-ICU-Signature: sha256=<hex HMAC-SHA256 of the exact JSON body>
```

The client rejects non-HTTPS webhook URLs.

## SMS commands

Configure both a trusted sender number and a unique command key of at least six characters. Syntax:

```text
ICU <key> STATUS
ICU <key> LOCATE
ICU <key> LOCK
ICU <key> ARM ON
ICU <key> ARM OFF
ICU <key> ALARM
ICU <key> STOPALARM
```

Commands are ignored unless both the sender number and key match. SMS itself is not an end-to-end encrypted command channel, so use a long random key and a trusted number. The project intentionally omits remote camera capture and destructive commands.

## Suggested first test

1. Install and open the app.
2. Activate Device Admin.
3. Grant camera + location.
4. Leave the threshold at `1` and keep **Armed** enabled.
5. Tap **Capture test incident** to validate camera, local storage, and webhook delivery.
6. Lock the phone and deliberately enter one incorrect credential.
7. Unlock correctly, reopen the app, and inspect **Recent incidents**.
8. In Device Owner mode, verify that the failed-unlock incident reports a saved front-camera photo.

## Source layout

- `MainActivity.kt` — configuration/status UI.
- `admin/GuardAdminReceiver.kt` — failed-unlock Device Policy event handler.
- `service/IncidentCaptureService.kt` — Device Owner/manual Camera2 capture pipeline.
- `service/IncidentJobService.kt` — no-camera incident processing.
- `remote/RemoteCommandReceiver.kt` — authenticated SMS control.
- `service/AlarmService.kt` — lost-device alarm.
- `util/WebhookClient.kt` — HTTPS + HMAC incident delivery.
- `data/Prefs.kt`, `data/IncidentStore.kt` — device-protected local state/logging.

## v0.2 communication transports

All communication transports are independent and can be enabled in any combination.

- **SMS commands:** Existing `ICU <key> ...` commands remain supported and can be enabled/disabled separately.
- **ntfy Internet commands:** Optional HTTPS streaming command channel over Wi-Fi/Internet. Configure an ntfy server, command topic, optional response topic, optional Bearer token, and a command key. A visible `remoteMessaging` foreground-service notification is shown while listening.
- **Webhook incidents:** Existing HTTPS/HMAC incident delivery remains available with an independent enable switch.
- **SMTP email incidents:** Optional direct SMTP delivery with STARTTLS (normally port 587) or implicit TLS (normally port 465). Photo attachments are included when a photo exists and photo delivery is enabled.
- **ntfy incident alerts:** Optional incident summaries can be published to a separate ntfy topic.

The SMS and ntfy command syntaxes are identical:

```text
ICU <key> STATUS
ICU <key> LOCATE
ICU <key> LOCK
ICU <key> ARM ON
ICU <key> ARM OFF
ICU <key> ALARM
ICU <key> STOPALARM
```

For ntfy, publish commands to the configured command topic. Do not reuse a command key from another service. Use HTTPS topics and a private/protected topic or access token for remote control.


## v0.3.1 gallery + persistent signing

Incident JPEGs are still retained in the app's private incident store for delivery, and are also published to Android MediaStore under `Pictures/iCaughtU`. This makes new captures visible to gallery applications and creates an `iCaughtU` device album/bucket. The app also retries publication of any private incident photos that have not yet been exported whenever the main activity starts, and exposes a manual gallery-sync button.

GitHub Actions now builds a persistently signed release APK. The signing key is generated once under the Termux-private directory `~/.icaughtu-signing/` and is uploaded to GitHub only through repository Actions secrets. Preserve that local signing directory as a backup. Losing both the local key and the GitHub secret prevents future APKs from updating existing installations.

The pre-v0.3.1 GitHub debug APKs were signed with runner-generated debug certificates. Android will therefore require the currently installed debug build to be uninstalled once before the first persistently signed v0.3.1 release can be installed. After that migration, future signed release builds can update v0.3.1+ in place.
