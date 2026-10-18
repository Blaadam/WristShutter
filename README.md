# WristShutter

WristShutter turns a Garmin Forerunner 55 into a remote shutter for the stock **Samsung Camera** app on a Galaxy S26 Ultra. It replaces the S Pen Air Actions remote that Samsung removed. It is a personal, sideloaded tool. See [SPEC.md](SPEC.md) for the full spec.

```
FR55 watch app ──Connect IQ──▶ Garmin Connect ──▶ WristShutter (AccessibilityService)
      ▲                                              │ clicks buttons in com.sec.android.app.camera
      └────────────────── ack {ack, ok, err} ────────┘
```

| Watch button | Action |
|---|---|
| START | Take a photo, after the countdown if one is set. Press START again to cancel the countdown. |
| UP | Flip between the front and rear cameras |
| DOWN | Start or stop video recording |
| BACK | Cancel the countdown, or exit |
| Hold UP | Settings: countdown of 0, 3 or 5 s |

The watch shows the result: **✓** with one buzz means the phone did it. An error code with a double buzz means it failed (`NO_CAM`, `NOT_FOUND`, `GESTURE_FAIL`, `NO_A11Y`). **No reply** means no ack arrived within 4 s.

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
On macOS or Linux, use `./gradlew installDebug`. Install over USB, or pair wireless debugging first with `adb pair` and `adb connect`. `gradlew.bat assembleRelease` builds a release APK signed with the debug key, which is fine for sideloading.

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
6. Tap **Save button settings**, then use **Test shoot / Test video / Test flip**. Each one runs 3 s later so you have time to switch to the camera.
7. Turn the dump back off.

The app tries the ID first, then the description, then the tap position. When a One UI update breaks something, repeat this section. You don't need to rebuild the app.

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
| **No phone** even though Garmin Connect shows the watch connected and WristShutter says *Watch connected: yes* | The watch's Connect IQ messaging is stuck. This happened after a corrupted message crashed the phone app, and it then lasted through phone app reinstalls and a Garmin Connect restart. **Restart the watch** (hold LIGHT → Power Off). To confirm, run `adb shell dumpsys activity broadcasts history`: it shows no new `connectiq.INCOMING_MESSAGE` entries. |
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
| Samsung Camera package | `com.sec.android.app.camera` (v16.5.01.29) | `pm list packages` on the S26 Ultra |
| Shutter / record button | `center_button_container`, the **same view** in both modes; desc "Take picture" (Photo) or "Start recording" (Video) | Node dump. The defaults match it by description only, because clicking it by ID would do the wrong action in the other mode. |
| Flip button | `switch_camera_button`, desc "Switch to front camera" | Node dump |
| Stop button while recording | `stop_button`, desc "Stop recording", a separate view that exists only while recording | Node dump. Start and stop from the watch were tested 2026-10-18. |
| Background camera launch (§7.8) | not implemented, `NO_CAM` is returned | Verify on the device before adding it |
