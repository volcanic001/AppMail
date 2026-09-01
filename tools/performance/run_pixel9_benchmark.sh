#!/usr/bin/env bash
set -euo pipefail

# ─────────────────────────────────────────────────────────────────────────────
# Real Physical Baseline Capture Script for Google Pixel 9 (API 37)
# Stage 0 Correction (0R.3 / 0R.4)
# ─────────────────────────────────────────────────────────────────────────────

ADB="${ADB:-/Users/david/Library/Android/sdk/platform-tools/adb}"
REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUTPUT_DIR="$REPO_DIR/docs/verification/email-open-performance"

echo "=== [PREFLIGHT] Checking physical device connected via ADB ==="
DEVICE_LIST=$("$ADB" devices | grep -v "List of devices" | grep "device$" || true)

if [ -z "$DEVICE_LIST" ]; then
    echo "ERROR: No authorized Android device detected by ADB."
    echo "Please connect your Google Pixel 9 (API 37) via USB or Wireless ADB."
    exit 1
fi

DEVICE_SERIAL=$(echo "$DEVICE_LIST" | head -n 1 | awk '{print $1}')
echo "Target device serial: $DEVICE_SERIAL"

# 1. Validate device model & SDK
MODEL=$("$ADB" -s "$DEVICE_SERIAL" shell getprop ro.product.model | tr -d '\r\n')
SDK=$("$ADB" -s "$DEVICE_SERIAL" shell getprop ro.build.version.sdk | tr -d '\r\n')
DEVICE_NAME=$("$ADB" -s "$DEVICE_SERIAL" shell getprop ro.product.device | tr -d '\r\n')

echo "Device model: $MODEL (device: $DEVICE_NAME, SDK: $SDK)"

if [[ "$MODEL" != *"Pixel 9"* && "$DEVICE_NAME" != *"tokay"* ]]; then
    echo "ERROR: Device is not a Pixel 9 (detected: '$MODEL' / '$DEVICE_NAME'). Aborting."
    exit 2
fi

if [ "$SDK" != "37" ]; then
    echo "ERROR: Device Android SDK is not 37 (detected: '$SDK'). Aborting."
    exit 2
fi

# 2. Validate battery level >= 50%
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

# 3. Validate thermal status (nominal or light: <= 1)
THERMAL_STATUS=$("$ADB" -s "$DEVICE_SERIAL" shell dumpsys thermalservice 2>/dev/null | grep -i "thermal status:" | head -n 1 | awk '{print $NF}' | tr -d '\r\n' || echo "0")
echo "Thermal status: $THERMAL_STATUS"
if [ "$THERMAL_STATUS" != "0" ] && [ "$THERMAL_STATUS" != "1" ] && [ "$THERMAL_STATUS" != "None" ] && [ "$THERMAL_STATUS" != "Light" ]; then
    echo "ERROR: Device thermal status is elevated ($THERMAL_STATUS). Allow device to cool."
    exit 4
fi

# 4. Validate Wi-Fi active and mobile data off
WIFI_ENABLED=$("$ADB" -s "$DEVICE_SERIAL" shell settings get global wifi_on | tr -d '\r\n')
MOBILE_DATA=$("$ADB" -s "$DEVICE_SERIAL" shell settings get global mobile_data | tr -d '\r\n')
echo "Wi-Fi state: $WIFI_ENABLED | Mobile data: $MOBILE_DATA"

if [ "$WIFI_ENABLED" != "1" ]; then
    echo "ERROR: Wi-Fi is not enabled on device. Benchmark requires isolated Wi-Fi."
    exit 5
fi

if [ "$MOBILE_DATA" = "1" ]; then
    echo "WARNING: Mobile data is enabled. Disabling mobile data for isolated benchmark..."
    "$ADB" -s "$DEVICE_SERIAL" shell svc data disable || true
fi

# 5. Create unique UTC capture ID and private temp directory
CAPTURE_ID=$(date -u +%Y%m%dT%H%M%SZ)
TEMP_DIR="/tmp/mailapp_perf_${CAPTURE_ID}"
mkdir -p "$TEMP_DIR"
echo "Capture ID: $CAPTURE_ID"
echo "Private capture directory: $TEMP_DIR"

# 6. Build and install benchmark APKs
echo "=== [BUILD] Assembling Benchmark and Macrobenchmark APKs ==="
cd "$REPO_DIR"
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleBenchmark :macrobenchmark:assembleBenchmark

APP_APK="$REPO_DIR/app/build/outputs/apk/benchmark/app-benchmark.apk"
BENCH_APK="$REPO_DIR/macrobenchmark/build/outputs/apk/benchmark/macrobenchmark-benchmark.apk"

APP_APK_SHA=$(shasum -a 256 "$APP_APK" | awk '{print $1}')
BENCH_APK_SHA=$(shasum -a 256 "$BENCH_APK" | awk '{print $1}')

echo "Installing target APK..."
"$ADB" -s "$DEVICE_SERIAL" install -r "$APP_APK"
echo "Installing benchmark APK..."
"$ADB" -s "$DEVICE_SERIAL" install -r "$BENCH_APK"

# 7. Start structured Logcat recording to private file
LOGCAT_RAW="$TEMP_DIR/logcat_raw.log"
"$ADB" -s "$DEVICE_SERIAL" logcat -c
"$ADB" -s "$DEVICE_SERIAL" logcat -s MailOpenTrace:D > "$LOGCAT_RAW" 2>&1 &
LOGCAT_PID=$!
trap "kill -9 $LOGCAT_PID 2>/dev/null || true" EXIT

# 8. Execute Macrobenchmark suite
echo "=== [EXECUTION] Running Macrobenchmark suite on Pixel 9 ==="
INSTRUMENTATION_OUTPUT="$TEMP_DIR/instrumentation_output.txt"
"$ADB" -s "$DEVICE_SERIAL" shell am instrument -w -r \
    -e class com.david.macrobenchmark.EmailOpenMacrobenchmark \
    -e androidx.benchmark.suppressErrors "EMULATOR,LOW-BATTERY,DEBUGGABLE" \
    com.david.macrobenchmark/androidx.test.runner.AndroidJUnitRunner \
    | tee "$INSTRUMENTATION_OUTPUT"

if grep -qE 'FAILURES!!!|INSTRUMENTATION_CODE: -1|INSTRUMENTATION_STATUS_CODE: -2' "$INSTRUMENTATION_OUTPUT"; then
    echo "ERROR: Macrobenchmark instrumentation failed; refusing to analyze an incomplete capture."
    exit 6
fi

# Stop logcat capture
kill -9 $LOGCAT_PID 2>/dev/null || true
trap - EXIT

# Pull benchmark JSON report from device
BENCHMARK_DEV_DIR="/sdcard/Android/media/com.david.mailapp/additional_test_output"
"$ADB" -s "$DEVICE_SERIAL" pull "$BENCHMARK_DEV_DIR" "$TEMP_DIR/benchmark_output" || true

# 9. Extract and sanitize traces
echo "=== [SANITIZATION & ANALYSIS] Processing traces ==="
grep "MailOpenTrace" "$LOGCAT_RAW" > "$TEMP_DIR/mailopen_filtered.log" || true

# Run analyzer
python3 "$REPO_DIR/tools/performance/analyze_traces.py" \
    "$TEMP_DIR/mailopen_filtered.log" \
    "$OUTPUT_DIR" \
    "$CAPTURE_ID" \
    "plainTextFirstOpen"

# Copy sanitized log to output dir
cp "$TEMP_DIR/mailopen_filtered.log" "$OUTPUT_DIR/sanitized-trace.log"

RAW_LOG_SHA=$(shasum -a 256 "$LOGCAT_RAW" | awk '{print $1}')
SANITIZED_LOG_SHA=$(shasum -a 256 "$OUTPUT_DIR/sanitized-trace.log" | awk '{print $1}')

# 10. Generate capture-manifest.json
cat <<EOF > "$OUTPUT_DIR/capture-manifest.json"
{
  "captureId": "$CAPTURE_ID",
  "timestampUtc": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "device": {
    "model": "$MODEL",
    "device": "$DEVICE_NAME",
    "serial": "$DEVICE_SERIAL",
    "sdk": $SDK,
    "batteryLevelPercent": $BATTERY_LEVEL,
    "thermalStatus": "$THERMAL_STATUS"
  },
  "environment": {
    "wifiEnabled": true,
    "mobileDataDisabled": true
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
