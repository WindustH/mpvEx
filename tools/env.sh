#!/usr/bin/env zsh
# Source this file before running Gradle to keep caches local to the project.
# Usage: source tools/env.sh && ./gradlew :app:assembleStandardRelease

export PROJECT_ROOT="${0:A:h:h}"

if [ -z "${JAVA_HOME:-}" ]; then
    JAVA_BIN="$(command -v java 2>/dev/null || true)"
    if [ -n "$JAVA_BIN" ]; then
        JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$JAVA_BIN")")")"
    fi
fi
export JAVA_HOME

if [ -d "$PROJECT_ROOT/.android-sdk/platforms" ]; then
    export ANDROID_HOME="$PROJECT_ROOT/.android-sdk"
elif [ -z "${ANDROID_HOME:-}" ]; then
    echo "error: ANDROID_HOME not set and .android-sdk/ not found" >&2
fi
export ANDROID_HOME

export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="$PROJECT_ROOT/.gradle-local"
