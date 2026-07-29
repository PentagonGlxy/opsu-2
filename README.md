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
- Automatic preference for the display's highest compatible refresh rate.
- Android-compatible OpenGL boolean state queries, preventing slider-render crashes.
- Modern Gradle 8.13, Android Gradle Plugin 8.13.2, and libGDX 1.14.2 build.
- 32-bit and 64-bit ARM/x86 native libraries.
- Runtime storage-permission handling with app-private storage fallback.

## osu!stable gameplay fidelity

Several gameplay formulas in upstream opsu! were approximations, some of them
explicitly marked `TODO`.  These have been replaced with the osu!stable
behaviour:

- No early-miss window.  A press outside the 50 window is discarded instead of
  breaking combo, matching stable's shake behaviour.
- Strict in-order note lock, restoring stable's "shielding" on stacks and
  streams.
- Stable's fade-in formula, `400 * min(1, preempt / 450)`, instead of
  `min(375, preempt / 2.5)`.
- Hidden mod fades derived from the preempt time rather than guessed divisors.
- Spinners requiring stable's 3/5/7.5 rotations per second at OD 0/5/10, graded
  on absolute rotation count instead of ratio thresholds.

See [docs/osu-stable-fidelity.md](docs/osu-stable-fidelity.md) for the full
divergence analysis, including what is still outstanding (sliders, Hidden
fade-in duration, scoring and HP drain).

## License
**This software is licensed under GNU GPL version 3.**
You can find the full text of the license [here](LICENSE).
