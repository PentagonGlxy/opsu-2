# Core overlays

This directory mirrors the layout of the `core` submodule. Every file here is
copied over its counterpart in `core/` by the `applyCorePatches` task, after all
of `patches/core/*.patch` have been applied.

## Why this exists

Most changes to the pinned core belong in `patches/core/` as ordinary diffs: a
diff shows reviewers exactly what changed, and it conflicts loudly if the pinned
revision moves.

Overlays exist for the cases where a context diff is more trouble than it is
worth. `MainMenu.java` was the motivating example -- its patch was rejected
repeatedly by `git apply`, and GNU `patch` would only place one hunk after
discarding its context entirely (`succeeded at 350 with fuzz 2`), which is not a
basis for a reproducible build. A whole-file replacement has no context to
match, so it either compiles or it does not.

## Rules

1. **Never overlay a file that a patch also modifies.** The overlay runs last and
   would silently discard the patch. Today no patch touches `MainMenu.java`.
2. **Overlays are pinned to a core revision.** These files were taken from
   `fluddokt/opsu` at `2748da438e05bfc065d96d60b482daaeb4635956`. If the
   submodule is bumped, re-derive each overlay from the new upstream file rather
   than assuming it still applies -- an overlay cannot detect that it has gone
   stale, which is the one real advantage a patch has over it.
3. **Keep the diff from upstream small and commented**, so the delta stays
   reviewable even though the file is stored in full.

## Contents

### `src/itdelatrisu/opsu/states/MainMenu.java`

Upstream file plus seasonal background integration:

- imports `itdelatrisu.opsu.ui.SeasonalBackground`
- `render()` draws the seasonal image aspect-filled ahead of the dynamic
  background, falling back to existing behaviour when `getImage()` is `null`
- `enter()` calls `invalidate()` then `reroll()`, so images copied in while the
  game was running are picked up without a restart
- `leave()` calls `unload()` to release the texture when leaving the menu
- `nextTrack()` / `previousTrack()` reroll and restart the background fade

Each addition is marked with a `seasonal background` comment in the source.
