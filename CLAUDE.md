# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Android app (Kotlin + Jetpack Compose) for viewing a [tCam](https://github.com/danjulio/tCam) thermal imaging camera over WiFi. Connects via TCP socket, decodes raw radiometric + telemetry data, applies color palettes, and displays live thermal video.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Full build (debug + release)
./gradlew build

# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device)
./gradlew connectedAndroidTest

# Run a single unit test class
./gradlew test --tests "com.das.tcamviewer2.ExampleUnitTest"
```

Install to connected device: use Android Studio's Run button or `./gradlew installDebug`.

## Architecture

### Camera Protocol

The tCam communicates over TCP port 5001. All messages are JSON framed with STX (``) prefix and ETX (``) suffix. Camera frames contain:
- `"radiometric"`: base64-encoded 16-bit little-endian pixel values, 160×120 resolution
- `"telemetry"`: base64-encoded 16-bit little-endian words at fixed offsets (A=0, B=80, C=160)
- `"metadata"`: timestamp, date, palette, etc.

mDNS service discovery uses `_tcam-socket._tcp.` (see `Constants.SERVICE_TYPE`).

### Data Flow

```
CameraService (TCP socket)
    │
    ├─ sendCmd() / pendingRequests map ──► one-shot commands (get_status, set_config, etc.)
    │
    └─ imageChannel (RxJava PublishSubject) ──► continuous frame stream
                                                     │
                                               CameraViewModel
                                                     │
                                               CameraScreen (Compose)
```

`CameraService` runs a coroutine loop reading bytes and routing parsed JSON to either a `CompletableDeferred` in `pendingRequests` (for request/response commands) or the `imageChannel` (for streaming frames).

### Global Singletons

`MainActivity.kt` declares file-level `lateinit var` globals that are accessed throughout the codebase:

```kotlin
lateinit var cameraService: CameraService
lateinit var settingsDataManager: SettingsDataManager
lateinit var cameraUtils: CameraUtils
lateinit var paletteFactory: PaletteFactory
```

These are initialized in `MainActivity.onCreate()`. Note: Hilt DI is partially wired (`CameraUtils` has `@Singleton`/`@Inject` annotations) but the app currently instantiates everything manually — there is no `@HiltAndroidApp` Application class yet.

### Key Classes

| Class | Role |
|-------|------|
| `CameraService` | Android `Service` owning the TCP socket and all I/O. Two data paths: request/response via `sendCmd()` and push stream via RxJava `imageChannel`. |
| `CameraViewModel` | ViewModel exposing `StateFlow` properties (`spotmeterTemp`, `maxTemp`, `minTemp`, `fpsCounter`) to the UI. Currently mostly empty — needs implementation. |
| `CameraUtils` | Image processing: decodes base64 radiometric/telemetry, maps pixel values through the active palette to produce an `IntArray` of ARGB pixels. |
| `ImageDto` | Data model for a single thermal frame. The `create()` factory is `suspend` because `init()` calls `CameraUtils.processImageResponse()`. |
| `PaletteFactory` | Provides 10 color palettes (Arctic, Banded, Blackhot, DoubleRainbow, Fusion, Gray, Ironblack, Isotherm, Rainbow, Sepia). Each palette is a `Array<IntArray?>` of 256 RGB triples. |
| `SettingsDataManager` | Wraps Jetpack DataStore Preferences. Exposes both `Flow<T>` properties for reactive observation and `suspend` one-shot getters. |

### UI Structure

`MainActivity` hosts a bottom navigation bar with three tabs: **Camera**, **Settings**, **Library**. Each tab renders a Compose screen:
- `CameraScreen` — live thermal view with spotmeter overlay, color bar, histogram, FPS counter
- `SettingsScreen` — camera IP, palette, temperature units, AGC, manual range, etc.
- Library tab — stub (`GenericScreen`)

### Image Processing Pipeline

In `CameraUtils.processImageResponse()`:
1. Decode base64 `radiometric` → 16-bit little-endian pixel array
2. Decode base64 `telemetry` → status bits (AGC, shutdown, emissivity, gain mode, spotmeter location)
3. If AGC: map each pixel directly through palette (index = raw pixel value)
4. If radiometric: normalize to 0–255 using min/max (or manual range from settings), then map through palette
5. Unit conversions (K×10 or K×100 → °C/°F) via `convertToRadiometric()`

### WIP / Known Incomplete Areas

- `CameraViewModel` body is empty; StateFlow state is declared but never populated
- `ImageDto.initFromFile()` is fully commented out (file-based playback not yet implemented)
- Hilt injection not complete — no `@HiltAndroidApp`, services/activities not annotated
- `viewModelScope` is set to `null` in `MainActivity` (line 52)
- Several `ImageDto` extension methods are commented out (color bar, histogram, hotspot drawing, save)
