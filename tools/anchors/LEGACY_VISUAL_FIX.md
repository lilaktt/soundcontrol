# Anchor visuals on 1.20.1

This patch applies to Fabric 1.20.1 and the legacy Forge-based module named
`neoforge-1.20.1`. Mod version remains 1.6.0.

## Changes

- SoundAnchorScreen, SoundAnchorEditScreen and AllSoundsPickerScreen draw the
  same `0xC0101010` full-screen dimming layer as the main Sound Control screen,
  before their controls. The list viewport adds a second `0x80000000` layer,
  leaving the header and footer lighter, matching the main menu. Its boundaries
  come from the list constructor, including the shorter list during legacy import.
  Browse and Recent use the same picker.
- AnchorWorldRenderMixin captures the actual world-render view/projection matrices
  and camera origin at the beginning of WorldRenderer.render / LevelRenderer.renderLevel.
- AnchorRenderFrame copies `projection * view`; the HUD uses that snapshot rather
  than constructing perspective from the options FOV and camera quaternion.
  Dynamic FOV, view bobbing, hurt effects and the world's camera roll consequently
  use the same transforms for the world and anchor overlay.
- Snapshots are world-bound and consumed once. They do not mutate game matrices
  or hold a strong world reference. The geometry/containment model is unchanged.
- The legacy SRG refmap includes the new render hook. It is client-only on both
  loaders. Generator templates preserve the changes.

## Verification

`python tools/anchors/test_legacy_visuals.py` checks actual frame capture and
screen wiring on both loaders, using real JOML. It tests 100 camera/FOV combinations,
800 corner transforms, matrix copy isolation, one-shot consumption, world changes,
and ordering of menu background versus controls. The existing projection tests
also exercise clipped lines and nonfinite input. These are headless tests, not
in-game validation of Mixin application or the visible result.

The current repository has a separate build configuration mismatch:
settings.gradle includes fabric-1.21.3, but that folder is now fabric-1.21.2.
This visual patch does not rename either. A scoped verification build is under
`build/legacy-visual-verification`, using the original root build logic and only
common + the two 1.20.1 modules:

```
.\gradlew.bat -p build/legacy-visual-verification :fabric-1.20.1:build :neoforge-1.20.1:build --no-daemon --max-workers=2
```

## In-game check still required

Open the anchor list, edit screen, Browse and Recent: world dimming should be
visible while controls remain readable. Place a box matching known block corners,
then turn yaw/pitch, move, sprint, switch first/third person, toggle view bobbing,
and vary FOV. The outline should remain aligned with the same world locations.
Repeat on each loader. Ordinary perspective changes as you move/look are expected;
the stored center, radius and box sizes must not change. Other versions and
third-party shader renderers have not been runtime-tested by this patch.
