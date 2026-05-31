#!/usr/bin/env zsh
set -euo pipefail

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

usage() {
  cat <<'USAGE'
Usage: build-mpv-android-aar.sh [options]

Builds app/libs/mpv-android-lib.aar from mpv-android sources.

Options:
  --output PATH       Destination AAR path.
  --work-dir PATH     Working directory for cloned and generated files.
  --repo URL          mpv-android repository URL.
  --ref REF           mpv-android git ref to build.
  --arches LIST       Space-separated mpv-android arches.
  --skip-fetch        Skip fetching mpv-android updates for offline rebuilds.
  --skip-download     Skip mpv-android buildscripts/download.sh.
  --skip-native-build Skip mpv-android native build and reuse existing outputs.
  -h, --help          Show this help.

Environment:
  ANDROID_HOME or ANDROID_SDK_ROOT must point to an Android SDK.
  MPV_ANDROID_REPO defaults to https://github.com/mpv-android/mpv-android.git
  MPV_ANDROID_REF defaults to master
  MPV_ANDROID_ARCHES defaults to "armv7l arm64 x86 x86_64"
  MPV_ANDROID_BUILD_DIR defaults to build/mpv-android-aar
  MPV_ANDROID_SKIP_FETCH defaults to 0
  MPV_ANDROID_RUN_DOWNLOAD defaults to 1
  MPV_ANDROID_SKIP_NATIVE_BUILD defaults to 0
USAGE
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "missing required command: $1"
}

ROOT_DIR="${0:A:h:h:h}"
OUTPUT="${MPV_ANDROID_AAR_OUTPUT:-$ROOT_DIR/app/libs/mpv-android-lib.aar}"
WORK_DIR="${MPV_ANDROID_BUILD_DIR:-$ROOT_DIR/build/mpv-android-aar}"
MPV_ANDROID_REPO="${MPV_ANDROID_REPO:-https://github.com/mpv-android/mpv-android.git}"
MPV_ANDROID_REF="${MPV_ANDROID_REF:-master}"
MPV_ANDROID_ARCHES="${MPV_ANDROID_ARCHES:-armv7l arm64 x86 x86_64}"
MPV_ANDROID_SKIP_FETCH="${MPV_ANDROID_SKIP_FETCH:-0}"
MPV_ANDROID_RUN_DOWNLOAD="${MPV_ANDROID_RUN_DOWNLOAD:-1}"
MPV_ANDROID_SKIP_NATIVE_BUILD="${MPV_ANDROID_SKIP_NATIVE_BUILD:-0}"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --output)
      shift
      OUTPUT="${1:-}"
      ;;
    --work-dir)
      shift
      WORK_DIR="${1:-}"
      ;;
    --repo)
      shift
      MPV_ANDROID_REPO="${1:-}"
      ;;
    --ref)
      shift
      MPV_ANDROID_REF="${1:-}"
      ;;
    --arches)
      shift
      MPV_ANDROID_ARCHES="${1:-}"
      ;;
    --skip-fetch)
      MPV_ANDROID_SKIP_FETCH=1
      ;;
    --skip-download)
      MPV_ANDROID_RUN_DOWNLOAD=0
      ;;
    --skip-native-build)
      MPV_ANDROID_SKIP_NATIVE_BUILD=1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die "unknown argument: $1"
      ;;
  esac
  shift
done

[ -n "$OUTPUT" ] || die "--output cannot be empty"
[ -n "$WORK_DIR" ] || die "--work-dir cannot be empty"
[ -n "$MPV_ANDROID_REPO" ] || die "--repo cannot be empty"
[ -n "$MPV_ANDROID_REF" ] || die "--ref cannot be empty"
[ -n "$MPV_ANDROID_ARCHES" ] || die "--arches cannot be empty"

require_cmd git
require_cmd cp
require_cmd find
require_cmd mkdir
require_cmd rm

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [ -z "$ANDROID_SDK_ROOT" ] && [ -d "$ROOT_DIR/.android-sdk" ]; then
  ANDROID_SDK_ROOT="$(cd "$ROOT_DIR/.android-sdk" && pwd)"
fi
[ -n "$ANDROID_SDK_ROOT" ] || die "set ANDROID_HOME or ANDROID_SDK_ROOT to your Android SDK"
[ -d "$ANDROID_SDK_ROOT" ] || die "Android SDK not found: $ANDROID_SDK_ROOT"

SRC_DIR="$WORK_DIR/mpv-android"
AAR_PROJECT_DIR="$WORK_DIR/aar-project"
LIB_DIR="$AAR_PROJECT_DIR/mpv-android-lib"

mkdir -p "$WORK_DIR"

if [ ! -d "$SRC_DIR/.git" ]; then
  git clone "$MPV_ANDROID_REPO" "$SRC_DIR"
fi

if [ "$MPV_ANDROID_SKIP_FETCH" != "1" ]; then
  git -C "$SRC_DIR" fetch --tags origin
fi
git -C "$SRC_DIR" checkout "$MPV_ANDROID_REF"

SDK_LINK="$SRC_DIR/buildscripts/sdk/android-sdk-linux"
mkdir -p "$(dirname "$SDK_LINK")"
if [ -e "$SDK_LINK" ] && [ ! -L "$SDK_LINK" ]; then
  die "$SDK_LINK exists and is not a symlink"
fi
ln -sfn "$ANDROID_SDK_ROOT" "$SDK_LINK"

if [ "$MPV_ANDROID_RUN_DOWNLOAD" != "0" ]; then
  (
    cd "$SRC_DIR/buildscripts"
    ./download.sh
  )
fi

if [ "$MPV_ANDROID_SKIP_NATIVE_BUILD" != "1" ]; then
  for arch in $MPV_ANDROID_ARCHES; do
    (
      cd "$SRC_DIR/buildscripts"
      ./buildall.sh --arch "$arch" mpv-android
    )
  done
fi

rm -rf "$AAR_PROJECT_DIR"
mkdir -p \
  "$LIB_DIR/src/main/kotlin" \
  "$LIB_DIR/src/main/jniLibs" \
  "$LIB_DIR/src/main/assets"

cat >"$AAR_PROJECT_DIR/settings.gradle.kts" <<'EOF'
pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}
rootProject.name = "mpv-android-lib-build"
include(":mpv-android-lib")
EOF

cat >"$AAR_PROJECT_DIR/build.gradle.kts" <<'EOF'
plugins {
  id("com.android.library") version "9.1.0" apply false
}
EOF

cat >"$LIB_DIR/build.gradle.kts" <<'EOF'
plugins {
  id("com.android.library")
}

android {
  namespace = "is.xyz.mpv"
  compileSdk = 36

  defaultConfig {
    minSdk = 21
    consumerProguardFiles("consumer-rules.pro")
  }

  buildFeatures {
    buildConfig = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
  }
}

dependencies {
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
EOF

cat >"$LIB_DIR/consumer-rules.pro" <<'EOF'
-keep,allowoptimization class is.xyz.mpv.** { public protected *; }
EOF

cat >"$LIB_DIR/src/main/AndroidManifest.xml" <<'EOF'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
EOF

cp -R "$ROOT_DIR/tools/mpv/wrapper-src/main/kotlin/." "$LIB_DIR/src/main/kotlin/"

NATIVE_LIBS_DIR=""
if [ -d "$SRC_DIR/app/src/main/jniLibs" ]; then
  NATIVE_LIBS_DIR="$SRC_DIR/app/src/main/jniLibs"
elif [ -d "$SRC_DIR/app/src/main/libs" ]; then
  NATIVE_LIBS_DIR="$SRC_DIR/app/src/main/libs"
fi
[ -n "$NATIVE_LIBS_DIR" ] || die "mpv-android did not produce native libraries"
cp -R "$NATIVE_LIBS_DIR/." "$LIB_DIR/src/main/jniLibs/"

if [ -d "$SRC_DIR/app/src/main/assets" ]; then
  cp -R "$SRC_DIR/app/src/main/assets/." "$LIB_DIR/src/main/assets/"
fi

(
  cd "$ROOT_DIR"
  sh ./gradlew -p "$AAR_PROJECT_DIR" :mpv-android-lib:assembleRelease
)

BUILT_AAR="$LIB_DIR/build/outputs/aar/mpv-android-lib-release.aar"
[ -f "$BUILT_AAR" ] || die "AAR build did not produce $BUILT_AAR"

mkdir -p "$(dirname "$OUTPUT")"
cp "$BUILT_AAR" "$OUTPUT.tmp"
mv "$OUTPUT.tmp" "$OUTPUT"
printf 'Built %s\n' "$OUTPUT"
