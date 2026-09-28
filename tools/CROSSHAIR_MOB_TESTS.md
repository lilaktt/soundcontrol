# Crosshair mob sound groups

The mob branch of `SoundWorldRenderer.toggleSoundUnderCrosshair` now selects
`<entity namespace>:entity.<entity path>.*` from both the sound-event registry
and the sound manager. It no longer chooses a recently heard positional sound.
This applies to all 17 Fabric/NeoForge modules in settings.gradle.

These are global per-sound settings, like the existing block muting feature:
muting a zombie mutes the zombie sound family, not one entity UUID.

## NeoForge modded-mob resolution

All eight NeoForge modules now use `EntitySoundResolver` for both SoundList and
crosshair mute. Fabric's selection implementation is unchanged.

- Match modded families such as `mod:mob_idle`, `mod:mob/hurt`,
  `mod:entity.mob.hurt`, and `mod:mob.mobname.death`. Namespace and entity-name
  boundaries are retained; a longer registered mob name wins over a shorter one.
- Query actual ambient/hurt/death getters through virtual Mixin invokers.
  This finds differently named and shared sounds even before the mob speaks.
  A failing getter does not disable the other discovery paths.
- Recognize additional events in an explicit action family identified by a
  getter or observation, e.g. `crypt_voice.idle` -> `crypt_voice.attack`.
- Learn opaque mod sound IDs at playback before radar/anchor filtering. Only
  non-relative sounds with a matching mod namespace, mob sound category and
  tight positional origin qualify. Overlapping different mob types are
  ambiguous and are skipped. This is a conservative positional heuristic,
  not proof of sound ownership. Learned associations are cleared on world change.
- Include non-player `LivingEntity` implementations, not just `Mob` subclasses.
- Refresh SoundList every 500 ms while aiming at the same entity, so learned
  sounds appear without looking away. Both consumers use the same resolver.

There is no general Minecraft API enumerating every sound a mod could emit.
Opaque events not returned by getters cannot be known before playback, and
sounds emitted remotely, in a different category/namespace, or through custom
networking may need mod-specific support. A newly discovered unrelated opaque
ID is not automatically muted by a previous click. This remains a per-sound
group toggle, not an entity-level filter. Shared sound IDs are muted globally.

## Automated regression checks

From the repository root with Python and JDK 17+ on PATH:

```
python tools/test_crosshair_mob_groups.py
python tools/test_mod_mob_sounds.py
```

The test compiles and executes the actual selection snippets and
`SoundConfig.toggleSoundsMuted` methods extracted from all 17 modules, with
small API fixtures. It covers silent mobs, zombie ambient/hurt/death/step/door
sounds, resource-pack-only events, deduplication, namespace and entity-name
boundaries, a partially muted group, mute/unmute, volume preservation and
empty groups. It also checks that each handler uses the group toggle and
updates/stops selected sounds. This does not replace compiling against each
Minecraft API or testing in-game.

## Build

```
.\gradlew.bat build --continue --console=plain
```

## In-game smoke test (each supported loader/version)

1. Bind "Mute/Unmute Targeted Block or Mob" in Controls (unbound by default).
2. Spawn a zombie and a skeleton. Disable both sound overlays.
3. Aim directly at the zombie and press the key before it makes any sound.
   The confirmation should show the mob name, not a single sound ID.
4. Confirm that the zombie's ambient, hurt, step, death and door sounds are
   muted. Check the sound menu for the whole `minecraft:entity.zombie.*` group.
   Skeleton sounds must remain unchanged.
5. Aim at another silent zombie and press again. The whole group should
   become audible, with pre-existing nonzero per-sound volumes preserved.
6. Repeat with the radar enabled; all affected visible sound labels should
   animate, and already playing selected sounds should stop on mute.
7. Leave/rejoin or restart: check that the mute settings were saved.
8. Aim at a block, empty space, and a player: existing block behavior should
   remain unchanged; empty space and players must not mute nearby sounds.
9. Check a modded mob whose sounds use `modid:entity.mobname.*`; equally named
   sounds in another namespace must remain unchanged.
