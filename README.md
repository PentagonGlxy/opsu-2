# [opsu!](https://itdelatrisu.github.io/opsu/)
**opsu!** is an unofficial open-source client for the rhythm game
[osu!](https://osu.ppy.sh/), written in Java using
[Slick2D](http://slick.ninjacave.com/) and  [LWJGL](http://lwjgl.org/)
(wrappers around OpenGL and OpenAL).

opsu! runs on Windows, OS X, and Linux.

This is a fork of [opsu!](https://github.com/itdelatrisu/opsu).

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/fluddokt.opsu.android/)
     
F-Droid inclusion from [UnderSampled](https://github.com/UnderSampled/opsu)

## Building
Requirements:

- JDK 17
- Android SDK Platform 36
- Android SDK Build Tools 35.0.0

Initialize the pinned source dependencies:

`git submodule update --init --recursive`

Then build with the standard Android Studio Gradle tasks:

`./gradlew :android:assembleDebug`

Output will be in `android/build/outputs/apk/debug/`.

Every push to `master` or an `agent/*` branch also builds a downloadable debug
APK through GitHub Actions.

The build automatically applies the versioned patches in `patches/core/` to
the pinned upstream core.  This lets the Android fork maintain fixes without
silently changing or depending on write access to the upstream submodule.

## Android revival changes

- Precise monotonic gameplay clock, synchronized to the decoded audio sample
  position without frame-to-frame time reversals.
- Correct floating-point audio latency compensation.
- Neutral `0ms` universal offset for new installs; existing saved offsets are
  preserved.
- Thread-safe audio clock state between the decoder and render threads.
- Unbuffered touch dispatch on supported Android versions.
- Default-on **Battery saver** display profile: menus use the closest compatible
  60Hz mode while gameplay retains the display's fastest compatible refresh rate.
- Screen-awake is enabled only during active gameplay instead of using a permanent
  wakelock; unused motion sensors are disabled.
- Android-compatible OpenGL boolean state queries, preventing slider-render crashes.
- Persistent **Beatmap sounds** toggle for enabling or muting hit-object and slider sounds.
- Separate **Custom beatmap sounds** toggle for indexed WAV, MP3, and OGG
  samples in the current beatmap folder, with skin/default fallback.
- Default-on **Smooth slider animations** with sub-millisecond slider-ball
  movement, denser curve geometry, animated follow circles, eased snaking,
  direction-correct ball rotation, and BPM-pulsing reverse arrows.
- Default-on **Stable hitobject animations** with eased circle entrances,
  animated slider caps, expanding hit bursts, graceful miss fades, lighting
  motion, and cleaner judgement pop/fade movement.
- Persistent **Seasonal backgrounds** toggle that rotates through every PNG, JPG,
  JPEG, or BMP image imported into `Internal storage/opsu/seasonalbackgrounds`
  on Android, with no hard image-count limit.
- Android user data now lives in the visible shared internal-storage folder
  `Internal storage/opsu/` instead of the app-private `Android/data` tree.
- Beatmap downloads now use the maintained Mino, osu.direct, and Hinai mirrors
  instead of the retired Ripple, Mnetwork, Hexide, and Bloodcat endpoints.
- Modern Gradle 8.13, Android Gradle Plugin 8.13.2, and libGDX 1.14.2 build.
- 32-bit and 64-bit ARM/x86 native libraries.
- Runtime storage-permission handling with app-private storage fallback.

## License
**This software is licensed under GNU GPL version 3.**
You can find the full text of the license [here](LICENSE).
