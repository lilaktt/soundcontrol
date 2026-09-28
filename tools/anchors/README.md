# Sound Anchor port and loader parity

Anchors are implemented in all 17 configured platform modules (9 Fabric, 8
NeoForge, including the legacy Forge-based `neoforge-1.20.1` module).

## Features and behavior

- Open Sound Control and click the anchor icon next to Recent Sounds.
- Create an anchor at the player's position, rename, enable/disable, delete,
  show/hide its outline, switch between sphere and box, edit radius or W/H/D.
- Edit overrides with mute and 0–200% volume controls. Add/remove individual
  sounds or basic groups through Browse; select namespaces, categories,
  all/added/favorite filters. Recent uses the same picker restricted to the
  recorded history. Full mod namespace IDs remain visible in the list.
- The same templates generate screens, geometry, projection and picker code
  for both loaders; adapters only change Minecraft API names/signatures.
- Zones evaluate the **sound source** position, not the listener position.
  Relative/UI preview sounds bypass zones.
- Exact sound settings take precedence over basic groups, then global groups.
  Overlapping anchors use the quietest matching override, independent of order.
- A profile/global mute takes precedence over an anchor. For audible profiles,
  an anchor replaces the profile volume while inside its area, as in 26.x.
- Existing active sources get updated each unpaused sound-engine tick, including
  ordinary looping sounds. Deleting the last anchor triggers a final refresh.
  A sound which Minecraft never started because its initial volume was zero
  cannot be resumed by this refresh; it needs to be emitted again normally.
- Radius/dimensions are limited to 1–999. Box geometry preserves the actual
  center, including odd dimensions/negative coordinates, and uses exactly the
  same bounds for containment and rendering. This corrects the old rounding
  asymmetry: old boxes with odd sizes may have their boundary shift by 0.5 block.
- Near-camera edges are clipped rather than discarded; screen clipping bounds
  rasterization work. No new OpenGL state manipulation is required.

## Persistence / compatibility

Anchors stay in `config/soundcontrol/settings.json`, independent of profiles,
with an `anchorsByWorld` map added in 1.6.0. Anchor controls call `saveSettings`, not
profile `save`. Writes use a UTF-8 temporary file and atomic replacement where
supported (replacement fallback otherwise). Old missing/null fields are handled.
Old `ResourceKey[minecraft:dimension / minecraft:overworld]` dimension strings
remain accepted alongside `minecraft:overworld`.

### World isolation in 1.6.0

Each singleplayer save folder has its own list in `anchorsByWorld`. The key uses
the normalized/canonical absolute save-folder path, not the display name or
seed. Renaming the world in the Minecraft menu retains the list; physically
moving the folder changes its key. Deleting a world and reusing the same folder
path reuses that path's list. This is folder identity, not a world UUID.

Multiplayer lists are separate by the connection's server address (case and
whitespace normalized; omitted port and `:25565` are equivalent). Non-default
ports remain distinct. Two addresses for the same server stay separate. The
client cannot distinguish multiple server-side worlds behind the same address
and dimension without server support; they share that server's list. Joining
another person's LAN world uses the LAN address/port. The host uses its save
folder, including when opening its world to LAN.

Dimension matching still applies inside each world/server. With no current
world or no usable connection identity, no anchors are returned or applied.
The anchor management screen, renderer and audio all use this same scoped
lookup. Anchor editors close when the world context changes to avoid stale
references into a previous world.

The old `anchors` list is retained but no longer applied globally. Because the
old schema stored no world identity, **nothing is automatically assigned to the
first world opened**. Enter the intended world and click **Move old anchors into
this world** in the anchor screen. This explicitly moves all unassigned old
anchors into that world's list and saves once; it does not copy them to other
worlds. Repeated import cannot duplicate anchors. Make a backup first, especially
if old anchors originally came from several worlds and need manual sorting.

Nothing is installed on a server. Back up `settings.json` before testing, and
do not downgrade to an older mod version that does not understand `anchorsByWorld`.

## Generation and checks

From `sc common`:

```powershell
python tools/anchors/port_anchors.py
python tools/anchors/integrate_audio.py
python tools/anchors/finish_integration.py
python tools/anchors/test_anchors.py
python tools/anchors/test_projection.py
python tools/anchors/test_layout.py
python tools/anchors/test_world_context.py
python tools/test_crosshair_mob_groups.py
python tools/test_mod_mob_sounds.py
.\gradlew.bat build --continue --console=plain --no-daemon --max-workers=2
```

`port_anchors.py` regenerates anchor-related classes from templates; do not put
manual platform-specific edits in generated classes. It preserves other UI and
config methods. `integrate_audio.py` installs audio hooks once; changing those
hooks later requires editing the integration script **and** installed hooks.
`finish_integration.py` supplies legacy initial-play interception/refmaps, safe
settings saving, and locale keys. No external code is downloaded by these scripts.

The headless tests compile actual code extracted from each port with real Gson
and JOML and small Minecraft fixtures. They cover geometry, invalid JSON, Unicode
persistence, deletion, selection groups, overlaps, relative sounds, profile mute,
moving source evaluation, near-camera clipping and 10,000 randomized screen clips.
They also check button/hook wiring and locale availability. They do **not** prove
runtime Mixin application, OpenAL output or actual GUI interactions.

## Required in-game smoke test

Test at least Fabric/NeoForge 1.20.1, 1.21.x and 26.x separately:

1. Open the anchor icon, create/rename a sphere and change its radius. Turn the
   outline off/on; switch to a box with odd sizes at negative coordinates.
2. Browse all sounds, a basic zombie group, global footsteps and a mod namespace;
   add them, search, filter Added/Favorites, remove entries, and test Recent.
3. Verify muted sources inside, audible sources outside, and no effects in another
   dimension. Verify overlapping anchors and a globally muted sound with a loud
   anchor override. UI sound previews must not be zone-muted.
4. With a source already looping, change/move/disable/delete its anchor and verify
   the audio updates. Repeat with a moving mob entering and leaving a zone.
5. Resize the window and change GUI scale; check scrollbars, sliders, keyboard
   focus and that hidden radius/box fields no longer receive input.
6. Walk through the anchor boundary and turn the camera through the outline.
7. Quit/restart and confirm names, overrides, sizes, enabled/outline flags and
   deletions persisted. Load old 26.x settings as a migration check.
8. Confirm the prior crosshair mob mute, SoundList and radar still work.
9. Create an anchor in world A, then visit B with the same dimension and
   coordinates. B must show an empty list and must not apply A's audio overrides.
   Add one in B, return to A, restart Minecraft and verify both lists persist.
10. Repeat with two multiplayer addresses. Also change dimensions within one
    world: the same list remains available, but only matching-dimension zones act.
11. Load pre-1.6 settings: old anchors must not appear/apply until explicitly
    imported in the intended world. Import once, restart, and visit another world.

Sources before this port were copied to `build/anchor-port-backup/`. Build logs
and generated test fixtures are also under `build/`; they are not release files.

## Compact anchor-list layout fix

The anchor management row is 248 GUI pixels wide rather than 360. Its left
edge centers the row plus a 4-pixel gap and 6-pixel scrollbar as one unit.
The sound count sits on the second row; W/H/D fields no longer overlap.
`test_layout.py` checks the actual generated coordinate methods for all 17
ports over GUI widths 320?1920. In-game mouse/focus and visual verification
remain necessary, particularly Fabric 1.21.6 and NeoForge 1.21.11.

## Fabric 1.21.11 camera crash hotfix

The Fabric 1.21.9-family build now calls `Camera.getCameraPos()` in both the
anchor renderer and 3D radar. `getPos()` (`method_19326`) was removed in 1.21.11;
`getCameraPos()` (`method_71156`) exists with the same Vec3d descriptor in 1.21.9
and 1.21.11. The generator retains this choice. The patch build is 1.5.2-1.21.9.

`python tools/anchors/test_camera_compat.py [remapped-jar]` checks the actual
cached Minecraft bytecode/mappings and optionally the remapped mod jar. It is
not a complete 1.21.11 runtime test; still verify world entry with a visible
anchor, third-person view, and the 3D radar in a launched client.

## Building release 1.6.0

`release_1_6_0.py` updates only mod versions (retaining Minecraft/loader suffixes)
and adds the explicit legacy-import label in all 12 locales. Run the Gradle build
and the tests above, then `python tools/anchors/package_1_6_0.py` to verify actual
JAR metadata and collect the 17 files under `build/soundcontrol-1.6.0/`.
`test_world_context.py` compiles the actual adapters against small client fixtures;
`test_anchors.py` executes actual config/volume methods for cross-world isolation,
A-B-A persistence, server separation, null context and one-time explicit migration.
These tests do not replace testing world transitions in a launched game.
