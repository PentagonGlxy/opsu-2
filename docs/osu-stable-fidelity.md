# osu!stable gameplay fidelity

This document tracks how opsu!'s gameplay differs from osu!stable, which
divergences have been corrected, and which are still outstanding.

All gameplay code lives in the pinned `core` submodule
([fluddokt/opsu](https://github.com/fluddokt/opsu)), so every change here
is expressed as a versioned patch in `patches/core/` and applied by the
`applyCorePatches` Gradle task.

## Corrected

### Early-miss window (`0005`)

opsu! defined a fourth hit window, `hitResultOffset[HIT_MISS] = 500 - OD * 10`,
and `Circle.hitResult()` awarded a real MISS for any press inside it.
osu!stable has no such window: a press outside the 50 window is discarded
and the circle shakes. Clicking ~400ms early no longer breaks combo.

### Note lock (`0005`)

osu!stable enforces strict in-order resolution -- object N + 1 cannot be
judged until N resolves. opsu! iterated the entire `passedObjects` list and
then the current object, so any unresolved object could be hit in any order.
Only the earliest unresolved object is now eligible, and a press that fails
to resolve it is swallowed rather than falling through. This restores
stable's "shielding" feel on stacks and streams.

### Fade-in time (`0006`)

| | Formula | AR 9 (600ms preempt) |
| --- | --- | --- |
| opsu! | `min(375, preempt / 2.5)` (marked TODO) | 240ms |
| osu!stable | `400 * min(1, preempt / 450)` | 400ms |

### Hidden mod fades (`0006`)

Upstream used `preempt / 3.6` and `preempt / 3.3` with a
`// TODO: find the actual formulas for this`. Stable derives these from the
preempt time: fade-in completes at `0.4 * preempt`, decay runs for
`0.3 * preempt`, and objects are fully invisible with `0.3 * preempt`
remaining.

### Spinner requirements and grading (`0007`)

| | Required spin rate at OD 5 |
| --- | --- |
| opsu! (`100 + OD * 15` per minute) | ~2.9 rot/s |
| osu!stable | 5 rot/s |

Grading also moved from ratio thresholds (`1.0 / 0.9 / 0.75`, marked
`// TODO: verify ratios`) to stable's absolute rotation counts: full clear
is a 300, one rotation short a 100, two short a 50.

## Verified as already faithful

- **Hit windows.** `mapDifficultyRange(OD, 80, 50, 20)`,
  `(140, 100, 60)` and `(200, 150, 100)` match stable's `80 - 6 * OD`,
  `140 - 8 * OD` and `200 - 10 * OD`.
- **Approach/preempt time.** `mapDifficultyRange(AR, 1800, 1200, 450)`
  matches stable.
- **Stack offset.** `STACK_OFFSET_MODIFIER = 0.05f` applied to the circle
  diameter equals stable's `radius / 10`, and the stack search window uses
  `preempt * stackLeniency` correctly.

## Outstanding

- **Hidden fade-in duration.** Stable uses `0.4 * preempt` for the fade-in
  under Hidden specifically, whereas `Circle.draw` still reuses the global
  `fadeInTime`. Needs a separate accessor on `Game`.
- **Sliders.** `Slider.java` has not been audited yet: follow-circle radius,
  tick placement, repeat and end-tick judgements, and slider-break handling
  all need comparison against stable.
- **Spinner RPM smoothing.** The stored-delta-angle window
  (`minVel`/`maxVel` interpolation) is still the hand-measured
  approximation from the upstream RPM table.
- **Scoring and HP.** ScoreV1 multipliers and `BeatmapHPDropRateCalculator`
  have not been verified against stable.
