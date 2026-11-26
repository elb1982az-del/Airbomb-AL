#!/usr/bin/env bash
# Build a signed, installable release APK: dist/Airbomb-Al.apk
#
# Signing key: android/airbomb-release.jks + android/keystore.properties.
# They are generated on first run and MUST be backed up — losing them means you
# cannot ship updates to the same package. Both are gitignored.
#
#   ./scripts/build_release.sh
#   ./scripts/build_release.sh --offline
#   AIRBOMB_STOREPASS=... ./scripts/build_release.sh   # choose your own password
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/android"

GRADLE_ARGS=(:app:assembleRelease --no-daemon --console=plain)
for arg in "$@"; do
    case "$arg" in
        --offline) GRADLE_ARGS+=(--offline) ;;
        *) echo "unknown option: $arg" >&2; exit 2 ;;
    esac
done

if [ -z "${JAVA_HOME:-}" ]; then
    for candidate in /usr/lib/jvm/java-21-openjdk-* /usr/lib/jvm/java-17-openjdk-*; do
        [ -d "$candidate" ] && JAVA_HOME="$candidate" && break
    done
fi
[ -n "${JAVA_HOME:-}" ] && export JAVA_HOME

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

# ---------------------------------------------------------------- signing key
KEYSTORE="$ROOT/android/airbomb-release.jks"
PROPS="$ROOT/android/keystore.properties"

if [ ! -f "$KEYSTORE" ] || [ ! -f "$PROPS" ]; then
    command -v keytool >/dev/null 2>&1 || {
        echo "keytool not found — install a JDK to generate a signing key" >&2
        exit 1
    }
    PASS="${AIRBOMB_STOREPASS:-$(head -c 64 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24)}"
    echo "==> generating release keystore (android/airbomb-release.jks)"
    keytool -genkeypair -v \
        -keystore "$KEYSTORE" -alias airbomb \
        -keyalg RSA -keysize 4096 -validity 10000 \
        -storepass "$PASS" -keypass "$PASS" \
        -dname "CN=Airbomb-Al, OU=Security Research, O=Airbomb-Al, L=Local, ST=Local, C=US" \
        >/dev/null 2>&1
    cat > "$PROPS" <<EOF
storeFile=airbomb-release.jks
storePassword=$PASS
keyAlias=airbomb
keyPassword=$PASS
EOF
    chmod 600 "$PROPS" "$KEYSTORE"
    echo "==> backup android/airbomb-release.jks and android/keystore.properties now"
fi

AAPT2="$ANDROID_HOME/build-tools/34.0.0/aapt2"
[ -x "$AAPT2" ] && GRADLE_ARGS+=("-Pandroid.aapt2FromMavenOverride=$AAPT2")

echo "==> gradle ${GRADLE_ARGS[*]}"
"$GRADLE" "${GRADLE_ARGS[@]}"

APK="app/build/outputs/apk/release/app-release.apk"
[ -f "$APK" ] || { echo "release APK not produced at $APK" >&2; exit 1; }

mkdir -p "$ROOT/dist"
cp -f "$APK" "$ROOT/dist/Airbomb-Al.apk"

APKSIGNER="$ANDROID_HOME/build-tools/34.0.0/apksigner"
if [ -x "$APKSIGNER" ]; then
    "$APKSIGNER" verify --print-certs "$ROOT/dist/Airbomb-Al.apk" | head -4 || true
fi

echo
echo "==> dist/Airbomb-Al.apk  ($(du -h "$ROOT/dist/Airbomb-Al.apk" | cut -f1))"
sha256sum "$ROOT/dist/Airbomb-Al.apk"
echo "==> upload this file to a GitHub Release"
