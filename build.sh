#!/bin/sh
set -eu

usage() {
    echo "Usage: $0 {weather|reader|floating-home|settings-shortcut}" >&2
    exit 2
}

app=${1:-}
case "$app" in weather|reader|floating-home|settings-shortcut) ;; *) usage ;; esac

root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
sdk=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}
[ -n "$sdk" ] || { echo "Set ANDROID_SDK_ROOT or ANDROID_HOME" >&2; exit 2; }
tools="$sdk/build-tools/${ANDROID_BUILD_TOOLS_VERSION:-34.0.0}"
android_jar="$sdk/platforms/android-${ANDROID_PLATFORM_VERSION:-34}/android.jar"
[ -f "$android_jar" ] || { echo "Missing $android_jar" >&2; exit 2; }
[ -x "$tools/aapt" ] || { echo "Missing Android build tools in $tools" >&2; exit 2; }

project="$root/apps/$app"
build="$root/.local/build/$app"
mkdir -p "$build/classes" "$build/dex" "$build/gen" "$root/dist"

set -- package -f -m -M "$project/AndroidManifest.xml" -S "$project/res" \
    -I "$android_jar" -J "$build/gen" -F "$build/unsigned.apk"
[ ! -d "$project/assets" ] || set -- "$@" -A "$project/assets"
"$tools/aapt" "$@"

find "$project/src" "$build/gen" -name '*.java' -print0 |
    xargs -0 javac -source 7 -target 7 -classpath "$android_jar" -d "$build/classes"
find "$build/classes" -name '*.class' -print0 |
    xargs -0 "$tools/d8" --min-api 22 --lib "$android_jar" --output "$build/dex"
cp "$build/unsigned.apk" "$build/with-dex.apk"
(cd "$build/dex" && zip -q -u "$build/with-dex.apk" classes.dex)
"$tools/zipalign" -f 4 "$build/with-dex.apk" "$build/aligned.apk"

if [ -z "${APK_KEYSTORE:-}" ]; then
    APK_KEYSTORE="$root/.local/debug.keystore"
    APK_KEY_ALIAS=androiddebugkey
    APK_KEYSTORE_PASSWORD=android
    APK_KEY_PASSWORD=android
    if [ ! -f "$APK_KEYSTORE" ]; then
        keytool -genkeypair -noprompt -keystore "$APK_KEYSTORE" \
            -alias "$APK_KEY_ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
            -dname 'CN=Local Android Debug,O=Development,C=US' \
            -storepass android -keypass android >/dev/null
    fi
else
    : "${APK_KEY_ALIAS:?Set APK_KEY_ALIAS for a custom keystore}"
    : "${APK_KEYSTORE_PASSWORD:?Set APK_KEYSTORE_PASSWORD for a custom keystore}"
    APK_KEY_PASSWORD=${APK_KEY_PASSWORD:-$APK_KEYSTORE_PASSWORD}
fi
export APK_KEYSTORE_PASSWORD APK_KEY_PASSWORD
"$tools/apksigner" sign --ks "$APK_KEYSTORE" --ks-key-alias "$APK_KEY_ALIAS" \
    --ks-pass env:APK_KEYSTORE_PASSWORD --key-pass env:APK_KEY_PASSWORD \
    --min-sdk-version 22 --out "$root/dist/$app.apk" "$build/aligned.apk"
"$tools/apksigner" verify --min-sdk-version 22 "$root/dist/$app.apk"
echo "$root/dist/$app.apk"
