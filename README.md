# mpvExtended

**Fork of [marlboro-advance/mpvEx](https://github.com/marlboro-advance/mpvEx), based on
[mpv-android](https://github.com/mpv-android/mpv-android) and the libmpv library.**

Native danmaku support is inspired by
[uosc_danmaku](https://github.com/gaowanliang/uosc_danmaku).

This fork adds:
- Native danmaku support (search, auto-match, overlay rendering)
- Self-contained build pipeline (mpv-android AAR built from source)
- Project-local Gradle and Android SDK caches
- Release builds signed with debug key

---

## Features

- Material3 Expressive Design
- Picture-in-Picture (PiP)
- Background Playback
- High-Quality Rendering
- Network Streaming
- File Management (tree and folder view)
- External Subtitle and Audio support
- SMB/FTP/WebDAV support
- Custom Playlist management
- Advanced Configuration and Scripting
- Native Danmaku (danmaku search, auto-match, overlay)

---

## Building

```bash
source tools/env.sh
./gradlew :app:assembleStandardRelease
```

### Prerequisites

- JDK 21 (`/usr/lib/jvm/java-21-openjdk`)
- Android SDK (automatically used from `.android-sdk/`)
- `gperf` (for mpv-android native build)

### APK Variants

- **universal** | **arm64-v8a** | **armeabi-v7a** | **x86** | **x86_64**

---

## Acknowledgments

- [marlboro-advance/mpvEx](https://github.com/marlboro-advance/mpvEx) — original project
- [mpv-android](https://github.com/mpv-android/mpv-android) — native library wrapper
- [mpvKt](https://github.com/abdallahmehiz/mpvKt)
- [uosc_danmaku](https://github.com/gaowanliang/uosc_danmaku) — danmaku feature inspiration
