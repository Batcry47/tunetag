# TuneTag 0.4

Fast, lean local music player for Android (Kotlin + Jetpack Compose + Media3) with built-in auto-tagging.
No streaming, no video, no lyrics, no equalizer. Targets Android 10+ (EMUI, no Google services needed).

## Build
Upload this folder to GitHub (keep `.github/workflows/build.yml`); the Actions tab builds `app-release.apk`.
If a build fails at `minifyReleaseWithR8`, set `isMinifyEnabled` and `isShrinkResources` to `false` in `app/build.gradle.kts`.

## Upgrading
Delete the whole `app` folder in your repo, then upload the new `app` folder (old files that no longer exist would break the build).

## Layout
Top bar: icon-only tabs (Albums, Recently Added, Songs, Artists, Genres, Folders, Playlists) - swipe or tap.
Search and Settings are top-right. The mini player is a square bar docked at the bottom with a progress ring round play.
The mini player grows into the Now Playing page (and back) as one continuous animation.

## Gestures
* Mini player: tap or swipe up = open player, swipe right-to-left = next song, left-to-right = previous song, swipe down = stop.
  (Swiping does nothing when only one song is queued.)
* Now Playing: swipe down = collapse, swipe up = queue, swipe right-to-left = next, left-to-right = previous,
  double-tap left/right of the artwork = seek (5/10/15/20s in Settings, default 5s).
* Queue: press and hold any song, then drag it anywhere to reorder; tap to jump, X to remove; swipe down or tap the arrow to close.
* Lists: long-press to select several songs.

## Listening history
The playback service records how long you really listen to each song (private, on-device SQLite).
Settings > Open listening stats shows a Wrapped-style summary (month / year / all time).

## iPod Classic
Settings > Player style > iPod Classic. Rotate the wheel to scroll (tick sound + haptic), centre = select,
MENU = back, right/left = next/previous (hold to seek), bottom = play/pause. On Now Playing the wheel is volume;
press the centre to make it scrub.
