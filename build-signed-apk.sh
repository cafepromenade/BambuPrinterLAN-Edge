#!/usr/bin/env bash
# ============================================================================
#  Bambu Printer LAN — build a SIGNED (self-signed) release APK in one command.
#  Works on a fresh Linux/macOS: bootstraps JDK 17, the Android SDK + NDK +
#  CMake + platform/build-tools, and uses the committed Gradle wrapper. Generates
#  a gitignored self-signed keystore and builds. Needs curl + tar/unzip.
#
#  Usage:  ./build-signed-apk.sh
#  Output: app/build/outputs/apk/release/app-release.apk
# ============================================================================
set -euo pipefail
cd "$(dirname "$0")"
TC="$PWD/.toolchain"
NDK_VER="27.0.12077973"
CMAKE_VER="3.22.1"
mkdir -p "$TC"

case "$(uname -s)" in
  Darwin) OS=mac;   JDK_OS=mac;   SDK_OS=mac ;;
  *)      OS=linux; JDK_OS=linux; SDK_OS=linux ;;
esac

echo "== [1/6] JDK 17 =="
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/keytool" ]; then
  JDK="$JAVA_HOME"
elif command -v keytool >/dev/null 2>&1 && command -v javac >/dev/null 2>&1; then
  JDK="$(dirname "$(dirname "$(command -v javac)")")"
else
  JDK="$(find "$TC/jdk" -maxdepth 1 -type d -name 'jdk-*' 2>/dev/null | head -1 || true)"
  if [ -z "$JDK" ]; then
    echo "Downloading Temurin JDK 17..."
    mkdir -p "$TC/jdk"
    curl -fL "https://api.adoptium.net/v3/binary/latest/17/ga/${JDK_OS}/x64/jdk/hotspot/normal/eclipse" -o "$TC/jdk.tgz"
    tar -xzf "$TC/jdk.tgz" -C "$TC/jdk"
    JDK="$(find "$TC/jdk" -maxdepth 2 -type d -name 'Contents' -prune -o -type d -name 'jdk-*' -print | head -1)"
    [ "$OS" = mac ] && JDK="$(find "$TC/jdk" -maxdepth 3 -type d -name 'Home' | head -1)"
  fi
fi
export JAVA_HOME="$JDK"
export PATH="$JAVA_HOME/bin:$PATH"
echo "JDK: $JAVA_HOME"

echo "== [2/6] Android SDK =="
if [ -z "${ANDROID_SDK_ROOT:-}" ]; then
  if [ -d "$HOME/Android/Sdk/platform-tools" ]; then ANDROID_SDK_ROOT="$HOME/Android/Sdk"
  else ANDROID_SDK_ROOT="$TC/sdk"; fi
fi
mkdir -p "$ANDROID_SDK_ROOT"
echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties
echo "SDK: $ANDROID_SDK_ROOT"

echo "== [3/6] SDK command-line tools =="
SDKM="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SDKM" ]; then
  echo "Downloading Android command-line tools..."
  curl -fL "https://dl.google.com/android/repository/commandlinetools-${SDK_OS}-11076708_latest.zip" -o "$TC/cmdtools.zip"
  mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
  (cd "$ANDROID_SDK_ROOT/cmdline-tools" && rm -rf latest tmp && mkdir tmp && cd tmp && (unzip -q "$TC/cmdtools.zip" || tar -xf "$TC/cmdtools.zip") && mv cmdline-tools ../latest && cd .. && rm -rf tmp)
fi

echo "== [4/6] SDK packages (NDK, CMake, platform, build-tools) =="
yes 2>/dev/null | "$SDKM" --sdk_root="$ANDROID_SDK_ROOT" --licenses >/dev/null 2>&1 || true
"$SDKM" --sdk_root="$ANDROID_SDK_ROOT" --install \
  "platform-tools" "platforms;android-35" "build-tools;35.0.0" "ndk;$NDK_VER" "cmake;$CMAKE_VER"

echo "== [5/6] Self-signed keystore (gitignored) =="
KS="$PWD/.signing/release.jks"
KSPASS="bambuprinterlan-local"
if [ ! -f "$KS" ]; then
  mkdir -p "$PWD/.signing"
  keytool -genkeypair -keystore "$KS" -alias bambuprinterlan -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass "$KSPASS" -keypass "$KSPASS" \
    -dname "CN=Bambu Printer LAN, OU=Release, O=Bambu Printer LAN, C=US"
fi
export RELEASE_STORE_FILE="$KS"
export RELEASE_STORE_PASSWORD="$KSPASS"
export RELEASE_KEY_ALIAS="bambuprinterlan"
export RELEASE_KEY_PASSWORD="$KSPASS"

echo "== [6/6] Build signed release APK =="
chmod +x ./gradlew
./gradlew :app:assembleRelease --no-daemon --stacktrace

APK="app/build/outputs/apk/release/app-release.apk"
echo
echo "============================================================"
echo " DONE. Signed APK: $PWD/$APK"
keytool -printcert -jarfile "$APK" 2>/dev/null | grep "SHA256:" || true
echo "============================================================"
