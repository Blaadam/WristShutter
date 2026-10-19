# WristShutter

WristShutter turns a Garmin Forerunner 55 into a remote shutter for the stock **Samsung Camera** app on a Galaxy S26 Ultra. It replaces the S Pen Air Actions remote that Samsung removed. It is a personal, sideloaded tool. See [SPEC.md](SPEC.md) for the full spec.

```
FR55 watch app ──Connect IQ──▶ Garmin Connect ──▶ WristShutter (AccessibilityService)
      ▲                                              │ clicks buttons in com.sec.android.app.camera
      └────────────────── ack {ack, ok, err} ────────┘
```

| Watch button | Action |
|---|---|
| START | The shutter. In Photo mode it takes a photo; in Video mode it starts recording, or stops it if already recording. Starting waits for the countdown if one is set; stopping is immediate. Burst (3 or 5 photos, 2 s apart) applies in Photo mode only. Press START again to cancel a countdown or burst. |
| UP | Flip between the front and rear cameras |
| DOWN | Mode: switch the camera between Photo and Video. Not possible while recording. |
| BACK | Cancel the countdown or burst, or exit |
| Hold UP | Settings: **Countdown** (Off, 3 or 5 s) and **Burst** (Off, 3 or 5 shots) change each time you select them. **Zoom** opens a list of lenses (0.6×, 1×, 2×, 3×, 5×, 10×) and sends the one you pick. |

The labels beside the buttons follow the camera: START reads **Shoot**, **Rec** or **Stop** (in red while recording), and DOWN reads **To video** or **To photo**. The phone reports the camera's mode in every reply, so the labels update after each command. If you change mode on the phone itself, they catch up at the next command.

If Samsung Camera is closed, START and DOWN open it in **Photo mode**, the baseline. Switching mode works by re-sending Android's photo or video camera intent, which Samsung Camera follows while open; no button text is read, so it works in any phone language.

Zoom presses Samsung Camera's lens buttons (`zoom_lens_text_button_main`) by the number in their label, so it works in any phone language. It needs the rear camera, because the front camera has no lens buttons.

The watch shows the result: **✓** with one buzz means the phone did it. The number under the ✓ is the round trip, from the button press to the phone's ack. Red text with a double buzz means it failed:

| Watch shows | Phone code | Meaning |
|---|---|---|
| No shutter | `NOT_FOUND` on shoot | No shutter found: the camera is in a mode other than Photo or Video (e.g. Portrait), or the IDs changed |
| Recording | `RECORDING` on mode | Stop recording before switching mode |
| Mode failed | `MODE_FAIL` | The camera didn't reach the other mode within 8 s |
| No button | `NOT_FOUND` on flip | The flip button wasn't found |
| No zoom | `NOT_FOUND` on zoom | There's no lens button for that level (for example on the front camera) |
| Open camera | `NO_CAM` | Samsung Camera isn't in the foreground |
| Tap failed | `GESTURE_FAIL` | The button was found but the click was refused |
| Too fast | `BUSY` | The same command arrived again within 1 s and was ignored |
| No reply | (none) | No ack within 4 s |

While a command waits for its ack, the watch ignores further button presses. A double press therefore can't stop a recording that just started.

---

## Repo layout

```
watch/   Monkey C watch-app (manifest.xml, monkey.jungle, source/, resources/)
phone/   Android Gradle project, Kotlin DSL (gradlew.bat, app/)
```

The shared app UUID is `c9c13a59-0bd5-4c29-b2e2-a003d8158db8`. It is defined in [watch/manifest.xml](watch/manifest.xml), written there without dashes, and in [Protocol.kt](phone/app/src/main/java/com/wristshutter/Protocol.kt). If you change it, change both.

---

## 1. Toolchain setup (Windows, VS Code only)

You don't need Android Studio or .NET.

### JDK
Install JDK 17 or newer, for example Temurin, and make sure `java -version` works. Gradle 9.8 runs on JDK 17 through 25.

### Android command-line tools
1. Download **Command line tools only** for Windows from <https://developer.android.com/studio#command-line-tools-only>.
2. Extract it so the folder ends up at `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest\` (that folder contains `bin\`, `lib\`, and so on).
3. Set the environment variables, then open a new terminal:
   ```bat
   setx ANDROID_HOME "%LOCALAPPDATA%\Android\Sdk"
   setx PATH "%PATH%;%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest\bin;%LOCALAPPDATA%\Android\Sdk\platform-tools"
   ```
4. Install the packages. Current command-line tools (build 16111833) deprecate `sdkmanager` in favour of the new `android sdk` CLI. That CLI uses `/` rather than `;` in package names and needs no separate licence step:
   ```bat
   android --sdk=%LOCALAPPDATA%\Android\Sdk sdk install platform-tools platforms/android-37.0 build-tools/37.0.0
   ```
   `android sdk list --all "platforms*"` shows what's available.

If you'd rather not set `ANDROID_HOME`, create `phone\local.properties` containing `sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk`. This file is gitignored, and on this machine it already exists.

### Connect IQ SDK and VS Code extension
1. Install the **Connect IQ SDK Manager** from <https://developer.garmin.com/connect-iq/sdk/>, sign in with your Garmin account, and download:
   - the latest SDK, then set it as the active SDK
   - the **Forerunner 55** device (Devices tab)
2. In VS Code, install the **Monkey C** extension (`garmin.monkey-c`).
3. Run **Monkey C: Verify Installation** from the command palette.
4. Run **Monkey C: Generate a Developer Key** and save it somewhere outside the repo, for example `%USERPROFILE%\.garmin\developer_key.der`. The extension remembers its location.

#### FR55 limits (spec §12)
`%APPDATA%\Garmin\ConnectIQ\Devices\fr55\compiler.json` (SDK 9.2.0) contains the following. Check it again after big SDK updates.
- **API level 3.4**: `deviceGroup` is "API level 3.4", and the firmware part numbers report Connect IQ 3.4.2 to 3.4.6. `minApiLevel="3.4.0"` matches this.
- **watchApp memory limit: 131072 bytes (128 KB).** The simulator's **File → View Memory** shows actual use.
- Launcher icon 35×35, 8-colour palette, 208×208 MIP screen.

A developer key generated with OpenSSL is at `%USERPROFILE%\.garmin\developer_key.der`, outside the repo. Point the extension at it when it asks (setting `monkeyC.developerKeyPath`). Don't commit it.

---

## 2. Build

### Phone
```bat
cd phone
gradlew.bat assembleDebug
gradlew.bat installDebug
```
On macOS or Linux, use `./gradlew installDebug`. Install over USB, or pair wireless debugging first with `adb pair` and `adb connect`.

#### Release build (for everyday use)
The release build leaves out the debug-only parts: the TETHERED switch and the `INTERNET` permission. Sign it with your own key, created once:

```bat
cd phone
keytool -genkeypair -v -keystore wristshutter.jks -alias wristshutter -keyalg RSA -keysize 4096 -validity 10000
```

`keytool` comes with the JDK. Then create `phone\keystore.properties`:

```properties
storeFile=wristshutter.jks
storePassword=<the password you chose>
keyAlias=wristshutter
keyPassword=<the password you chose>
```

Both files are gitignored. Back them up somewhere safe: an update only installs over the top if it's signed with the **same** key. Then build and install:

```bat
gradlew.bat installRelease
```

Without `keystore.properties`, release falls back to the debug key. Switching keys means Android won't update the installed app: uninstall it first, then redo **Allow restricted settings** and turn the accessibility service back on.

Read the logs with:
```bat
adb logcat -s WristShutter
```

### Watch
- **Simulator:** open the `watch` folder in VS Code, then run **Monkey C: Run** and pick *Forerunner 55*.
- **Device build:** run **Monkey C: Build for Device**, choose *Forerunner 55*, and the extension writes a `.prg`.
- **Command line** (uses the SDK's `bin` folder; the path depends on the SDK version you installed):
  ```bat
  cd watch
  "%APPDATA%\Garmin\ConnectIQ\Sdks\connectiq-sdk-win-9.2.0-2026-06-09-92a1605b2\bin\monkeyc.bat" -d fr55 -f monkey.jungle -o bin\WristShutter.prg -y "%USERPROFILE%\.garmin\developer_key.der"
  ```

---

## 3. Install

### Watch
1. Plug the FR55 in over USB. It mounts as a drive, or as an MTP device called *GARMIN*.
2. Copy `WristShutter.prg` into `GARMIN\APPS\`.
3. Eject the watch. The app appears under the watch's apps list (hold UP, then *Apps*, on most firmware).

### Phone: sideloading gotchas

> [!IMPORTANT]
> **Restricted settings.** Android blocks sideloaded apps from turning on accessibility. The toggle is greyed out, or a "Restricted setting" dialog appears. To unlock it:
> 1. Try to enable the service once (Settings → Accessibility → Installed apps → WristShutter watch remote) so the restriction is triggered.
> 2. Go to **Settings → Apps → WristShutter → ⋮ (top right) → Allow restricted settings**, and confirm with your PIN or fingerprint.
> 3. Go back to Accessibility and turn **WristShutter watch remote** on.
>
> You may need to repeat this after reinstalling the app with a different signing key.

**Battery.** Samsung kills background apps aggressively. Do all of the following for **both WristShutter and Garmin Connect**:
- Settings → Apps → *app* → Battery → **Unrestricted**
- Settings → Battery → Background usage limits → **Never sleeping apps** → add both apps
- In WristShutter, tap **Ignore battery optimisation**

**Garmin Connect** must be installed, signed in, and paired with the FR55.

---

## 4. Find the camera button IDs (one-time, and after One UI updates)

The view IDs in Samsung Camera aren't documented and change between One UI versions, so the app doesn't guess them. To find them:

1. In WristShutter, turn on **Dump camera UI to logcat**.
2. On the PC, run:
   ```bat
   adb logcat -c
   adb logcat -s WristShutter
   ```
3. Open Samsung Camera. Every time the window changes, and at most every 3 s while it updates, the service logs one line per node:
   ```
   id=com.sec.android.app.camera:id/xxxx class=ImageView text=- desc="Take picture" clickable=true bounds=[...]
   ```
   Switch to Video mode and to the front camera so that every button you need appears in a dump.
4. Find the shutter, record and flip buttons. Look for `clickable=true` nodes with a matching `desc` and plausible `bounds`.
5. Paste the `id=` value (either the full ID or just the part after `:id/`) into **View IDs** for each command. If a button has no ID, put its `desc` text under **Content descriptions** instead. As a last resort, set **Fallback tap X/Y** as a percentage of the screen size, calculated from `bounds`.
6. Tap **Save button settings**, then use **Test shoot / Test mode / Test flip**. Each one runs 3 s later so you have time to switch to the camera.
7. Turn the dump back off.

The app tries the ID first, then the description, then the tap position. When a One UI update breaks something, repeat this section. You don't need to rebuild the app.

**Without a PC:** the **Recent commands** list in WristShutter shows the last 20 commands from the watch or the test buttons. For each one it shows the result and which rule pressed the button (for example `id stop_button` or `desc 'Take picture'`). **Reset button settings to defaults** removes your edits. Only values that differ from the defaults are stored, so improved defaults in later app versions still apply.

---

## 5. Simulator ↔ phone testing (TETHERED)

The Connect IQ SDK on the phone can talk to the simulator on your PC over ADB instead of going through Garmin Connect. Its default ADB port is **7381**. That value comes from the bytecode of `AdbConnection` in `ciq-companion-app-sdk` 2.2.0, and the WristShutter code sets it explicitly with `setAdbPort`.

1. Install a **debug** build (`gradlew.bat installDebug`), because the toggle only exists in debug builds.
2. Connect the phone over USB or wireless debugging, then run:
   ```bat
   adb forward tcp:7381 tcp:7381
   adb forward --list
   ```
3. In WristShutter, turn on **TETHERED mode**. The service restarts its Connect IQ connection straight away.
4. In the simulator's menu bar, choose **adb Connection → Start**. Without this, the simulator shows "There is no data connection" and the watch shows **No phone**.
5. In VS Code, run **Monkey C: Run** with the Forerunner 55. The watch should show **Phone OK**.

TETHERED needs the `INTERNET` permission, because the SDK opens a server socket on the phone. Only the debug build has it (`app/src/debug/AndroidManifest.xml`). Without it the socket fails with `EPERM`, and the SDK's `AdbConnection` thread crashes the whole app with a NullPointerException.

Known issue, which affects the **installed SDK 9.2.0**: in SDK 9.1.0 and 9.2.0, the simulator can crash on the first `Communications.transmit()` over a tethered connection. Garmin acknowledges this ([forum bug report](https://forums.garmin.com/developer/connect-iq/i/bug-reports/connect-iq-simulator-9-1-0-crashes-on-first-communications-transmit-over-tethered-connection)). If it happens, use a newer SDK, or test on the real watch with TETHERED off.

**Observed here on 2026-10-18 (SDK 9.2.0):** the TCP chain came up (simulator → adb → phone on port 7381), but the watch app froze on "Connecting..." at its first `transmit`. Its own 4 s timer never fired, and the phone received nothing. Treat simulator-to-phone testing as blocked on 9.2.0.

Turn TETHERED off again before using the real watch.

---

## 6. Troubleshooting

| Symptom | Check |
|---|---|
| Watch shows **No phone** at start | Garmin Connect is running and the watch is connected. In WristShutter, *Connect IQ SDK ready* is yes and *Watch connected* shows the FR55. The accessibility service is on. |
| **Restart watch?**, or **No phone** even though Garmin Connect shows the watch connected and WristShutter says *Watch connected: yes* | The watch's Connect IQ messaging is stuck. The watch shows "Restart watch?" after 3 sends in a row get no ack, counting across app launches. This happened after a corrupted message crashed the phone app, and it then lasted through phone app reinstalls and a Garmin Connect restart. **Restart the watch** (hold LIGHT → Power Off). To confirm, run `adb shell dumpsys activity broadcasts history`: it shows no new `connectiq.INCOMING_MESSAGE` entries. |
| **No reply** | Battery settings (above). Run `adb logcat -s WristShutter` and look for `From <watch>: ...` lines. If there are none, the message never reached the app. |
| `NO_CAM` with the camera open | Samsung Camera's package isn't `com.sec.android.app.camera` on this firmware. Check with `adb shell dumpsys window \| findstr mCurrentFocus`. |
| `NOT_FOUND` | IDs or descriptions are wrong or missing. Redo section 4. |
| `GESTURE_FAIL` | The node was found but the click was refused. Try the fallback tap position. |
| `NO_A11Y` (test buttons) | The accessibility service isn't running. Re-enable it, and see Restricted settings above. |
| Stops working after a while | Battery settings. If it still dies, Phase 4's fallback is to move the Connect IQ listener into a `connectedDevice` foreground service. |

---

## Verified facts (2026-10-18)

| Item | Value | Source |
|---|---|---|
| FR55 Connect IQ API level | 3.4 (firmware 3.4.2 to 3.4.6) | `Devices\fr55\compiler.json`, SDK 9.2.0 |
| FR55 watch-app memory limit | 131072 bytes | `Devices\fr55\compiler.json` |
| Connect IQ Mobile SDK | `com.garmin.connectiq:ciq-companion-app-sdk:2.2.0` | Maven Central search, POM |
| Tethered ADB port | 7381 (default, settable from 1025 to 65535) | SDK 2.2.0 bytecode |
| AGP / Gradle / Kotlin | 9.4.1 / 9.8.1 / 2.4.21 | Google Maven, services.gradle.org, Maven Central |
| compileSdk / targetSdk | 37 (AGP 9.4's maximum) | AGP 9.4 release notes |
| Samsung Camera package | `com.sec.android.app.camera`: v16.5.01.29 (One UI 8), then v17.0.00.43 (One UI 9, Android 17) | `pm list packages` on the S26 Ultra. All IDs below were unchanged by the One UI 9 update (2026-10-19). |
| Shutter / record button | `center_button_container`, the **same view** in both modes; desc "Take picture" (Photo) or "Start recording" (Video) | Node dump. Clicking it by ID alone would do the wrong action in the other mode. |
| Current mode | A layer named after the mode: `shooting_mode_photo_main` or `shooting_mode_video_main` | `uiautomator dump`, One UI 9. Built-in rule: shoot presses the center button when either layer is present (`stop_button` first while recording), and the mode reported to the watch comes from these IDs. This works in any language; the English descriptions are only a fallback. |
| Flip button | `switch_camera_button`, desc "Switch to front camera" | Node dump |
| Zoom buttons | `zoom_lens_text_button_main` (×7), desc "Zoom 0.6, Button" … "Zoom 100, Button" | `uiautomator dump`, One UI 9 |
| Stop button while recording | `stop_button`, desc "Stop recording", a separate view that exists only while recording | Node dump. Start and stop from the watch were tested 2026-10-18. |
| Background camera launch (§7.8) | Implemented behind the "Open Samsung Camera if it's closed" toggle (on by default). Shoot and mode open it in Photo mode, or the secure camera when the phone is locked. Mode switches by re-sending the photo/video camera intent while it's open. Screen off still returns `NO_CAM`. | **Allowed** on One UI 9 / Android 17: logcat shows `BAL_ALLOW_TOKEN` (2026-10-19). A cold start took about 5 s, longer than the watch's 4 s timeout, so the phone first sends an interim `wait` ack. The watch then shows "Opening camera" and waits up to 10 s. |
