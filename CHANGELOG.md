## Sound Control 1.7.0

### New
- All 17 versions (Fabric 1.20.1–26.3, Forge 1.20.1, NeoForge 1.21.1–26.3) now share one interface and look the same.
- Mute a whole mod from the Mods list on every version.
- Config button in the NeoForge/Forge mod list (in addition to Mod Menu on Fabric).
- Click a sound name to copy its id, on every version.
- Better detection of modded mob sounds on Fabric too (crosshair mute and sound lookup).
- Deleting a profile now asks for a second click.

### Fixes
- Fixed a crash on Forge 1.20.1 together with Connector and Dynamic Surroundings (#2).
- NeoForge/Forge no longer crash when installed on a dedicated server.
- Fixed the Mod Menu config button on Fabric 1.21–26.3.
- Fixed the Mods list not reacting to clicks on Fabric 26.x and NeoForge.
- Renaming the active profile no longer resets to "default" after a restart.
- Profiles with similar or non-Latin names no longer overwrite each other's file.
- Settings are saved safely: a crash while saving can no longer corrupt a profile.
- Unmuting with the crosshair key no longer leaves hidden overrides behind.
- Fixed French translation showing escape codes, and translations being replaced by English.
- Layout fixes: the sound list no longer overlaps the side panels, buttons fit small windows.
- Less disk writing while dragging volume sliders.
