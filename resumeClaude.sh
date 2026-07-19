# Session e06d1a25 covered:
# - Changed the hotspot/spotmeter square marker from filled to hollow
#   (stroked black outline + white outline), on-screen and in exports
#   (SpotmeterOverlay.kt, HotspotMarker.kt)
# - Added a Yes/No "Save recording?"/"Save time lapse?" confirmation
#   dialog on Stop, with discard actually deleting the file
#   (CameraViewModel stopRecording/stopTimeLapse(save), RecordingHandle
#   in CameraUtils)
# - Added a "Go to frame" numeric jump dialog in the video/time-lapse
#   player, next to the existing scrub slider (LibraryScreen.kt)
# - Added a live arrow marker beside the color bar showing where the
#   current spotmeter reading falls between max/min temp, gated by the
#   Settings "Spotmeter" toggle (which was previously completely
#   unwired — settingsDataManager.spotmeterFlow had no readers)
# - Fixed a bug where Share/Export to gallery (still images and video)
#   baked in the hotspot marker regardless of the Spotmeter setting;
#   both paths now check settingsDataManager.getSpotmeter() first
# - Known remaining gap (flagged, not fixed): the on-screen square
#   marker in CameraScreen's live view and LibraryScreen's BrowseWindow
#   is still not gated by the Spotmeter setting — only the color-bar
#   arrow and the export/share paths are
claude --resume e06d1a25-ab9a-4b77-9677-0b1313122bea
