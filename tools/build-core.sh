#!/usr/bin/env bash
#
# Builds the Avaca core into shared/libs/avacacore.aar: the Xray core (core-xray/, Go package
# "yumi", inherited from Yumi) and the OpenFlux transport (third_party/OpenFlux/mobile, package
# "mobile") bound together by core/ into ONE gomobile .aar. One bind gives one Go runtime
# (libgojni.so) exporting both APIs; two separate .aars would ship two runtimes at the same path
# and the APK would not package.
#
#   tools/build-core.sh          # arm64-v8a only: every modern phone, half the wait
#   tools/build-core.sh both     # arm64-v8a + armeabi-v7a: what a release ships
#   tools/build-core.sh arm      # armeabi-v7a only
#
# Needs:
#   - Go matching core/go.mod (`go` line); GOTOOLCHAIN=local below keeps Go from swapping SDKs;
#   - gomobile and gobind at the version core/go.mod pins:
#         (cd core && go install golang.org/x/mobile/cmd/gomobile golang.org/x/mobile/cmd/gobind)
#   - the Android SDK with an NDK (ANDROID_HOME, ANDROID_NDK_HOME), and a JDK 17+;
#   - the OpenFlux sources: git submodule update --init
#
# Flags:
#   -androidapi 26     the app's minSdk
#   -checklinkname=0   github.com/wlynxg/anet reaches into net.zoneCache via //go:linkname,
#                      which Go 1.23+ rejects without it
#   -javapkg           Java prefix of the bindings: com.mydrop.vpn.xray.yumi.* (Xray) and
#                      com.mydrop.vpn.xray.mobile.* (OpenFlux), which the app imports
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${OUT:-$ROOT/shared/libs/avacacore.aar}"

case "${1:-arm64}" in
    arm64) TARGETS="android/arm64" ;;
    arm)   TARGETS="android/arm" ;;
    both)  TARGETS="android/arm64,android/arm" ;;
    *) echo "usage: $(basename "$0") [arm64|arm|both]" >&2; exit 2 ;;
esac

if [ ! -f "$ROOT/third_party/OpenFlux/go.mod" ]; then
    echo "third_party/OpenFlux is empty: run  git submodule update --init" >&2
    exit 1
fi

# Toolchain defaults for macOS with Android Studio and for Linux; override any in the environment.
if [ -z "${ANDROID_HOME:-}" ]; then
    for candidate in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
        [ -d "$candidate" ] && ANDROID_HOME="$candidate" && break
    done
fi
: "${ANDROID_HOME:?set ANDROID_HOME to the Android SDK}"
export ANDROID_HOME
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$(ls -d "$ANDROID_HOME"/ndk/* 2>/dev/null | sort -V | tail -1)}"
: "${ANDROID_NDK_HOME:?install an NDK (sdkmanager --install 'ndk;<version>') or set ANDROID_NDK_HOME}"
STUDIO_JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
if [ -z "${JAVA_HOME:-}" ] && [ -d "$STUDIO_JBR" ]; then export JAVA_HOME="$STUDIO_JBR"; fi
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
export PATH="$PATH:$(go env GOPATH)/bin"
export GOTOOLCHAIN=local

mkdir -p "$(dirname "$OUT")"
cd "$ROOT/core"
echo "==> $(go version)"
echo "==> targets: $TARGETS"
echo "==> NDK:     $ANDROID_NDK_HOME"
time gomobile bind \
    -target="$TARGETS" \
    -androidapi 26 \
    -ldflags="-checklinkname=0" \
    -javapkg=com.mydrop.vpn.xray \
    -o "$OUT" \
    github.com/GXUser7/yumi/core-xray openflux-mobile

echo "==> $OUT"
ls -lh "$OUT" | awk '{print "    " $5}'
unzip -l "$OUT" | awk '$4 ~ /\.so$/ {printf "    %-30s %6.1f MB\n", $4, $1/1048576}'
