@echo off
REM ===========================================================================
REM  BambuPrinterLan - one-click SIGNED (self-signed) release APK build.
REM
REM  Runs on a FRESH Windows 10/11 install with NOTHING pre-installed: it
REM  bootstraps JDK 17, the Android SDK (cmdline-tools + NDK + CMake + platform
REM  + build-tools) and Gradle into a local .toolchain\ folder, generates a
REM  gitignored self-signed keystore, and builds. Only needs built-in Windows
REM  curl + tar (present since Win10 1809).
REM
REM  Usage:  build-signed-apk.bat         (reuse system JDK/SDK if present)
REM          build-signed-apk.bat fresh   (ignore system tools; download all)
REM
REM  Output: app\build\outputs\apk\release\app-release.apk
REM  Re-runs are fast - every download/install step is skipped once done.
REM ===========================================================================
setlocal enabledelayedexpansion
cd /d "%~dp0"
set "TC=%~dp0.toolchain"
set "GRADLE_VER=8.11.1"
set "NDK_VER=27.0.12077973"
set "CMAKE_VER=3.22.1"
set "FRESH=0"
if /i "%~1"=="fresh" set "FRESH=1"
if not exist "%TC%" mkdir "%TC%"

echo(
echo === [1/7] JDK 17 ===============================================
set "JDK="
if "%FRESH%"=="0" (
  if defined JAVA_HOME if exist "%JAVA_HOME%\bin\keytool.exe" set "JDK=%JAVA_HOME%"
  if not defined JDK if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\keytool.exe" set "JDK=%ProgramFiles%\Android\Android Studio\jbr"
)
if not defined JDK for /d %%d in ("%TC%\jdk\jdk-*") do set "JDK=%%d"
if not defined JDK (
  echo Downloading Temurin JDK 17...
  curl -fL -o "%TEMP%\bdjdk.zip" "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse" || (echo JDK download failed & exit /b 1)
  if not exist "%TC%\jdk" mkdir "%TC%\jdk"
  tar -xf "%TEMP%\bdjdk.zip" -C "%TC%\jdk" || (echo JDK extract failed & exit /b 1)
  for /d %%d in ("%TC%\jdk\jdk-*") do set "JDK=%%d"
)
if not defined JDK ( echo ERROR: no JDK available & exit /b 1 )
set "JAVA_HOME=%JDK%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo JDK: %JAVA_HOME%

echo(
echo === [2/7] Android SDK location =================================
if "%FRESH%"=="1" set "ANDROID_SDK_ROOT="
if not defined ANDROID_SDK_ROOT (
  if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools" (
    set "ANDROID_SDK_ROOT=%LOCALAPPDATA%\Android\Sdk"
  ) else (
    set "ANDROID_SDK_ROOT=%TC%\sdk"
  )
)
if not exist "%ANDROID_SDK_ROOT%" mkdir "%ANDROID_SDK_ROOT%"
echo SDK: %ANDROID_SDK_ROOT%
> local.properties echo sdk.dir=%ANDROID_SDK_ROOT:\=\\%

echo(
echo === [3/7] SDK command-line tools ==============================
set "SDKM=%ANDROID_SDK_ROOT%\cmdline-tools\latest\bin\sdkmanager.bat"
if not exist "%SDKM%" (
  echo Downloading Android command-line tools...
  curl -fL -o "%TEMP%\bdcmdtools.zip" https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip || (echo cmdline-tools download failed & exit /b 1)
  if not exist "%ANDROID_SDK_ROOT%\cmdline-tools" mkdir "%ANDROID_SDK_ROOT%\cmdline-tools"
  tar -xf "%TEMP%\bdcmdtools.zip" -C "%ANDROID_SDK_ROOT%\cmdline-tools" || (echo cmdline-tools extract failed & exit /b 1)
  if exist "%ANDROID_SDK_ROOT%\cmdline-tools\cmdline-tools" ren "%ANDROID_SDK_ROOT%\cmdline-tools\cmdline-tools" latest
)
if not exist "%SDKM%" ( echo ERROR: sdkmanager missing after install & exit /b 1 )

echo(
echo === [4/7] SDK packages (NDK, CMake, platform, build-tools) =====
(for /l %%i in (1,1,20) do @echo y)| call "%SDKM%" --sdk_root="%ANDROID_SDK_ROOT%" --licenses >nul 2>&1
(for /l %%i in (1,1,20) do @echo y)| call "%SDKM%" --sdk_root="%ANDROID_SDK_ROOT%" --install "platform-tools" "platforms;android-35" "build-tools;35.0.0" "ndk;%NDK_VER%" "cmake;%CMAKE_VER%"
if errorlevel 1 ( echo ERROR: sdkmanager install failed & exit /b 1 )

echo(
echo === [5/7] Gradle %GRADLE_VER% =================================
if not exist "gradlew.bat" (
  set "GHOME=%TC%\gradle-%GRADLE_VER%\gradle-%GRADLE_VER%"
  if not exist "!GHOME!\bin\gradle.bat" (
    echo Downloading Gradle %GRADLE_VER%...
    curl -fL -o "%TEMP%\bdgradle.zip" https://services.gradle.org/distributions/gradle-%GRADLE_VER%-bin.zip || (echo Gradle download failed & exit /b 1)
    if not exist "%TC%\gradle-%GRADLE_VER%" mkdir "%TC%\gradle-%GRADLE_VER%"
    tar -xf "%TEMP%\bdgradle.zip" -C "%TC%\gradle-%GRADLE_VER%" || (echo Gradle extract failed & exit /b 1)
  )
  call "!GHOME!\bin\gradle.bat" wrapper --gradle-version %GRADLE_VER% --distribution-type bin || (echo wrapper bootstrap failed & exit /b 1)
)

echo(
echo === [6/7] Self-signed keystore (gitignored) ===================
set "KS=%~dp0.signing\bambuprinterlan-release.jks"
set "KSPASS=bambuprinterlan-local"
if not exist "%KS%" (
  if not exist "%~dp0.signing" mkdir "%~dp0.signing"
  echo Generating self-signed keystore...
  "%JAVA_HOME%\bin\keytool" -genkeypair -keystore "%KS%" -alias bambuprinterlan -keyalg RSA -keysize 2048 -validity 10000 -storepass %KSPASS% -keypass %KSPASS% -dname "CN=BambuPrinterLan, OU=Release, O=BambuPrinterLan, C=US" || (echo keytool failed & exit /b 1)
)
set "RELEASE_STORE_FILE=%KS%"
set "RELEASE_STORE_PASSWORD=%KSPASS%"
set "RELEASE_KEY_ALIAS=bambuprinterlan"
set "RELEASE_KEY_PASSWORD=%KSPASS%"

echo(
echo === [7/7] Build signed release APK ============================
cd /d "%~dp0"
call "%~dp0gradlew.bat" :app:assembleRelease --no-daemon --stacktrace
if errorlevel 1 ( echo ERROR: gradle build failed & exit /b 1 )

echo(
echo ============================================================
echo  DONE. Signed APK:
echo    %~dp0app\build\outputs\apk\release\app-release.apk
"%JAVA_HOME%\bin\keytool" -printcert -jarfile "%~dp0app\build\outputs\apk\release\app-release.apk" 2>nul | findstr /C:"SHA256:"
echo ============================================================
endlocal
