<#
  driver.ps1 — build, launch, and drive the Bambu Printer LAN Android app.

  Usage (from the app root):
    pwsh .claude/skills/run-bambuprinterlan/driver.ps1 <command> [args]

  Commands:
    build            assembleDebug (sets JAVA_HOME to Android Studio jbr)
    boot             start the AVD if no device is attached, wait for boot
    install          adb install -r the debug APK
    launch           start MainActivity
    shot [name]      screenshot -> shots/<name>.png (default: shot)
    tap <x> <y>      input tap
    text "<s>"       input text
    key <code>       input keyevent (e.g. 4 = BACK, 3 = HOME)
    all              boot + install + launch + shot   (the smoke test)
    stop             force-stop the app

  Env overrides: BPL_AVD (AVD name), BPL_APK (apk path).
#>
param(
  [Parameter(Position = 0)] [string] $cmd = "all",
  [Parameter(Position = 1, ValueFromRemainingArguments = $true)] $rest
)
$ErrorActionPreference = "Stop"

$Sdk  = "$env:LOCALAPPDATA\Android\Sdk"
$Adb  = "$Sdk\platform-tools\adb.exe"
$Emu  = "$Sdk\emulator\emulator.exe"
$Avd  = if ($env:BPL_AVD) { $env:BPL_AVD } else { "Pixel_10_Pro_XL" }
$Pkg  = "com.bambuprinterlan.app"
$Act  = "$Pkg/com.bambuprinterlan.app.MainActivity"
$Root = (Resolve-Path "$PSScriptRoot\..\..\..").Path
$Apk  = if ($env:BPL_APK) { $env:BPL_APK } else { "$Root\app\build\outputs\apk\debug\app-debug.apk" }
$Shots = "$PSScriptRoot\shots"

function Has-Device { (& $Adb devices | Select-String -Pattern "device$") -ne $null }

function Do-Build {
  $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
  $env:ANDROID_SDK_ROOT = $Sdk
  if (-not (Test-Path "$Root\local.properties")) {
    "sdk.dir=$($Sdk -replace '\\','/')" | Out-File -Encoding ascii "$Root\local.properties"
  }
  & "$Root\gradlew.bat" ":app:assembleDebug" "--no-daemon" "--console=plain"
  if ($LASTEXITCODE -ne 0) { throw "build failed" }
}

function Do-Boot {
  if (Has-Device) { "device already attached"; return }
  if (-not (Get-Process -Name qemu* -ErrorAction SilentlyContinue)) {
    Start-Process -FilePath $Emu -ArgumentList '-avd', $Avd, '-no-boot-anim', '-no-snapshot-save' -WindowStyle Minimized
  }
  & $Adb wait-for-device
  for ($i = 0; $i -lt 120; $i++) {
    $b = (& $Adb shell getprop sys.boot_completed 2>$null).Trim()
    if ($b -eq "1") { "boot complete"; & $Adb shell input keyevent 82 | Out-Null; return }
    Start-Sleep -Seconds 2
  }
  throw "emulator did not finish booting"
}

function Do-Install {
  if (-not (Test-Path $Apk)) { Do-Build }
  & $Adb install -r -t $Apk
  if ($LASTEXITCODE -ne 0) { throw "install failed" }
}

function Do-Launch { & $Adb shell am start -n $Act | Out-Null; Start-Sleep -Seconds 3 }

function Do-Shot([string]$name = "shot") {
  if (-not (Test-Path $Shots)) { New-Item -ItemType Directory -Force $Shots | Out-Null }
  & $Adb shell screencap -p /sdcard/_bpl.png | Out-Null
  & $Adb pull /sdcard/_bpl.png "$Shots\$name.png" | Out-Null
  & $Adb shell rm /sdcard/_bpl.png | Out-Null
  "screenshot: $Shots\$name.png"
}

switch ($cmd) {
  "build"   { Do-Build }
  "boot"    { Do-Boot }
  "install" { Do-Install }
  "launch"  { Do-Launch }
  "shot"    { Do-Shot ($(if ($rest) { $rest[0] } else { "shot" })) }
  "tap"     { & $Adb shell input tap $rest[0] $rest[1] }
  "text"    { & $Adb shell input text $rest[0] }
  "key"     { & $Adb shell input keyevent $rest[0] }
  "stop"    { & $Adb shell am force-stop $Pkg }
  "all"     { Do-Boot; Do-Install; Do-Launch; Do-Shot "launch" }
  default   { Get-Content $PSCommandPath -TotalCount 22 | Select-Object -Skip 1 }
}
