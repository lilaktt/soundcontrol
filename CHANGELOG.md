## Sound Control 1.6.0

### New
- Added one-key sound muting: aim at a block or mob to mute its sound group. Press again to unmute.
- Ported Sound Anchors to all supported Minecraft versions on Fabric and NeoForge.
- Added separate anchor lists for each singleplayer world and multiplayer server.
- Added an option to move legacy anchors into the current world.

### Improvements
- Unified Sound Anchor behavior across loaders.
- Improved modded mob sound detection and SoundList support on NeoForge.
- Optimized Sound Anchor boundary rendering with screen clipping to avoid unnecessary off-screen drawing.
- Updated anchor menus with centered lists, properly positioned scrollbars, and consistent backgrounds.

### Fixes
- Fixed anchor settings not saving correctly.
- Fixed sound group overrides and overlapping anchor behavior.
- Fixed anchor outline alignment on Minecraft 1.20.1.
- Fixed a Fabric 1.21.11 camera API crash affecting anchors and the 3D radar.
- Fixed overlapping size fields and invisible fields receiving input.
