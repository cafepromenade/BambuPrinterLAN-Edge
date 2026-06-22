---
name: run-bambuprinterlan
description: Build, launch, install, and drive the Bambu Printer LAN Android app on an emulator — boot an AVD, install the APK, start it, take screenshots, and tap/type to drive the UI. Use when asked to run, launch, build, install, screenshot, or test the Android app (com.bambuprinterlan.app).
---

# Run Bambu Printer LAN (Android)

A Kotlin + Jetpack Compose Android app (package `com.bambuprinterlan.app`,
launcher `.MainActivity`) with an NDK/CMake native slicer engine. You can't
"click a button" from markdown, so the harness is **`driver.ps1`** — a
PowerShell wrapper around `adb` + the emulator that boots an AVD, installs the
debug APK, launches the app, screenshots, and sends taps/keys.

Environment here is **Windows + PowerShell 7 (`pwsh`)** with Android Studio's
SDK. All paths below are relative to the app root (this directory's
grandparent's parent), e.g. the driver is
`.claude/skills/run-bambuprinterlan/driver.ps1`.

## Prerequisites

- **Android SDK** at `%LOCALAPPDATA%\Android\Sdk` with `platform-tools` (adb),
  `emulator`, `build-tools`, **NDK `27.0.12077973`**, and **CMake `3.22.1`**
  (the native engine needs the exact NDK/CMake; install via SDK Manager).
- **JDK 17** — use Android Studio's bundled JBR:
  `C:\Program Files\Android\Android Studio\jbr` (the driver sets `JAVA_HOME`).
- **An AVD.** This machine has `Pixel_10_Pro_XL` (1344×2992). Override with
  `$env:BPL_AVD`.

## Run (agent path) — the driver

One-shot smoke test (boot AVD if needed → install → launch → screenshot):

```powershell
pwsh -NoProfile -File .claude/skills/run-bambuprinterlan/driver.ps1 all
```

That writes `.claude/skills/run-bambuprinterlan/shots/launch.png`. **The first
screenshot catches the animated startup intro + first-run onboarding + the
POST_NOTIFICATIONS dialog** — it looks dark/blank, that's expected, not a
crash. Drive through it, then screenshot the real UI:

```powershell
$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$adb shell input tap 672 1662   # "Allow" on the notifications dialog
$adb shell input tap 672 2048   # "Skip 略過" the onboarding
pwsh -NoProfile -File .claude/skills/run-bambuprinterlan/driver.ps1 shot prepare-tab
```

Navigate tabs (bottom nav has 5 items at x ≈ 134/403/672/941/1210, y ≈ 2900):

```powershell
$adb shell input tap 672 2900   # Device tab (3rd of 5)
pwsh -NoProfile -File .claude/skills/run-bambuprinterlan/driver.ps1 shot device-tab
```

Driver commands: `build`, `boot`, `install`, `launch`, `shot [name]`,
`tap <x> <y>`, `text "<s>"`, `key <code>`, `stop`, `all`. Screenshots land in
`shots/`.

## Build only

```powershell
pwsh -NoProfile -File .claude/skills/run-bambuprinterlan/driver.ps1 build
```

This sets `JAVA_HOME`, writes `local.properties` if missing, and runs
`gradlew.bat :app:assembleDebug`. The raw command (what the driver runs):

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

## Run (human path)

Open the project in Android Studio → Run ▶. For a distributable signed APK use
`build-signed-apk.bat` at the app root (generates a self-signed keystore under
`.signing/` and runs `assembleRelease`). Headless, the human path is useless —
use the driver.

## Test

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
```

## Gotchas (battle scars)

- **First launch is not the app yet.** `StartupIntro` (animated) + first-run
  `OnboardingScreen` (4 bilingual cards, "Skip 略過") + an Android
  POST_NOTIFICATIONS system dialog stack on top. A screenshot taken right after
  launch is the intro animation, not a crash. Tap Allow + Skip first.
- **`local.properties` needs forward slashes.** `sdk.dir=C:/Users/.../Sdk` —
  Java mangles single backslashes into "invalid file path". The driver writes
  it correctly.
- **Native engine pins NDK `27.0.12077973` + CMake `3.22.1`.** Without them the
  `:engine:jni` CMake build fails. `build-signed-apk.bat` installs them; plain
  Android Studio does not.
- **Release builds fail `lintVitalRelease`** on `InvalidFragmentVersionForActivityResult`
  (a false positive from `registerForActivityResult` in an Activity). Already
  disabled in `app/build.gradle.kts`; debug builds skip lint-vital entirely, so
  a debug build can pass while a release build breaks.
- **Sign release APKs with v1+v2+v3.** v2-only caused "App not installed" on
  some devices/installers. `build-signed-apk.bat` + `build.gradle.kts` enable
  all three.
- **Don't redirect `screencap` through PowerShell** (`adb ... > x.png` corrupts
  the binary with CRLF). The driver does `adb shell screencap -p /sdcard/x.png`
  then `adb pull`.
- **Use `pwsh` (7), not Windows PowerShell 5.1.** 5.1 treats gradle's native
  stderr as terminating and aborts mid-build.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `adb devices` empty | `driver.ps1 boot` (starts `Pixel_10_Pro_XL`, waits for `sys.boot_completed`). First boot takes a few minutes. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | A prior install with a different signature — `adb uninstall com.bambuprinterlan.app`, then `driver.ps1 install`. |
| Build: "SDK location not found" | Missing `local.properties` — `driver.ps1 build` writes it, or create `sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk`. |
| CMake/NDK error in `:engine:jni` | Install NDK `27.0.12077973` + CMake `3.22.1` via SDK Manager. |
| Screenshot is dark/blank | It's the startup-intro animation — tap through Allow + Skip, then `shot`. |
