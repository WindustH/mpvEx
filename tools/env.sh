#!/usr/bin/env bash
# Source this file before running Gradle to keep caches local to the project.
# Usage: source tools/env.sh && ./gradlew :app:assembleStandardRelease

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOCAL_SDK="$PROJECT_ROOT/.android-sdk"

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk}"

if [ -n "$LOCAL_SDK" ] && [ -d "$LOCAL_SDK/platforms" ]; then
    export ANDROID_HOME="$LOCAL_SDK"
else
    export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
fi

export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="$PROJECT_ROOT/.gradle-local"
