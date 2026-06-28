# tCam Viewer 2

An Android app for viewing and managing imagery from a [tCam](https://github.com/danjulio/tCam) thermal imaging camera over Wi-Fi.

## Screenshots

| Camera (disconnected) | Camera (live) |
|---|---|
| ![Camera disconnected](screenshots/camera_disconnected.png) | ![Camera live](screenshots/camera_live.png) |

**Settings (camera connected) — top and bottom:**

| Connected — top | Connected — bottom |
|---|---|
| ![Settings connected top](screenshots/settings_connected_top.png) | ![Settings connected bottom](screenshots/settings_connected_bottom.png) |

When connected, a **Camera Settings** section appears at the top with AGC, emissivity, gain mode, and WiFi/network controls that are sent directly to the camera. The **Application Settings** section below is always visible.


| Library |
|---|
| ![Library](screenshots/library.png) |

**Camera live view** shows a thermal image of electronics using the Ironblack palette (21.6 °C – 33.4 °C range). The spotmeter temperature (23.1 °C) is overlaid at the measurement point, with a live histogram beside the color bar.

## Overview

tCam Viewer 2 connects to a tCam device over a TCP socket, decodes its raw radiometric data, applies colour palettes, and renders a live thermal video feed. Captured frames can be saved, browsed, shared, and exported directly from the device.

## Requirements

- Android 8.0 (API 26) or later
- A tCam camera on the same Wi-Fi network (or acting as its own access point)

## Features

### Camera screen
- Live 160 × 120 radiometric thermal video streamed over TCP port 5001
- Spotmeter, max, and min temperature overlays (Celsius or Fahrenheit)
- Live colour histogram and colour bar scale
- Frame-rate counter shown while streaming
- 10 colour palettes selectable from a drop-down: Arctic, Banded, Blackhot, DoubleRainbow, Fusion, Gray, Ironblack, Isotherm, Rainbow, Sepia
- Single-frame capture (Get) and continuous streaming (Stream / Stop)
- Save current frame as a `.tjsn` file (raw radiometric data + metadata) to app-private storage

### Library screen
- Browses all saved `.tjsn` (image) and `.mtjsn` (video) files grouped by date
- Thumbnail preview loaded lazily per visible row; video files show a camera badge
- Multi-select with visual highlight and checkmark badge
- Ascending / descending sort and Select All / Clear via overflow menu
- Delete selected files from disk
- Browse button opens a full-screen image viewer for selected files

### Browse / image viewer
- Full-screen thermal image with colour bar sidebar
- Max temperature (top of bar), min temperature (bottom of bar), and spotmeter temperature overlaid on the image
- Image time and filename shown in the title bar
- Previous / next navigation when multiple files are selected
- **Share** – composites the full image (scaled 4×), colour bar, and all temperature labels into a single PNG and fires the system share sheet
- **Export** – saves the same composite PNG to the device gallery via MediaStore; no storage permission required on Android 10+
- **Delete** – removes the file from disk and returns to the library
- **Play** (`.mtjsn` recordings) – opens the video player

### Video player
- Plays back `.mtjsn` recordings with accurate per-frame timing derived from metadata timestamps
- **Skip back / forward** 5 frames with fast-rewind / fast-forward buttons
- Scrub slider with frame counter
- **Fullscreen mode** – hides the title bar and system bars; tap the video or the fullscreen button to toggle; Back exits fullscreen before closing the player

### Settings screen
- Camera IP address
- Colour palette selection
- Temperature units (Celsius / Fahrenheit)
- AGC (Automatic Gain Control) toggle
- Manual temperature range (min / max)
- Shutter sound toggle
- Spotmeter enable / disable
- All settings are deferred until **Done** is pressed; **Cancel** discards changes and returns to the previous tab

## Architecture

```
CameraService (TCP socket, Android Service)
    │
    ├─ sendCmd() / pendingRequests     ──► one-shot commands (get_status, set_config, …)
    └─ imageChannel (RxJava Subject)   ──► continuous frame stream
                                               │
                                         CameraViewModel
                                         (StateFlow properties)
                                               │
                                         Compose UI screens
```

### Camera protocol

All messages are JSON framed with STX (``) prefix and ETX (``) suffix over **TCP port 5001**. Each image frame contains:

| Field | Content |
|---|---|
| `radiometric` | Base64-encoded 16-bit little-endian pixel values, 160 × 120 |
| `telemetry` | Base64-encoded 16-bit little-endian words; AGC flag, spotmeter location, temperature resolution at fixed offsets |
| `metadata` | Timestamp, date, palette name |

Camera devices are discovered via mDNS service type `_tcam-socket._tcp.`

### Image processing pipeline (`CameraUtils.processImageResponse`)

1. Decode base64 `radiometric` → raw 16-bit pixel array
2. Decode base64 `telemetry` → AGC flag, spotmeter position, tLinear enable/resolution
3. If AGC: map raw values (0–255) directly through the active palette
4. If radiometric: normalise 0–255 using min/max (or manual range from settings), then map through palette
5. Build ARGB bitmap and per-palette histogram

### File storage

Saved frames are written to `getExternalFilesDir(DIRECTORY_PICTURES)/<MM_dd_yyyy>/img_<HH_mm_ss>.tjsn` as raw JSON. No storage permission is required; files are app-private. Exported gallery images use the MediaStore API.

### Key classes

| Class | Role |
|---|---|
| `CameraService` | Android `Service` owning the TCP socket. Routes frames to either a `CompletableDeferred` (commands) or an RxJava `PublishSubject` (stream). |
| `CameraViewModel` | ViewModel consuming the frame stream; exposes `StateFlow` properties for bitmap, histogram, temperatures, FPS, and connection state. |
| `CameraUtils` | Decodes radiometric/telemetry data, maps through palettes, builds `Bitmap` and histogram. Reads display settings from `SettingsDataManager` per frame. |
| `ImageDto` | Data model for a single frame. `create(JSONObject, palette)` for live frames; `create(path, palette)` for file playback. |
| `PaletteFactory` | Provides 10 palettes, each a 256-entry RGB triple array. |
| `SettingsDataManager` | Jetpack DataStore wrapper. Exposes `Flow<T>` properties for reactive collection and `suspend` one-shot getters. |

## Building

```bash
# Debug APK
./gradlew assembleDebug

# Full build (debug + release)
./gradlew build

# Install to connected device
./gradlew installDebug

# Unit tests
./gradlew test

# Instrumented tests (requires connected device)
./gradlew connectedAndroidTest
```

## Dependencies

| Library | Purpose |
|---|---|
| Jetpack Compose + Material 3 | UI |
| Lifecycle ViewModel + Compose | Architecture |
| Jetpack DataStore Preferences | Persistent settings |
| RxJava 3 / RxAndroid | Frame stream from `CameraService` to `CameraViewModel` |
| Timber | Logging |
| Hilt | Dependency injection (partially wired; `CameraUtils` uses `@Singleton`/`@Inject`) |

## License

This project is provided as-is for personal and research use.
