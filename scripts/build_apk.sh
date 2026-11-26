#!/usr/bin/env bash
# Build a debug APK into dist/Airbomb-Al-debug.apk.
#
#   ./scripts/build_apk.sh            # online (Gradle resolves dependencies)
#   ./scripts/build_apk.sh --offline  # use the local Gradle cache
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/android"

GRADLE_ARGS=(:app:assembleDebug --no-daemon --console=plain)
for arg in "$@"; do
    case "$arg" in
        --offline) GRADLE_ARGS+=(--offline) ;;
        *) echo "unknown option: $arg" >&2; exit 2 ;;
    esac
done

# JDK 17+ is required by AGP 8.5.
if [ -z "${JAVA_HOME:-}" ]; then
    for candidate in /usr/lib/jvm/java-21-openjdk-* /usr/lib/jvm/java-17-openjdk-*; do
        [ -d "$candidate" ] && JAVA_HOME="$candidate" && break
    done
fi
[ -n "${JAVA_HOME:-}" ] && export JAVA_HOME

# Android SDK: env, local.properties, or common NetHunter path.
if [ -z "${ANDROID_HOME:-}" ]; then
    if [ -f local.properties ]; then
        ANDROID_HOME="$(sed -n 's/^sdk.dir=//p' local.properties | head -1)"
    fi
    [ -n "${ANDROID_HOME:-}" ] || ANDROID_HOME=/opt/android-sdk
fi
[ -d "$ANDROID_HOME" ] || { echo "Android SDK not found (set ANDROID_HOME)" >&2; exit 1; }
export ANDROID_HOME

GRADLE="${GRADLE:-}"
if [ -z "$GRADLE" ]; then
    if command -v gradle >/dev/null 2>&1; then
        GRADLE="$(command -v gradle)"
    elif [ -x /opt/gradle-8.9/bin/gradle ]; then
        GRADLE=/opt/gradle-8.9/bin/gradle
    else
        GRADLE="$ROOT/android/gradlew"
    fi
fi

# Offline NetHunter installs ship aapt2 inside build-tools; point AGP at it.
AAPT2="$ANDROID_HOME/build-tools/34.0.0/aapt2"
[ -x "$AAPT2" ] && GRADLE_ARGS+=("-Pandroid.aapt2FromMavenOverride=$AAPT2")

echo "==> gradle ${GRADLE_ARGS[*]}"
"$GRADLE" "${GRADLE_ARGS[@]}"

APK="app/build/outputs/apk/debug/app-debug.apk"
[ -f "$APK" ] || { echo "APK not produced at $APK" >&2; exit 1; }

mkdir -p "$ROOT/dist"
cp -f "$APK" "$ROOT/dist/Airbomb-Al-debug.apk"
echo "==> dist/Airbomb-Al-debug.apk  ($(du -h "$ROOT/dist/Airbomb-Al-debug.apk" | cut -f1))"
