# mpv Android Library Build

`app/libs/mpv-android-lib.aar` is generated output and is ignored by Git.
The app still consumes that AAR, but the repository now contains the build flow
that recreates it from source.

The default build path is:

```sh
ANDROID_HOME=/path/to/android-sdk ./gradlew :app:prepareMpvAndroidLib
```

Normal app builds also depend on `:app:prepareMpvAndroidLib`. If the AAR already
exists locally, the task is skipped. If it is missing, Gradle runs:

```sh
tools/mpv/build-mpv-android-aar.sh --output app/libs/mpv-android-lib.aar
```

The script clones `mpv-android`, runs its native build scripts for Android, then
packs native libraries, `cacert.pem`, and the local wrapper sources from
`tools/mpv/wrapper-src/main/kotlin/is/xyz/mpv` into an Android Library AAR.

Useful overrides:

```sh
MPV_ANDROID_REF=master \
MPV_ANDROID_ARCHES="arm64" \
ANDROID_HOME=/path/to/android-sdk \
./gradlew :app:prepareMpvAndroidLib
```

`MPV_ANDROID_ARCHES` uses mpv-android names: `armv7l`, `arm64`, `x86`, `x86_64`.

For local iteration after native libraries have already been built:

```sh
MPV_ANDROID_SKIP_FETCH=1 \
MPV_ANDROID_RUN_DOWNLOAD=0 \
MPV_ANDROID_SKIP_NATIVE_BUILD=1 \
MPV_ANDROID_ARCHES="arm64" \
ANDROID_HOME=/path/to/android-sdk \
tools/mpv/build-mpv-android-aar.sh --output app/libs/mpv-android-lib.aar
```

The native build is intentionally not vendored in Git. It is heavy, requires
network access, and follows mpv-android's own build requirements. Linux and macOS
are the supported hosts for the upstream native build scripts.
