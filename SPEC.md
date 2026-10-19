# WristShutter: spec

Use a Garmin Forerunner 55 as a remote shutter for the **stock Samsung Camera app** on a Galaxy S26 Ultra. This replaces the S Pen Air Actions remote that Samsung removed.

This is a personal tool. It will be sideloaded, not published.

---

## 1. Goals and non-goals

**Goals**
- Pressing a button on the watch takes a photo in Samsung's own camera app, so all of Samsung's processing, modes and zoom are kept.
- Other watch buttons start and stop video recording and flip between front and rear cameras.
- The watch shows whether the phone acknowledged the command, and vibrates when it does.
- Development works with **VS Code only**. Android Studio and .NET/MAUI are not used.

**Non-goals**
- Play Store or Connect IQ Store publishing
- iOS support
- A live preview on the watch
- A custom camera implementation (no CameraX)

---

## 2. Environment

| Item | Detail |
|---|---|
| Watch | Garmin Forerunner 55 (5 buttons, no touchscreen, very limited memory) |
| Phone | Samsung Galaxy S26 Ultra, current One UI |
| Dev machine | VS Code. Assume Windows, so give `gradlew.bat` equivalents for every command. |
| Toolchains | JDK 17+, Garmin Connect IQ SDK (via SDK Manager) and the Monkey C VS Code extension, Android command-line tools (`sdkmanager`, `platform-tools`) |
| Phone language | Kotlin with Gradle Kotlin DSL |

---

## 3. Architecture

```
[FR55 watch app]  --Connect IQ Communications-->  [Garmin Connect app on phone]
                                                        |
                                                        v
                     [WristShutter Android app: AccessibilityService]
                        - hosts the Connect IQ SDK listener
                        - finds and clicks buttons in com.sec.android.app.camera
                        - sends an ack back to the watch
```

**Key decision:** the Connect IQ listener lives *inside the AccessibilityService*. The system keeps accessibility services alive, so the app doesn't need a separate foreground service and is less likely to be killed by Samsung's battery management. If that proves unreliable, fall back to a foreground service of type `connectedDevice` (see Phase 4).

---

## 4. Repo layout

```
wristshutter/
├── README.md              # setup, build, sideload and troubleshooting
├── SPEC.md                # this file
├── watch/                 # Monkey C project
│   ├── manifest.xml
│   ├── monkey.jungle
│   ├── resources/
│   └── source/
└── phone/                 # Android Gradle project (Kotlin DSL)
    ├── settings.gradle.kts
    ├── build.gradle.kts
    ├── gradlew / gradlew.bat
    └── app/
        ├── build.gradle.kts
        └── src/main/
            ├── AndroidManifest.xml
            ├── java/.../  (or kotlin/)
            └── res/xml/accessibility_config.xml
```

---

## 5. Message protocol

Both sides share one **app UUID**. Generate it once and store it in the watch `manifest.xml` and in a single Kotlin constant.

**Watch to phone** (a Monkey C Dictionary, which arrives in the phone app as a Map):

```json
{ "cmd": "shoot" | "mode" | "flip" | "zoom" | "ping", "id": <int, increments per message> }
```

After Phase 4, `video` was replaced: `shoot` is one shutter (photo in Photo mode, start/stop recording in Video mode) and `mode` switches Photo <-> Video.

`zoom` also carries `"level": "<lens factor as a string, e.g. \"0.6\" or \"3\">"` (added after Phase 4).

**Phone to watch:**

```json
{ "ack": <id>, "ok": true | false, "err": "<short reason or empty>", "mode": "photo" | "video" | "recording" | "" }
```

`mode` (added after Phase 4) is the camera state the command left behind; "" means closed or another mode.

Interim reply when the phone has to open Samsung Camera first (added after Phase 4): `{ "ack": <id>, "wait": true }`. The watch keeps waiting, up to 10 s, for the final ack.

**Error codes:** these are short because the watch screen is tiny.
- `NO_CAM`: Samsung Camera is not in the foreground
- `NOT_FOUND`: the target button could not be located
- `GESTURE_FAIL`: the button was found but the click or tap failed
- `NO_A11Y`: the service is not running (best effort)
- `BUSY`: the same command arrived again within 1 s and was ignored (added after Phase 4)
- `RECORDING`: mode can't switch while recording (added after Phase 4)
- `MODE_FAIL`: the camera didn't reach the requested mode in time (added after Phase 4)

---

## 6. Watch app (Monkey C)

- **App type:** a device app (`watch-app`), not a widget.
- **Device and API level:** target the FR55 only. Read its supported API level from the SDK device files and set `minApiLevel` to match. Don't assume a level.
- **Permissions:** `Communications` only.

**Button mapping**

| Button | Action |
|---|---|
| START | `shoot`, with the countdown if one is set |
| UP | `flip` |
| DOWN | `video` (toggles record) |
| BACK | exit |
| Long-press UP (menu) | settings menu: countdown 0, 3 or 5 s |

**Behaviour**
- On start, send `ping` and show "Phone OK" or "No phone".
- The countdown runs **on the watch**: show big digits, vibrate on each second, and send the command when it reaches 0. This hides the relay latency.
- After sending, show "Sent…". Then, on the ack:
  - if `ok`, show "✓" and give a short vibration
  - if not `ok`, show the error code and give a double vibration
- If no ack arrives within 4 s, show "No reply".
- Use `Communications.transmit()` with a `ConnectionListener` and `Communications.registerForPhoneAppMessages()` for acks.
- Keep the memory footprint minimal: no bitmaps, only system fonts, and a single view plus a menu.
- Persist the countdown setting with `Application.Storage`.

---

## 7. Phone app (Kotlin)

### 7.1 Build config
- AGP and Kotlin: use the latest stable versions. `compileSdk` and `targetSdk` should be the latest stable API level, and `minSdk` can be 34 since this only targets an S26 Ultra.
- **Garmin SDK dependency:** the Connect IQ Mobile SDK from Maven Central. The artifact is expected to be `com.garmin.connectiq:ciq-companion-app-sdk`. Verify the current group, artifact and version before using them.
- Avoid Compose to keep builds fast. Use one simple Activity with XML Views or programmatic Views.

### 7.2 Manifest
- Add a `<queries>` entry for `com.garmin.android.apps.connectmobile`. The Connect IQ SDK needs package visibility on recent Android versions.
- Declare the AccessibilityService with `android.permission.BIND_ACCESSIBILITY_SERVICE` and point it at `res/xml/accessibility_config.xml`.
- Add `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

### 7.3 `accessibility_config.xml`
- `packageNames="com.sec.android.app.camera"`, so the service only receives events from the camera.
- `canRetrieveWindowContent="true"`
- `canPerformGestures="true"`
- `accessibilityFlags` should include `flagReportViewIds` and `flagRetrieveInteractiveWindows`.
- `accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"`
- Write a clear user-facing `description` string.

### 7.4 `ShutterAccessibilityService`
- In `onServiceConnected`, initialise `ConnectIQ` and register for app events from the watch app UUID on every known device.
- **Connection type:** use `WIRELESS` in release builds. In debug builds, make it switchable to `TETHERED` for the simulator (see section 9).
- **On message:**
  1. Parse `cmd` and `id`.
  2. If the command is `ping`, ack `ok` straight away.
  3. Check that the active window's package is `com.sec.android.app.camera`. If it isn't, ack with `NO_CAM`.
  4. Resolve the target button using the strategy chain in section 7.5.
  5. Perform the click and ack the result to the same device and app.
- Tear down ConnectIQ cleanly in `onUnbind` and `onDestroy`.

### 7.5 Finding the button: strategy chain

Try each method in order and stop at the first that succeeds.

1. **By view resource ID.** Store the IDs as editable settings with defaults, because they will change across One UI versions. Use `findAccessibilityNodeInfosByViewId()` and call `performAction(ACTION_CLICK)` on the node or its nearest clickable ancestor.
2. **By content description.** Match case-insensitively against a configurable list of strings, defaulting to likely English labels such as shutter, take picture, record, or switch camera.
3. **By coordinates.** Use `dispatchGesture()` to tap at a user-configured position, stored as percentages of screen width and height. Store one position per command.

The initial resource IDs are **unknown**. Claude Code should not guess them. Section 7.6 provides the tool to discover them on the device.

### 7.6 Node-dump debug tool
- Add a toggle in the app: "Dump camera UI to logcat".
- When the toggle is on and the camera window changes, walk the node tree. For each node, log the view ID, class, text, content description, clickable flag and bounds.
- Use the log tag `WristShutter`.
- The README must explain how to use the dump: open the camera, run `adb logcat -s WristShutter`, find the shutter, record and flip buttons, and paste their IDs into the settings.

### 7.7 Settings Activity
Keep it single-screen and plain.

- **Status rows:**
  - Accessibility service enabled (yes/no, with a button that opens accessibility settings)
  - Connect IQ SDK ready
  - Watch connected (show the device name)
  - Battery optimisation ignored (with a button that opens the request)
- **Editable fields:** resource IDs and content-description lists for each command, plus fallback coordinates for each command.
- **Test buttons:** "Test shoot", "Test video" and "Test flip". These run the same code path as a watch command, after a 3 s delay so you can switch to the camera.
- The node-dump toggle.
- Store everything with `SharedPreferences`. No database.

### 7.8 Optional stretch goal
- If the camera isn't in the foreground when `shoot` arrives, try to launch it with `MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA`, then click once the window appears. Background activity-launch restrictions may block this, so verify on the device. If it is blocked, drop the feature and keep `NO_CAM`.

---

## 8. Sideloading gotchas (must go in the README)

- **Restricted settings:** Android blocks sideloaded apps from enabling accessibility at first. To unlock it, go to Settings → Apps → WristShutter → ⋮ → *Allow restricted settings*, then enable the service. Document this step prominently.
- **Battery:**
  - Set *both* WristShutter and Garmin Connect to "Unrestricted" battery use.
  - Add both to Samsung's "Never sleeping apps" list.
- **Garmin Connect** must be installed and paired with the FR55.
- **Watch install:** build a `.prg` for the FR55 and copy it to `GARMIN/APPS` on the watch over USB.

---

## 9. Build, run and test

**Watch**
- Build and run in the Connect IQ simulator from VS Code by running "Monkey C: Run" from the command palette with the FR55 selected.
- Sideload with "Monkey C: Build for Device", then copy the `.prg` to the watch.

**Phone**
- Run `gradlew.bat installDebug` (Windows) or `./gradlew installDebug`, with the phone connected over USB or wireless debugging.
- View logs with `adb logcat -s WristShutter`.

**Simulator to phone testing**
- Use a debug build in `TETHERED` mode, with `adb forward tcp:7381 tcp:7381`.
- Verify the port against the current Garmin docs and document the exact steps in the README.

---

## 10. Phases

1. **Scaffold.**
   - Create both projects, generate the UUID, and write the README with the toolchain setup.
   - Check that both projects build from the command line.
2. **Comms.**
   - The watch sends `ping` and the phone acks it.
   - Test in the simulator with `TETHERED`, then on the real devices with `WIRELESS`.
3. **Camera control.**
   - Build the accessibility service, the node dump, and the strategy chain.
   - Discover the real IDs on the S26 Ultra, then wire up `shoot`, `video` and `flip`.
4. **Polish.**
   - Add the countdown, the vibration patterns and the settings screen.
   - Run a reliability soak: leave the phone idle for 30+ minutes and confirm it still responds. If it doesn't, add the foreground-service fallback.

## 11. Acceptance criteria

- With Samsung Camera open, pressing START on the watch takes a photo in under about 2 s, and the watch shows ✓.
- DOWN starts and stops video recording. UP flips the camera.
- With the camera closed, the watch shows `NO_CAM` within 4 s.
- It still works after the phone has been idle with the screen off for 30 minutes. Shooting with the screen off is not expected, but waking the phone and opening the camera must work without reopening WristShutter.
- After a One UI update breaks the shutter, it can be fixed by editing settings alone, with no rebuild needed.
- Both projects build from the command line with no Android Studio or .NET tooling.

## 12. Things Claude Code must verify, not assume

- The FR55's supported Connect IQ API level and memory limits, from the SDK device files
- The current Maven coordinates and version of the Connect IQ Mobile SDK
- The current Samsung Camera package name and its view IDs (found with the node dump on the device)
- The tethered-mode port and setup for simulator-to-phone testing
- Whether the background camera launch (7.8) is allowed
