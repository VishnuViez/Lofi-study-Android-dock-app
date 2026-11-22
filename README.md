# Lofi Dock for Android

The Android version of [Lofi Dock](https://github.com/VishnuViez/Lofi-Study-iOS-dock-app):
an always-on lofi desk clock for phones that turn their screen off while
charging. Native Kotlin, Jetpack Compose and coroutines; feature-for-feature
the same as the iOS app.

## What it does

- **Six faces, swipe sideways:** Lofi Room (an animated rainy-night study room
  drawn entirely in code), Big Digits, Analog, Flip, Timer and Checklist. The
  choice is remembered.
- **Screen stays on** while Lofi Dock is in front, whatever the screen timeout
  is set to; optionally only while charging.
- **Session timer** on every face, restarting whenever you leave the app
  (home, recents, power button) but not for the notification shade.
- **Timer:** hour/min/sec wheels and 5/15/25/45/60-minute presets, Cancel and
  Start/Pause/Resume, a ring that empties with the end time shown. At zero a
  soft chime repeats for up to a minute and the phone vibrates. Other faces
  show a countdown chip. If you've left the app, an exact alarm posts a
  notification instead (the first Start asks for notification permission).
- **Checklist:** add tasks at the top, tap to tick off, long-press to delete,
  Clear done. Kept between launches.
- **Rain and vinyl crackle**, synthesised live (no audio files), each with a
  switch, plus Mute all. Mixes under music from other apps.
- **Night dimming** (default 10 PM–7 AM), **burn-in protection** (the picture
  walks a few pixels each minute), **ease off when warm** (the animation frame
  rate drops as the phone heats up or in Battery Saver), **rain on the
  window** (the animation): each with its own switch.
- **Screen saver:** Lofi Dock is also an Android screen saver (Settings →
  Display → Screen saver, start while charging), so the clock can appear
  whenever the phone is charging even if the app isn't open — the Android
  counterpart of iPhone StandBy.

Tap anywhere for the control bar (face dots, mute, rain, vinyl, settings, a
bolt while charging); it hides after 5 s.

## Where coroutines do the work

- `rememberSceneTime` / `rememberWallClock` (ui/Common.kt): the animation
  clock (`withFrameNanos` + `delay`, capped at the heat-aware frame rate) and
  a clock that ticks on each whole second.
- `AmbiencePlayer`: the audio render loop is a coroutine on its own audio
  thread, blocking in `AudioTrack.write`; fades, the delayed stop and the
  repeating chime are coroutines too.
- `TimerController`: a coroutine waits until the countdown's end; its tests
  run on virtual time with `kotlinx-coroutines-test`.
- Settings and the checklist are DataStore `Flow`s exposed as `StateFlow`s;
  charging and thermal state are `callbackFlow`s over broadcasts/listeners.

## Build and install

Requires JDK 17 and the Android SDK (compileSdk 36). Versions are pinned to
AGP 8.11.1 / Kotlin 2.2.20 / Gradle 8.14.

```
scripts/install.sh          # builds release, installs on the connected device or emulator, launches
./gradlew :app:testDebugUnitTest
```

The release build is minified and signed with the debug key, for sideloading.

## Shared with iOS

The scene, audio synth, chime, session rules, layout fractions and settings
keys are ports of the iOS code. The scene's pseudo-random placement and the
audio noise generator are bit-for-bit the same (unit tests compare against
values computed by the Swift code), so both apps draw the same skyline.
`res/raw/lofi_chime.wav` is the same chime waveform, rendered once, used as the
notification sound.

Differences from iOS, by platform: Roboto stands in for SF Rounded; the
"open on charge" tip is the built-in screen saver instead of a Shortcuts
automation; wheels are a custom snapping list (Android has no wheel picker).
