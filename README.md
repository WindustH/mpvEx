# mpvDanmuku

<p align="center"><img src="mpvDanmuku.svg" width="128" alt="mpvDanmuku icon" /></p>

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

## Project Architecture

```
mpvDanmuku/
├── .android-sdk/            # Android SDK (gitignored, see Building)
├── .gradle-local/           # Gradle caches (gitignored, via tools/env.sh)
├── app/
│   ├── build.gradle.kts     # App module config
│   ├── src/main/java/app/windusth/mpvdanmuku/
│   │   ├── di/              # Koin dependency injection modules
│   │   ├── database/        # Room DB (entities, DAOs, converters)
│   │   ├── domain/          # Business logic (anime4k, browser, thumbnail)
│   │   ├── preferences/     # Preference models & store abstractions
│   │   ├── presentation/    # Shared Compose components (PlayerSheet, SliderItem)
│   │   ├── repository/      # External data sources
│   │   │   ├── danmaku/     #   Dandanplay API + danmaku auto-match
│   │   │   └── wyzie/       #   Wyzie subtitle search & download
│   │   ├── ui/
│   │   │   ├── player/      #   Player screen (Activity, ViewModel, controls)
│   │   │   │   └── controls/
│   │   │   │       ├── components/
│   │   │   │       │   ├── panels/    # Settings panels (video, subtitle, audio)
│   │   │   │       │   └── sheets/    # Bottom sheets (danmaku, tracks, chapters)
│   │   │   │       ├── PlayerControls.kt       # Root control layout + danmaku overlay
│   │   │   │       ├── PlayerControlsShared.kt # Button factory (switches, toggle, search)
│   │   │   │       ├── PlayerSheets.kt         # Sheet routing (Danmaku, Subtitles, More)
│   │   │   │       └── GestureHandler.kt       # Touch gesture handling
│   │   │   ├── browser/      #   File browser & folder list
│   │   │   ├── preferences/  #   Settings screens
│   │   │   └── theme/        #   Material3 theme & colors
│   │   └── utils/            # General utilities (media, storage, update)
├── tools/
│   ├── env.sh               # Sets ANDROID_HOME, JAVA_HOME, GRADLE_USER_HOME
│   ├── ci-stub-aar.sh        # Creates minimal AAR for CI compilation
│   └── mpv/
│       ├── build-mpv-android-aar.sh  # Builds AAR from mpv-android sources
│       └── wrapper-src/              # Kotlin wrappers (MPVLib, FastThumbnails, ...)
├── docs/
│   └── mpv-android-lib.md    # AAR build documentation
```

### Key Patterns

- **MVVM**: `PlayerActivity` hosts `PlayerViewModel`; UI state flows drive Compose.
- **Koin DI**: Modules in `di/` register singletons (repositories, managers, preferences).
- **Room**: `MpvDanmukuDatabase` with DAOs for playback state, folders, recently played.
- **Repository**: `DandanplayDanmakuRepository` talks to danmaku API; `WyzieSearchRepository` handles subtitle sources.
- **Compose Overlay**: `DanmakuOverlay` runs its own frame-rate loop independent of mpv's progress polling for smooth scrolling.
- **Preference Store**: Abstracted `Primitive` classes (in `preferences/preference/`) wrap SharedPreferences with Flow-based observation.

### Danmaku Feature

| Component | File | Role |
|-----------|------|------|
| API client | `repository/danmaku/DandanplayDanmakuRepository.kt` | Search, match, fetch comments |
| UI state | `ui/player/DanmakuUiState.kt` | Immutable state for the danmaku sheet |
| Search sheet | `controls/components/sheets/DanmakuSheet.kt` | Manual search → select anime → load episode |
| Overlay | `controls/components/DanmakuOverlay.kt` | Scroll/fixed danmaku rendering layer |
| Toggle button | `controls/PlayerControlsShared.kt` | Toggle danmaku on/off (auto-match on first toggle) |
| Search button | `controls/PlayerControlsShared.kt` | Open the danmaku search sheet |
| Preferences | `preferences/DanmakuPreferences.kt` | Font size, opacity, speed, frame rate, area |
| Settings UI | `ui/preferences/DanmakuPreferencesScreen.kt` | Sliders and switches for danmaku settings |
| ViewModel | `ui/player/PlayerViewModel.kt` | Orchestrates auto-match, load, toggle, clear |

---

## Building

```bash
source tools/env.sh
./gradlew :app:assembleStandardRelease
```

### Prerequisites

- JDK 17 (`/usr/lib/jvm/java-17-openjdk`)
- Android SDK (automatically used from `.android-sdk/`)
- `gperf` (for mpv-android native build)

### APK Variants

- **arm64-v8a**

---

## Acknowledgments

- [marlboro-advance/mpvEx](https://github.com/marlboro-advance/mpvEx) — original project
- [mpv-android](https://github.com/mpv-android/mpv-android) — native library wrapper
- [mpvKt](https://github.com/abdallahmehiz/mpvKt)
- [uosc_danmaku](https://github.com/gaowanliang/uosc_danmaku) — danmaku feature inspiration
