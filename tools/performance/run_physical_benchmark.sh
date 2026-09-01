#!/usr/bin/env bash
set -euo pipefail

# Real Physical Baseline Capture Script for an Android reference device.
# Requires a physical device with functional atrace/tracefs.

ADB="${ADB:-/Users/david/Library/Android/sdk/platform-tools/adb}"
REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUTPUT_DIR="${OUTPUT_DIR:-$REPO_DIR/docs/verification/email-open-performance}"
TARGET_SERIAL="${TARGET_SERIAL:-}"
REQUIRED_MODEL_CONTAINS="${REQUIRED_MODEL_CONTAINS:-}"
REQUIRED_DEVICE_CONTAINS="${REQUIRED_DEVICE_CONTAINS:-}"
REQUIRED_SDK="${REQUIRED_SDK:-}"
REFERENCE_LABEL="${REFERENCE_LABEL:-physicalAndroidReference}"
SCENARIO_NAME="${SCENARIO_NAME:-plainTextFirstOpen}"
SKIP_INSTALL="${SKIP_INSTALL:-false}"

LOGCAT_PID=""
MOBILE_DATA_ORIGINAL=""
MOBILE_DATA_CHANGED="false"

cleanup() {
    if [ -n "$LOGCAT_PID" ]; then
        kill -9 "$LOGCAT_PID" 2>/dev/null || true
    fi
    if [ "$MOBILE_DATA_CHANGED" = "true" ] && [ "$MOBILE_DATA_ORIGINAL" = "1" ]; then
        "$ADB" -s "$DEVICE_SERIAL" shell svc data enable >/dev/null 2>&1 || true
    fi
}
trap cleanup EXIT

echo "=== [PREFLIGHT] Checking physical device connected via ADB ==="
DEVICE_LIST=$("$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1 }')

if [ -z "$DEVICE_LIST" ]; then
    echo "ERROR: No authorized Android device detected by ADB."
    exit 1
fi

if [ -n "$TARGET_SERIAL" ]; then
    if ! echo "$DEVICE_LIST" | grep -Fxq "$TARGET_SERIAL"; then
        echo "ERROR: Requested TARGET_SERIAL '$TARGET_SERIAL' is not an authorized ADB device."
        exit 1
    fi
    DEVICE_SERIAL="$TARGET_SERIAL"
else
    DEVICE_COUNT=$(echo "$DEVICE_LIST" | wc -l | tr -d ' ')
    if [ "$DEVICE_COUNT" -ne 1 ]; then
        echo "ERROR: Multiple authorized devices detected. Set TARGET_SERIAL explicitly."
        echo "$DEVICE_LIST"
        exit 1
    fi
    DEVICE_SERIAL="$DEVICE_LIST"
fi

if [[ "$DEVICE_SERIAL" == emulator-* ]]; then
    echo "ERROR: Emulator detected. Physical baseline requires a real Android device."
    exit 2
fi

echo "Target device serial: $DEVICE_SERIAL"

MODEL=$("$ADB" -s "$DEVICE_SERIAL" shell getprop ro.product.model | tr -d '\r\n')
SDK=$("$ADB" -s "$DEVICE_SERIAL" shell getprop ro.build.version.sdk | tr -d '\r\n')
DEVICE_NAME=$("$ADB" -s "$DEVICE_SERIAL" shell getprop ro.product.device | tr -d '\r\n')
PRODUCT_NAME=$("$ADB" -s "$DEVICE_SERIAL" shell getprop ro.product.name | tr -d '\r\n')
BUILD_FINGERPRINT=$("$ADB" -s "$DEVICE_SERIAL" shell getprop ro.build.fingerprint | tr -d '\r\n')
BUILD_TYPE=$("$ADB" -s "$DEVICE_SERIAL" shell getprop ro.build.type | tr -d '\r\n')
WEBVIEW_VERSION=$("$ADB" -s "$DEVICE_SERIAL" shell dumpsys webviewupdate 2>/dev/null | awk -F': ' '/Current WebView package/ { print $2; exit }' | tr -d '\r\n' || true)

echo "Device model: $MODEL (device: $DEVICE_NAME, product: $PRODUCT_NAME, SDK: $SDK)"
echo "Build fingerprint: $BUILD_FINGERPRINT"

if [ -n "$REQUIRED_MODEL_CONTAINS" ] && [[ "$MODEL" != *"$REQUIRED_MODEL_CONTAINS"* ]]; then
    echo "ERROR: Device model '$MODEL' does not match required pattern '$REQUIRED_MODEL_CONTAINS'."
    exit 2
fi

if [ -n "$REQUIRED_DEVICE_CONTAINS" ] && [[ "$DEVICE_NAME" != *"$REQUIRED_DEVICE_CONTAINS"* ]]; then
    echo "ERROR: Device name '$DEVICE_NAME' does not match required pattern '$REQUIRED_DEVICE_CONTAINS'."
    exit 2
fi

if [ -n "$REQUIRED_SDK" ] && [ "$SDK" != "$REQUIRED_SDK" ]; then
    echo "ERROR: Device Android SDK is not $REQUIRED_SDK (detected: '$SDK')."
    exit 2
fi

echo "=== [PREFLIGHT] Validating atrace/tracefs ==="
if ! "$ADB" -s "$DEVICE_SERIAL" shell atrace --list_categories >/dev/null; then
    echo "ERROR: atrace is not functional on this device."
    exit 2
fi

if ! "$ADB" -s "$DEVICE_SERIAL" shell ls /sys/kernel/tracing/trace_marker >/dev/null 2>&1; then
    echo "ERROR: /sys/kernel/tracing/trace_marker is not available."
    exit 2
fi

if ! "$ADB" -s "$DEVICE_SERIAL" shell ls /sys/kernel/tracing/events >/dev/null 2>&1; then
    echo "ERROR: /sys/kernel/tracing/events is not available."
    exit 2
fi

BATTERY_LEVEL=$("$ADB" -s "$DEVICE_SERIAL" shell dumpsys battery | awk '/^[[:space:]]*level:[[:space:]]*/ { print $2; exit }' | tr -d '\r\n')
echo "Battery level: ${BATTERY_LEVEL}%"
if ! [[ "$BATTERY_LEVEL" =~ ^[0-9]+$ ]]; then
    echo "ERROR: Unable to parse battery level from device output ('$BATTERY_LEVEL')."
    exit 3
fi
if [ "$BATTERY_LEVEL" -lt 50 ]; then
    echo "ERROR: Battery level is below 50% ($BATTERY_LEVEL%). Connect charger and retry."
    exit 3
fi

THERMAL_STATUS=$("$ADB" -s "$DEVICE_SERIAL" shell dumpsys thermalservice 2>/dev/null | awk -F': ' 'tolower($0) ~ /thermal status:/ { print $NF; exit }' | tr -d '\r\n' || true)
THERMAL_STATUS="${THERMAL_STATUS:-Unknown}"
echo "Thermal status: $THERMAL_STATUS"
if [ "$THERMAL_STATUS" != "Unknown" ] && [ "$THERMAL_STATUS" != "0" ] && [ "$THERMAL_STATUS" != "1" ] && [ "$THERMAL_STATUS" != "None" ] && [ "$THERMAL_STATUS" != "Light" ]; then
    echo "ERROR: Device thermal status is elevated ($THERMAL_STATUS). Allow device to cool."
    exit 4
fi

WIFI_ENABLED=$("$ADB" -s "$DEVICE_SERIAL" shell settings get global wifi_on | tr -d '\r\n' || true)
MOBILE_DATA_ORIGINAL=$("$ADB" -s "$DEVICE_SERIAL" shell settings get global mobile_data | tr -d '\r\n' || true)
echo "Wi-Fi state: ${WIFI_ENABLED:-unknown} | Mobile data: ${MOBILE_DATA_ORIGINAL:-unknown}"

if [ "$WIFI_ENABLED" != "1" ]; then
    echo "ERROR: Wi-Fi is not enabled on device. Benchmark requires isolated Wi-Fi."
    exit 5
fi

if [ "$MOBILE_DATA_ORIGINAL" = "1" ]; then
    echo "WARNING: Mobile data is enabled. Disabling mobile data for isolated benchmark..."
    "$ADB" -s "$DEVICE_SERIAL" shell svc data disable || true
    MOBILE_DATA_CHANGED="true"
fi

CAPTURE_ID=$(date -u +%Y%m%dT%H%M%SZ)
TEMP_DIR="/tmp/mailapp_perf_${CAPTURE_ID}"
mkdir -p "$TEMP_DIR"
echo "Capture ID: $CAPTURE_ID"
echo "Private capture directory: $TEMP_DIR"

echo "=== [BUILD] Assembling Benchmark and Macrobenchmark APKs ==="
cd "$REPO_DIR"
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleBenchmark :macrobenchmark:assembleBenchmark

APP_APK="$REPO_DIR/app/build/outputs/apk/benchmark/app-benchmark.apk"
BENCH_APK="$REPO_DIR/macrobenchmark/build/outputs/apk/benchmark/macrobenchmark-benchmark.apk"

APP_APK_SHA=$(shasum -a 256 "$APP_APK" | awk '{print $1}')
BENCH_APK_SHA=$(shasum -a 256 "$BENCH_APK" | awk '{print $1}')

echo "Installing target APK..."
if [ "$SKIP_INSTALL" = "true" ]; then
    echo "Skipping target APK install because SKIP_INSTALL=true."
else
    "$ADB" -s "$DEVICE_SERIAL" install -r "$APP_APK"
fi
echo "Installing benchmark APK..."
if [ "$SKIP_INSTALL" = "true" ]; then
    echo "Skipping benchmark APK install because SKIP_INSTALL=true."
else
    "$ADB" -s "$DEVICE_SERIAL" install -r "$BENCH_APK"
fi

LOGCAT_RAW="$TEMP_DIR/logcat_raw.log"
"$ADB" -s "$DEVICE_SERIAL" logcat -c
"$ADB" -s "$DEVICE_SERIAL" logcat -s MailOpenTrace:D > "$LOGCAT_RAW" 2>&1 &
LOGCAT_PID=$!

echo "=== [EXECUTION] Running Macrobenchmark suite on physical reference device ==="
INSTRUMENTATION_OUTPUT="$TEMP_DIR/instrumentation_output.txt"
"$ADB" -s "$DEVICE_SERIAL" shell am instrument -w -r \
    -e class com.david.macrobenchmark.EmailOpenMacrobenchmark \
    -e androidx.benchmark.suppressErrors "LOW-BATTERY,DEBUGGABLE" \
    com.david.macrobenchmark/androidx.test.runner.AndroidJUnitRunner \
    | tee "$INSTRUMENTATION_OUTPUT"

if grep -qE 'FAILURES!!!|INSTRUMENTATION_CODE: -1|INSTRUMENTATION_STATUS_CODE: -2' "$INSTRUMENTATION_OUTPUT"; then
    echo "ERROR: Macrobenchmark instrumentation failed; refusing to analyze an incomplete capture."
    exit 6
fi

kill -9 "$LOGCAT_PID" 2>/dev/null || true
LOGCAT_PID=""

BENCHMARK_DEV_DIR="/sdcard/Android/media/com.david.mailapp/additional_test_output"
"$ADB" -s "$DEVICE_SERIAL" pull "$BENCHMARK_DEV_DIR" "$TEMP_DIR/benchmark_output" || true

echo "=== [SANITIZATION & ANALYSIS] Processing traces ==="
grep "MailOpenTrace" "$LOGCAT_RAW" > "$TEMP_DIR/mailopen_filtered.log" || true

python3 "$REPO_DIR/tools/performance/analyze_traces.py" \
    "$TEMP_DIR/mailopen_filtered.log" \
    "$OUTPUT_DIR" \
    "$CAPTURE_ID" \
    "$SCENARIO_NAME"

cp "$TEMP_DIR/mailopen_filtered.log" "$OUTPUT_DIR/sanitized-trace.log"

RAW_LOG_SHA=$(shasum -a 256 "$LOGCAT_RAW" | awk '{print $1}')
SANITIZED_LOG_SHA=$(shasum -a 256 "$OUTPUT_DIR/sanitized-trace.log" | awk '{print $1}')

cat <<EOF > "$OUTPUT_DIR/capture-manifest.json"
{
  "captureId": "$CAPTURE_ID",
  "timestampUtc": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "referenceLabel": "$REFERENCE_LABEL",
  "device": {
    "model": "$MODEL",
    "device": "$DEVICE_NAME",
    "product": "$PRODUCT_NAME",
    "serial": "$DEVICE_SERIAL",
    "sdk": $SDK,
    "buildType": "$BUILD_TYPE",
    "buildFingerprint": "$BUILD_FINGERPRINT",
    "webViewVersion": "$WEBVIEW_VERSION",
    "batteryLevelPercent": $BATTERY_LEVEL,
    "thermalStatus": "$THERMAL_STATUS"
  },
  "environment": {
    "physicalDevice": true,
    "wifiEnabled": true,
    "mobileDataDisabledForCapture": $([ "$MOBILE_DATA_ORIGINAL" = "1" ] && echo "true" || echo "false"),
    "apkInstallSkipped": $([ "$SKIP_INSTALL" = "true" ] && echo "true" || echo "false")
  },
  "build": {
    "targetApkSha256": "$APP_APK_SHA",
    "benchmarkApkSha256": "$BENCH_APK_SHA"
  },
  "inputs": {
    "rawLogSha256": "$RAW_LOG_SHA",
    "sanitizedLogSha256": "$SANITIZED_LOG_SHA"
  }
}
EOF

echo "=== [SUCCESS] Real physical baseline captured successfully ==="
echo "Manifest: $OUTPUT_DIR/capture-manifest.json"
echo "Summary: $OUTPUT_DIR/summary.json"
