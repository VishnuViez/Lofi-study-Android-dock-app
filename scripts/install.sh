#!/bin/zsh
# Builds the release APK, installs it on the connected device or running
# emulator, and launches it. Release is minified and signed with the debug key.
set -euo pipefail

ROOT=${0:A:h:h}
cd "$ROOT"
export JAVA_HOME=${JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home}
SDK=$(sed -n 's/^sdk.dir=//p' local.properties 2>/dev/null || true)
ADB=${SDK:+$SDK/platform-tools/}adb

./gradlew :app:assembleRelease --console=plain -q
"$ADB" install -r app/build/outputs/apk/release/app-release.apk
"$ADB" shell am start -n com.vishnu.lofidock/.MainActivity
# The Gradle daemon holds ~1 GB; on a Mac short of memory that becomes swap.
./gradlew --stop -q
