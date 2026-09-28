"""Compile/run the real NeoForge resolver against minimal client fixtures (JDK 17+).
Run from any directory: python tools/test_mod_mob_sounds.py.
Gradle and an in-game smoke test are still needed to validate actual Mixin application.
"""
from pathlib import Path
import json
import subprocess

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'build/mod-mob-tests'
SOURCES = {
'net/minecraft/resources/TestId.java': '''package net.minecraft.resources;
public record TestId(String value) {
 public String toString() { return value; }
}''',
'net/minecraft/core/registries/BuiltInRegistries.java': '''package net.minecraft.core.registries;
import java.util.*; import net.minecraft.resources.TestId;
public class BuiltInRegistries {
 public static Registry ENTITY_TYPE = new Registry(), SOUND_EVENT = new Registry();
 public static class Registry {
  public Set<TestId> ids = new LinkedHashSet<>();
  public Set<TestId> keySet() { return ids; }
  public TestId getKey(String type) { return new TestId(type); }
 }
}''',
'net/minecraft/sounds/SoundEvent.java': '''package net.minecraft.sounds;
import net.minecraft.resources.TestId;
public record SoundEvent(String id) {
 public TestId getLocation() { return new TestId(id); }
 public TestId location() { return getLocation(); }
}''',
'net/minecraft/world/damagesource/DamageSource.java': '''package net.minecraft.world.damagesource;
public class DamageSource { public DamageSource generic() { return this; } }''',
'net/minecraft/world/entity/Entity.java': '''package net.minecraft.world.entity;
public class Entity {
 public String type; public double x, y, z;
 public Entity(String type) { this.type = type; }
 public String getType() { return type; }
 public double getX() { return x; } public double getY() { return y; } public double getZ() { return z; }
 public float getBbHeight() { return 2; }
}''',
'net/minecraft/world/entity/LivingEntity.java': '''package net.minecraft.world.entity;
import net.minecraft.world.damagesource.DamageSource;
public class LivingEntity extends Entity {
 public LivingEntity(String type) { super(type); }
 public String getSoundSource() { return "hostile"; }
 public DamageSource damageSources() { return new DamageSource(); }
}''',
'net/minecraft/world/entity/Mob.java': '''package net.minecraft.world.entity;
public class Mob extends LivingEntity { public Mob(String type) { super(type); } }''',
'net/minecraft/world/entity/player/Player.java': '''package net.minecraft.world.entity.player;
public class Player extends net.minecraft.world.entity.LivingEntity { public Player() { super("mod:player"); } }''',
'net/minecraft/client/multiplayer/ClientLevel.java': '''package net.minecraft.client.multiplayer;
import java.util.*; import net.minecraft.world.entity.Entity;
public class ClientLevel {
 public List<Entity> entities = new ArrayList<>();
 public Iterable<Entity> entitiesForRendering() { return entities; }
}''',
'net/minecraft/client/resources/sounds/SoundInstance.java': '''package net.minecraft.client.resources.sounds;
public record SoundInstance(double x, double y, double z, boolean relative, String source) {
 public double getX() { return x; } public double getY() { return y; } public double getZ() { return z; }
 public boolean isRelative() { return relative; } public String getSource() { return source; }
}''',
'net/minecraft/client/Minecraft.java': '''package net.minecraft.client;
import java.util.*; import net.minecraft.client.multiplayer.ClientLevel; import net.minecraft.resources.TestId;
public class Minecraft {
 private static final Minecraft INSTANCE = new Minecraft();
 public ClientLevel level = new ClientLevel();
 public boolean mainThread = true; public List<Runnable> queued = new ArrayList<>();
 public final Manager manager = new Manager();
 public static Minecraft getInstance() { return INSTANCE; }
 public boolean isSameThread() { return mainThread; }
 public void execute(Runnable task) { queued.add(task); }
 public Manager getSoundManager() { return manager; }
 public static class Manager {
  public Set<TestId> ids = new LinkedHashSet<>();
  public Set<TestId> getAvailableSounds() { return ids; }
 }
}''',
'org/slf4j/Logger.java': '''package org.slf4j;
public interface Logger { default void debug(String message, Object... args) {} }''',
'org/slf4j/LoggerFactory.java': '''package org.slf4j;
public class LoggerFactory { public static Logger getLogger(String name) { return new Logger() {}; } }''',
# Stub only the annotations. Compile the real invoker declarations, not copies of their methods.
'org/spongepowered/asm/mixin/Mixin.java': '''package org.spongepowered.asm.mixin;
public @interface Mixin { Class<?> value(); }''',
'org/spongepowered/asm/mixin/gen/Invoker.java': '''package org.spongepowered.asm.mixin.gen;
public @interface Invoker { String value(); }''',
'ModMobSoundsTest.java': '''
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.TestId;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import soundcontrol.*;
import soundcontrol.mixin.*;
public class ModMobSoundsTest {
 static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
 static Set<TestId> ids(String... values) {
  Set<TestId> result = new LinkedHashSet<>(); for (String v : values) result.add(new TestId(v)); return result;
 }
 static class GetterMob extends Mob implements MobSoundAccessor, LivingEntitySoundAccessor {
  boolean broken;
  GetterMob(String type) { super(type); }
  public SoundEvent soundcontrol$getAmbientSound() {
   if (broken) throw new IllegalStateException("Server-only getter");
   return new SoundEvent("mod:crypt_voice.idle");
  }
  public SoundEvent soundcontrol$getHurtSound(DamageSource source) { return new SoundEvent("mod:ouch"); }
  public SoundEvent soundcontrol$getDeathSound() { return new SoundEvent("shared:final_breath"); }
 }
 static class CustomLiving extends LivingEntity implements LivingEntitySoundAccessor {
  CustomLiving() { super("mod:living_boss"); }
  public SoundEvent soundcontrol$getHurtSound(DamageSource source) { return new SoundEvent("mod:custom_hurt"); }
  public SoundEvent soundcontrol$getDeathSound() { return null; }
 }
 static void matcherTests() {
  Set<String> sounds = Set.of("mod:entity.wolf.ambient", "mod:wolf_idle", "mod:wolf/hurt", "mod:mob.wolf.death",
    "mod:entity_wolf_step", "mod:wolfhound_idle", "mod:wolf_hound_hurt", "mod:entity.wolf.hound.hurt",
    "mod:owl_idle", "other:wolf_idle", "mod:ui.click");
  Set<String> result = EntitySoundMatcher.collect("mod:wolf", sounds,
    Set.of("mod:wolf", "mod:wolf_hound", "mod:wolf/hound", "mod:owl"), Set.of());
  check(result.equals(Set.of("mod:entity.wolf.ambient", "mod:wolf_idle", "mod:wolf/hurt",
    "mod:mob.wolf.death", "mod:entity_wolf_step")), "alternate names / longest mob name / namespace: " + result);
  result = EntitySoundMatcher.collect("mod:crypt_keeper", Set.of("mod:crypt_voice.idle", "mod:crypt_voice.attack",
    "mod:crypt_voice.step", "mod:crypt_voice_other.hurt", "other:crypt_voice.attack"), Set.of(), Set.of("mod:crypt_voice.idle"));
  check(result.equals(Set.of("mod:crypt_voice.idle", "mod:crypt_voice.attack", "mod:crypt_voice.step")), "getter-derived family");
  check(EntitySoundMatcher.collect("minecraft:zombie", Set.of("minecraft:entity.zombie.hurt",
    "minecraft:entity.zombie_villager.hurt", "mod:entity.zombie.hurt"), Set.of(), Set.of())
    .equals(Set.of("minecraft:entity.zombie.hurt")), "vanilla regression");
  check(EntitySoundMatcher.collect("mod:unknown", sounds, Set.of(), Set.of()).isEmpty(), "no namespace-wide fallback");
  check(EntitySoundMatcher.collect("mod:mob", Set.of("mod:mob_idle"), Set.of(), Set.of())
    .equals(Set.of("mod:mob_idle")), "entity name may itself be a sound-kind prefix");
 }
 public static void main(String[] args) {
  matcherTests();
  Minecraft client = Minecraft.getInstance();
  BuiltInRegistries.ENTITY_TYPE.ids = ids("mod:crypt_keeper", "mod:owl", "mod:living_boss");
  BuiltInRegistries.SOUND_EVENT.ids = ids("mod:crypt_voice.idle", "mod:crypt_voice.attack", "mod:crypt_voice.step", "mod:owl_idle");
  client.manager.ids = ids("mod:crypt_voice.pack_extra");
  GetterMob mob = new GetterMob("mod:crypt_keeper");
  Set<String> expected = Set.of("mod:crypt_voice.idle", "mod:crypt_voice.attack", "mod:crypt_voice.step",
    "mod:crypt_voice.pack_extra", "mod:ouch", "shared:final_breath");
  check(EntitySoundResolver.resolve(client, mob).equals(expected), "silent mob: actual getters + registry + resources");
  check(EntitySoundResolver.resolve(client, new CustomLiving()).contains("mod:custom_hurt"), "LivingEntity not Mob");
  mob.broken = true;
  check(EntitySoundResolver.resolve(client, mob).contains("mod:ouch"), "broken getter must not disable other getters");
  mob.broken = false;
  Mob opaque = new Mob("mod:opaque");
  client.level.entities.add(opaque);
  SoundInstance sound = new SoundInstance(0, 1, 0, false, "hostile");
  EntitySoundResolver.record(sound, "mod:unnamed_noise");
  check(EntitySoundResolver.resolve(client, opaque).contains("mod:unnamed_noise"), "radar observation while overlay off");
  EntitySoundResolver.record(sound, "other:noise");
  EntitySoundResolver.record(sound, "mod:block.machine");
  EntitySoundResolver.record(new SoundInstance(4, 1, 0, false, "hostile"), "mod:distant");
  EntitySoundResolver.record(new SoundInstance(0, 1, 0, true, "hostile"), "mod:relative");
  EntitySoundResolver.record(new SoundInstance(0, 1, 0, false, "music"), "mod:music_category");
  check(EntitySoundResolver.resolve(client, opaque).equals(Set.of("mod:unnamed_noise")), "unrelated sounds not learned");
  Mob other = new Mob("mod:other"); client.level.entities.add(other);
  EntitySoundResolver.record(sound, "mod:ambiguous");
  check(!EntitySoundResolver.resolve(client, opaque).contains("mod:ambiguous"), "overlapping different mobs");
  client.level.entities.remove(other); client.level.entities.add(new Mob("mod:opaque"));
  EntitySoundResolver.record(sound, "mod:same_type_noise");
  check(EntitySoundResolver.resolve(client, opaque).contains("mod:same_type_noise"), "same type overlap is safe for group");
  client.mainThread = false;
  EntitySoundResolver.record(sound, "mod:queued_noise");
  check(!EntitySoundResolver.resolve(client, opaque).contains("mod:queued_noise"), "off-thread observation queued");
  client.mainThread = true; client.queued.forEach(Runnable::run);
  check(EntitySoundResolver.resolve(client, opaque).contains("mod:queued_noise"), "queued observation processed");
  client.level = new ClientLevel();
  check(EntitySoundResolver.resolve(client, opaque).isEmpty(), "world change clears observations");
  client.level.entities.add(new Player());
  EntitySoundResolver.record(sound, "mod:player_noise");
  check(EntitySoundResolver.resolve(client, new Player()).isEmpty(), "players are not observed as mobs");
  client.level = null;
  EntitySoundResolver.record(sound, "mod:outside_world");
  check(EntitySoundResolver.resolve(client, opaque).isEmpty(), "disconnect clears observations");
  System.out.println("PASS resolver, getters, naming, resources, observations, ambiguity, threads, world reset");
 }
}''',
}
fixture_dir = OUT / 'fixtures'
for name, content in SOURCES.items():
    p = fixture_dir / name
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding='utf-8')
ports = sorted(ROOT.glob('neoforge-*'))
assert len(ports) == 8
canonical = None
for port in ports:
    java = port / 'src/main/java/soundcontrol'
    matcher = (java / 'EntitySoundMatcher.java').read_text(encoding='utf-8')
    resolver = (java / 'EntitySoundResolver.java').read_text(encoding='utf-8').replace('sound.location()', 'sound.getLocation()')
    pair = (matcher, resolver)
    if canonical is None:
        canonical = pair
    assert pair == canonical, f'{port.name}: resolver drift'
    world = (java / 'SoundWorldRenderer.java').read_text(encoding='utf-8')
    lookup = next(java.rglob('SoundLookupRenderer.java')).read_text(encoding='utf-8')
    assert 'EntitySoundResolver.resolve(client, target)' in world
    assert 'EntitySoundResolver.record(sound, soundId)' in world
    assert 'EntitySoundResolver.resolve(client, entity)' in lookup
    assert 'now >= nextEntityRefresh' in lookup
    assert 'net.minecraft.world.entity.LivingEntity' in world
    mixins = json.loads((port / 'src/main/resources/soundcontrol.mixins.json').read_text())['client']
    assert 'MobSoundAccessor' in mixins and 'LivingEntitySoundAccessor' in mixins
    classes = OUT / port.name
    classes.mkdir(parents=True, exist_ok=True)
    files = list(fixture_dir.rglob('*.java')) + [java / n for n in (
        'EntitySoundMatcher.java', 'EntitySoundResolver.java',
        'mixin/MobSoundAccessor.java', 'mixin/LivingEntitySoundAccessor.java')]
    subprocess.run(['javac', '--release', '17', '-encoding', 'UTF-8', '-d', str(classes), *map(str, files)], check=True)
    print(port.name, flush=True)
    subprocess.run(['java', '-cp', str(classes), 'ModMobSoundsTest'], check=True)
