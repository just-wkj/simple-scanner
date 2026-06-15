#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
PLATFORM_VERSION="${ANDROID_PLATFORM_VERSION:-android-32}"
BUILD_TOOLS_VERSION="${ANDROID_BUILD_TOOLS_VERSION:-32.0.0}"
PLATFORM="$SDK/platforms/$PLATFORM_VERSION/android.jar"
BUILD_TOOLS="$SDK/build-tools/$BUILD_TOOLS_VERSION"

ZXING_JAR="${ZXING_JAR:-$ROOT/libs/core-3.3.1.jar}"
if [ ! -f "$ZXING_JAR" ]; then
  ZXING_JAR="$HOME/.gradle/caches/modules-2/files-2.1/com.google.zxing/core/3.3.1/bd5bb06f4ae0fef8e67044131272905eb8895230/core-3.3.1.jar"
fi

JDK8="${JDK8_HOME:-$HOME/Library/Java/JavaVirtualMachines/corretto-1.8.0_452/Contents/Home}"
if [ ! -d "$JDK8" ]; then
  JDK8="/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home"
fi

if [ ! -d "$JDK8" ]; then
  echo "JDK 8 not found. Set JDK8_HOME to your JDK 8 path." >&2
  exit 1
fi
if [ ! -f "$PLATFORM" ]; then
  echo "Android platform not found: $PLATFORM" >&2
  exit 1
fi
if [ ! -x "$BUILD_TOOLS/aapt" ]; then
  echo "Android build-tools not found: $BUILD_TOOLS" >&2
  exit 1
fi
if [ ! -f "$ZXING_JAR" ]; then
  echo "ZXing jar not found. Expected: $ROOT/libs/core-3.3.1.jar" >&2
  echo "You can also set ZXING_JAR=/path/to/core-3.3.1.jar." >&2
  exit 1
fi

export JAVA_HOME="$JDK8"
export PATH="$JAVA_HOME/bin:$PATH"

KEYSTORE="$ROOT/build/simple-scanner.keystore"
UNSIGNED="$ROOT/build/SimpleScanner-unsigned.apk"
ALIGNED="$ROOT/build/SimpleScanner-aligned.apk"
SIGNED="$ROOT/build/SimpleScanner.apk"
CLASSES_JAR="$ROOT/build/classes.jar"
SOURCES_FILE="$ROOT/build/java-sources.txt"

rm -rf "$ROOT/build/gen" "$ROOT/build/classes" "$ROOT/build/dex" "$UNSIGNED" "$ALIGNED" "$SIGNED" "$CLASSES_JAR" "$SOURCES_FILE"
mkdir -p "$ROOT/build/gen" "$ROOT/build/classes" "$ROOT/build/dex"

"$BUILD_TOOLS/aapt" package \
  -f \
  -m \
  -J "$ROOT/build/gen" \
  -M "$ROOT/AndroidManifest.xml" \
  -S "$ROOT/res" \
  -I "$PLATFORM" \
  -F "$UNSIGNED"

find "$ROOT/src" "$ROOT/build/gen" -name '*.java' | sort > "$SOURCES_FILE"

"$JAVA_HOME/bin/javac" \
  -encoding UTF-8 \
  -source 1.8 \
  -target 1.8 \
  -bootclasspath "$PLATFORM" \
  -classpath "$ZXING_JAR:$ROOT/build/gen" \
  -d "$ROOT/build/classes" \
  @"$SOURCES_FILE"

(cd "$ROOT/build/classes" && jar cf "$CLASSES_JAR" .)

"$BUILD_TOOLS/d8" \
  --min-api 23 \
  --lib "$PLATFORM" \
  --classpath "$ZXING_JAR" \
  --output "$ROOT/build/dex" \
  "$CLASSES_JAR" \
  "$ZXING_JAR"

(cd "$ROOT/build/dex" && zip -q -r "$UNSIGNED" classes.dex)

"$BUILD_TOOLS/zipalign" -f 4 "$UNSIGNED" "$ALIGNED"

if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -storepass android \
    -keypass android \
    -alias simplescanner \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Simple Scanner,O=Codex,C=CN" >/dev/null
fi

"$BUILD_TOOLS/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$SIGNED" \
  "$ALIGNED"

"$BUILD_TOOLS/apksigner" verify "$SIGNED"
echo "$SIGNED"
