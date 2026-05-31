#!/usr/bin/env zsh
# CI helper - generates a minimal stub AAR containing only the Kotlin API classes.
# Used by GitHub Actions where native mpv libraries are not needed for compilation.
set -euo pipefail

PROJECT_DIR="${0:A:h:h}"
STUB_DIR="$PROJECT_DIR/build/ci-stub-aar"
LIB_DIR="$STUB_DIR/mpv-lib"
OUTPUT="$PROJECT_DIR/app/libs/mpv-android-lib.aar"

rm -rf "$STUB_DIR"
mkdir -p \
  "$LIB_DIR/src/main/kotlin" \
  "$LIB_DIR/src/main/AndroidManifest.xml"

cat >"$STUB_DIR/settings.gradle.kts" <<'EOF'
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "ci-stub"
include(":mpv-lib")
EOF

cat >"$STUB_DIR/build.gradle.kts" <<'EOF'
plugins { id("com.android.library") version "9.1.0" apply false }
EOF

cat >"$LIB_DIR/build.gradle.kts" <<'EOF'
plugins { id("com.android.library") }
android {
  namespace = "is.xyz.mpv"
  compileSdk = 36
  defaultConfig { minSdk = 21 }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}
dependencies {
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
EOF

cat >"$LIB_DIR/src/main/AndroidManifest.xml" <<'EOF'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
EOF

# Copy wrapper source
cp -R "$PROJECT_DIR/tools/mpv/wrapper-src/main/kotlin/." "$LIB_DIR/src/main/kotlin/"

# Build stub AAR
cd "$PROJECT_DIR"
./gradlew -p "$STUB_DIR" :mpv-lib:assembleRelease

mkdir -p "$(dirname "$OUTPUT")"
cp "$LIB_DIR/build/outputs/aar/mpv-lib-release.aar" "$OUTPUT"
