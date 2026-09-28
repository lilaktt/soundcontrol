"""Headless regression checks for the actual selection/toggle snippets in all 17 ports.
Run: python tools/test_crosshair_mob_groups.py (requires a JDK 17+).
Minecraft API compatibility is checked separately by Gradle builds.
"""
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'build/crosshair-mob-tests'


def method_body(source, signature):
    start = source.index('{', source.index(signature)) + 1
    depth = 1
    for end in range(start, len(source)):
        depth += (source[end] == '{') - (source[end] == '}')
        if depth == 0:
            return source[start:end]
    raise AssertionError('Unclosed method')


HARNESS = '''
import java.util.*;
public class CrosshairMobGroupsTest {
    record Id(String value) {
        public String getNamespace() { return value.split(":", 2)[0]; }
        public String getPath() { return value.split(":", 2)[1]; }
        public String toString() { return value; }
    }
    record Name(String value) { public String getString() { return value; } }
    record Target(Id type) {
        public Id getType() { return type; }
        public Name getName() { return new Name("Target mob"); }
    }
    record Hit(Target entity) { public Target getEntity() { return entity; } }
    static class Registry {
        static Registry ENTITY_TYPE = new Registry(), SOUND_EVENT = new Registry();
        Set<Id> ids = new LinkedHashSet<>();
        Id getId(Id type) { return type; }
        Id getKey(Id type) { return type; }
        Set<Id> getIds() { return ids; }
        Set<Id> keySet() { return ids; }
    }
    static class Manager {
        Set<Id> ids = new LinkedHashSet<>();
        Set<Id> getKeys() { return ids; }
        Set<Id> getAvailableSounds() { return ids; }
    }
    static class Client {
        Object hitResult, crosshairTarget;
        Manager manager = new Manager();
        Manager getSoundManager() { return manager; }
        Client(String type) { hitResult = crosshairTarget = new Hit(new Target(new Id(type))); }
    }
    // The integration of NeoForge's real resolver is covered by test_mod_mob_sounds.py.
    // Here use its pure matcher to retain the existing 17-port group-toggle checks.
    static class EntitySoundResolver {
        static Set<String> resolve(Client client, Target target) {
            Set<String> available = new LinkedHashSet<>();
            for (Id id : Registry.SOUND_EVENT.ids) available.add(id.toString());
            for (Id id : client.manager.ids) available.add(id.toString());
            return soundcontrol.EntitySoundMatcher.collect(target.type().toString(), available, Set.of(), Set.of());
        }
    }
    static class SoundSettings {
        boolean muted, overrideParent;
        float volume = 1.0f;
    }
    static Map<String, SoundSettings> settings = new HashMap<>();
    static Map<String, SoundSettings> sounds() { return settings; }
    static int saves;
    static void save() { saves++; }
    static float getVolumeModifier(String id) {
        SoundSettings s = settings.get(id);
        return s == null ? 1 : s.muted ? 0 : s.volume;
    }
    static Set<Id> ids(String... values) {
        Set<Id> result = new LinkedHashSet<>();
        for (String value : values) result.add(new Id(value));
        return result;
    }
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    interface Select { Set<String> run(Client client); }
    interface Toggle { boolean run(Collection<String> ids); }
    static void test(String port, Select select, Toggle toggle) {
        // No active sound history exists in this harness: selection must work while silent.
        Client client = new Client("minecraft:zombie");
        Registry.SOUND_EVENT.ids = ids("minecraft:entity.zombie.ambient",
            "minecraft:entity.zombie.hurt", "minecraft:entity.zombie.death",
            "minecraft:entity.zombie.step", "minecraft:entity.zombie.attack_wooden_door",
            "minecraft:entity.zombie_villager.hurt", "minecraft:entity.zombified_piglin.hurt",
            "minecraft:entity.skeleton.hurt", "other:entity.zombie.hurt", "minecraft:block.stone.break");
        Set<String> expected = Set.of("minecraft:entity.zombie.ambient", "minecraft:entity.zombie.hurt",
            "minecraft:entity.zombie.death", "minecraft:entity.zombie.step",
            "minecraft:entity.zombie.attack_wooden_door");
        check(select.run(client).equals(expected), port + ": silent mob / namespace / boundary");
        client.manager.ids = ids("minecraft:entity.zombie.hurt", "minecraft:entity.zombie.pack_extra");
        Set<String> selected = select.run(client);
        check(selected.size() == 6 && selected.containsAll(expected), port + ": resource pack union");
        settings.clear(); saves = 0;
        SoundSettings previous = new SoundSettings(); previous.muted = true; previous.volume = 0.4f;
        settings.put("minecraft:entity.zombie.hurt", previous);
        check(toggle.run(selected), port + ": partly muted family should mute all");
        check(selected.stream().allMatch(id -> getVolumeModifier(id) == 0), port + ": all muted");
        check(!toggle.run(selected), port + ": second press should unmute all");
        check(selected.stream().allMatch(id -> getVolumeModifier(id) > 0), port + ": all restored");
        check(previous.volume == 0.4f, port + ": nonzero volume preserved");
        check(settings.size() == selected.size() && saves == 2, port + ": unrelated settings / save once");
        check(!toggle.run(Set.of()) && saves == 2, port + ": empty group does not save");
        check(select.run(new Client("minecraft:unknown")).isEmpty(), port + ": unknown family");
        check(select.run(new Client("other:zombie")).equals(Set.of("other:entity.zombie.hurt")),
            port + ": modded namespace");
        System.out.println("PASS " + port);
    }
'''

paths = sorted(ROOT.glob('*/src/main/java/soundcontrol/**/SoundWorldRenderer.java'))
assert len(paths) == 17, len(paths)
calls = []
for index, path in enumerate(paths):
    source = path.read_text(encoding='utf-8')
    handler = method_body(source, 'public static boolean toggleSoundUnderCrosshair(')
    assert 'best.soundId' not in handler, path
    assert 'SoundConfig.toggleSoundsMuted(ids)' in handler, path
    assert 'if (ids.contains(event.soundId))' in handler, path
    assert 'if (muted)' in handler and ('stopSoundId' in handler or '.stop' in handler), path
    begin = handler.index('var target =')
    end = handler.index('label = target.getName().getString();', begin) + len('label = target.getName().getString();')
    selection = handler[begin:end]
    assert 'activeSounds' not in selection, path
    selection = re.sub(r'net\.minecraft\.(?:registry\.Registries|core\.registries\.BuiltInRegistries)', 'Registry', selection)
    selection = re.sub(r'net\.minecraft\.(?:util\.hit|world\.phys)\.EntityHitResult', 'Hit', selection)
    config = path.parents[0]
    while config.name != 'soundcontrol':
        config = config.parent
    toggle = method_body((config / 'SoundConfig.java').read_text(encoding='utf-8'),
                         'public static boolean toggleSoundsMuted(')
    HARNESS += f'''static Set<String> select{index}(Client client) {{
        Set<String> ids = new LinkedHashSet<>(); String label;
        {selection}
        check(label.equals("Target mob"), "Mob label");
        return ids;
    }}
    static boolean toggle{index}(Collection<String> ids) {{ {toggle} }}
'''
    calls.append(f'test("{path.relative_to(ROOT).parts[0]}", CrosshairMobGroupsTest::select{index}, CrosshairMobGroupsTest::toggle{index});')
HARNESS += 'public static void main(String[] args) {\n' + '\n'.join(calls) + '\n}\n}\n'
OUT.mkdir(parents=True, exist_ok=True)
java = OUT / 'CrosshairMobGroupsTest.java'
java.write_text(HARNESS, encoding='utf-8')
matcher = ROOT / 'neoforge-1.21.1/src/main/java/soundcontrol/EntitySoundMatcher.java'
subprocess.run(['javac', '--release', '17', '-encoding', 'UTF-8', '-d', str(OUT), str(java), str(matcher)], check=True)
subprocess.run(['java', '-cp', str(OUT), 'CrosshairMobGroupsTest'], check=True)
