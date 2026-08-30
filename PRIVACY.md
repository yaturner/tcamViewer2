# Privacy Policy

**Last updated: August 30, 2026**

tCam Viewer ("the app") is a viewer for [tCam](https://github.com/danjulio/tCam) thermal imaging
cameras. This page explains what data the app accesses and what, if anything, leaves your device.

## Summary

tCam Viewer talks directly to your camera over your local WiFi network. It does not have an
account system, does not show ads, and does not run analytics on how you use it. The only data
that can leave your device is an automated crash report, and only in the version distributed
outside F-Droid — see below.

## Data the app accesses

### Camera connection (local network only)
The app connects to a tCam camera over TCP on your local WiFi network to stream thermal video,
telemetry, and camera settings. This traffic stays on your local network between your device and
the camera; tCam Viewer does not relay it anywhere else.

### Photos, recordings, and charts you save
When you save a thermal image, video, time lapse, or temperature chart, it's written to storage
on your device. These files are never uploaded automatically. If you use Android's share sheet to
send one somewhere, that transfer is between you and whatever app or service you chose — the same
as sharing any other photo from your phone.

### Location permission
The app requests `ACCESS_FINE_LOCATION`. This is **not used to determine or record your
location**. Android requires this permission before any app can read WiFi network names (SSIDs)
from scan results, and tCam Viewer uses it solely to let you pick your camera's WiFi network when
configuring it. No location data is stored, transmitted, or associated with you.

### Crash reports (GitHub/Prebuilt release build only)
The build distributed via GitHub Releases includes automated crash reporting through
[GlitchTip](https://glitchtip.com/) (a self-hosted, Sentry-compatible error tracking service). If
the app crashes or hits an unexpected error, a report is sent containing:
- The stack trace of the error
- App version and build type
- Device model and Android version

These reports do not include your camera's IP address, saved images, telemetry, or anything else
you've captured. They exist to help fix bugs.

**The F-Droid build of tCam Viewer has no crash reporting or network telemetry of any kind** — it
contains no code path that can contact GlitchTip or any other external service beyond the camera
itself.

## What the app does not do

- No account, sign-in, or user profile
- No advertising or ad SDKs
- No usage analytics
- No selling or sharing of data with third parties for marketing purposes

## Data retention and deletion

Crash reports are retained only as long as needed to diagnose and fix issues. Because they aren't
linked to any account or identifier, there's no per-user record to look up or delete on request.
Files you save locally are under your control — delete them from within the app's Library/Charts
screens or from your device's file storage at any time.

## Children's privacy

tCam Viewer is a technical tool for viewing thermal camera output and is not directed at children.
It does not knowingly collect data from children.

## Changes to this policy

If what the app collects changes, this page will be updated and the "Last updated" date above will
reflect it.

## Contact

Questions about this policy or the app's data handling can be sent to
[privacy@darcangel.com](mailto:privacy@darcangel.com), or raised by opening an issue at
<https://github.com/yaturner/tcamViewer2/issues>.
