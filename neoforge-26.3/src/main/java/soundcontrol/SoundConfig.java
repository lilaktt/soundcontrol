package soundcontrol;

import soundcontrol.api.SoundControlAPI;
import com.google.gson.*;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.function.Function;

public class SoundConfig {
    static final File SC_DIR = new File(FMLPaths.CONFIGDIR.get().toFile(), "soundcontrol");
    public static final File CONFIGS_DIR = new File(SC_DIR, "configs");
    static final File SETTINGS_FILE = new File(SC_DIR, "settings.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger("soundcontrol");

    public static class AppSettings {
        public String activeProfile = "default";
        public int radarX = 10;
        public int radarY = -1;
        public List<SoundAnchor> anchors = new ArrayList<>(); // Unassigned legacy anchors; explicit import only.
        public Map<String, List<SoundAnchor>> anchorsByWorld = new LinkedHashMap<>();
    }
    private static AppSettings SETTINGS = new AppSettings();

    public static class SoundSettings {
        public float volume = 1.0f;
        public boolean muted = false;
        public boolean favorite = false;
        public boolean overrideParent = false;
    }

    public static class SoundProfile {
        public String name = "default";
        public Map<String, SoundSettings> sounds = new LinkedHashMap<>();
        public transient File file;
    }

    private static final List<SoundProfile> PROFILES = new ArrayList<>();
    private static SoundProfile ACTIVE_PROFILE = null;
    private static long lastSwitchTime = 0;
    private static final long SWITCH_DELAY_MS = 500;

    private static Map<String, SoundSettings> EDIT_TARGET = null;

    private static Map<String, SoundSettings> sounds() {
        if (EDIT_TARGET != null) return EDIT_TARGET;
        return ACTIVE_PROFILE != null ? ACTIVE_PROFILE.sounds : Collections.emptyMap();
    }

    public static SoundSettings getSound(String id)   { return sounds().get(id); }
    public static void putSound(String id, SoundSettings s) { sounds().put(id, s); }
    public static void removeSound(String id)          { sounds().remove(id); }
    public static boolean containsSound(String id)     { return sounds().containsKey(id); }
    public static Map<String, SoundSettings> getSounds() { return Collections.unmodifiableMap(sounds()); }
    public static SoundSettings computeSound(String id, Function<String, SoundSettings> fn) {
        return sounds().computeIfAbsent(id, fn);
    }

    public static boolean toggleSoundsMuted(Collection<String> ids) {
        Set<String> unique = new LinkedHashSet<>(ids);
        if (unique.isEmpty()) return false;
        boolean mute = !unique.stream().allMatch(id -> getVolumeModifier(id) <= 0f);
        for (String id : unique) {
            SoundSettings setting = sounds().computeIfAbsent(id, key -> new SoundSettings());
            setting.muted = mute;
            setting.overrideParent = true;
            if (!mute && setting.volume <= 0f) setting.volume = 1f;
        }
        save();
        return mute;
    }

    public static void setEditTarget(Map<String, SoundSettings> target) { EDIT_TARGET = target; }
    public static void clearEditTarget() { EDIT_TARGET = null; }

    public static int getRadarX() { return SETTINGS.radarX; }
    public static int getRadarY() { return SETTINGS.radarY; }
    public static void setRadarPos(int x, int y) { SETTINGS.radarX = x; SETTINGS.radarY = y; saveSettings(); }

    public static List<SoundProfile> getProfiles() { return Collections.unmodifiableList(PROFILES); }
    public static SoundProfile getActiveProfile()  { return ACTIVE_PROFILE; }

    public static long getSwitchCooldownRemaining() {
        long elapsed = System.currentTimeMillis() - lastSwitchTime;
        return Math.max(0, SWITCH_DELAY_MS - elapsed);
    }

    public static boolean switchProfile(String name) {
        if (System.currentTimeMillis() - lastSwitchTime < SWITCH_DELAY_MS) return false;
        lastSwitchTime = System.currentTimeMillis();
        for (SoundProfile p : PROFILES) {
            if (p.name.equals(name)) {
                ACTIVE_PROFILE = p;
                SETTINGS.activeProfile = name;
                saveSettings();
                return true;
            }
        }
        return false;
    }

    public static SoundProfile createProfile(String name) {
        SoundProfile p = new SoundProfile();
        p.name = name;
        p.file = new File(CONFIGS_DIR, sanitize(name) + ".json");
        PROFILES.add(p);
        saveProfile(p);
        return p;
    }

    public static void deleteProfile(SoundProfile profile) {
        if (profile.name.equals("default")) {
            profile.sounds.clear();
            saveProfile(profile);
            return;
        }
        if (profile.file != null && profile.file.exists()) profile.file.delete();
        PROFILES.remove(profile);
        if (ACTIVE_PROFILE == profile) switchProfile("default");
    }

    public static void refreshProfiles() {
        CONFIGS_DIR.mkdirs();

        PROFILES.removeIf(p -> p.file != null && !p.file.exists() && !p.name.equals("default"));

        File defFile = new File(CONFIGS_DIR, "default.json");
        boolean hasDefault = PROFILES.stream().anyMatch(p -> p.name.equals("default"));
        if (!hasDefault) {
            SoundProfile def = new SoundProfile();
            def.name = "default";
            def.file = defFile;
            if (!defFile.exists()) saveProfileToFile(def);
            PROFILES.add(0, def);
        }

        File[] files = CONFIGS_DIR.listFiles((d, n) -> n.endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                boolean alreadyLoaded = PROFILES.stream().anyMatch(p -> p.file != null && p.file.equals(f));
                if (!alreadyLoaded) {
                    try (FileReader r = new FileReader(f)) {
                        SoundProfile p = GSON.fromJson(r, SoundProfile.class);
                        if (p == null) p = new SoundProfile();
                        if (p.sounds == null) p.sounds = new LinkedHashMap<>();
                        if (p.name == null || p.name.isEmpty()) p.name = f.getName().replace(".json", "");
                        p.file = f;
                        PROFILES.add(p);
                    } catch (Exception e) {
                        LOGGER.warn("Failed to load profile: {}", f.getName(), e);
                    }
                }
            }
        }

        if (ACTIVE_PROFILE != null && !PROFILES.contains(ACTIVE_PROFILE)) {
            ACTIVE_PROFILE = PROFILES.isEmpty() ? null : PROFILES.get(0);
            if (ACTIVE_PROFILE != null) SETTINGS.activeProfile = ACTIVE_PROFILE.name;
        }
    }

    public static void load() {
        SC_DIR.mkdirs();
        CONFIGS_DIR.mkdirs();

        if (SETTINGS_FILE.exists()) {
            try (java.io.Reader r = java.nio.file.Files.newBufferedReader(SETTINGS_FILE.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                AppSettings s = GSON.fromJson(r, AppSettings.class);
                if (s != null) SETTINGS = s;
            } catch (Exception e) {
                LOGGER.warn("Failed to load soundcontrol settings", e);
            }
        }

        File defFile = new File(CONFIGS_DIR, "default.json");
        if (!defFile.exists()) {
            SoundProfile def = new SoundProfile();
            def.name = "default";
            def.file = defFile;
            saveProfileToFile(def);
        }

        PROFILES.clear();
        File[] files = CONFIGS_DIR.listFiles((d, n) -> n.endsWith(".json"));
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(f -> f.getName().equals("default.json") ? "" : f.getName()));
            for (File f : files) {
                try (FileReader r = new FileReader(f)) {
                    SoundProfile p = GSON.fromJson(r, SoundProfile.class);
                    if (p == null) p = new SoundProfile();
                    if (p.sounds == null) p.sounds = new LinkedHashMap<>();
                    if (p.name == null || p.name.isEmpty()) p.name = f.getName().replace(".json", "");
                    p.file = f;
                    PROFILES.add(p);
                } catch (Exception e) {
                    LOGGER.warn("Failed to load profile: {}", f.getName(), e);
                }
            }
        }

        ACTIVE_PROFILE = null;
        for (SoundProfile p : PROFILES) {
            if (p.name.equals(SETTINGS.activeProfile)) { ACTIVE_PROFILE = p; break; }
        }
        if (ACTIVE_PROFILE == null && !PROFILES.isEmpty()) {
            ACTIVE_PROFILE = PROFILES.get(0);
            SETTINGS.activeProfile = ACTIVE_PROFILE.name;
        }

        // Migrate old single-file config
        File oldFile = new File(FMLPaths.CONFIGDIR.get().toFile(), "soundcontrol.json");
        if (oldFile.exists()) migrateOldConfig(oldFile);
    }

    private static void migrateOldConfig(File oldFile) {
        try (FileReader r = new FileReader(oldFile)) {
            JsonObject obj = GSON.fromJson(r, JsonObject.class);
            if (obj != null && obj.has("sounds")) {
                SoundProfile def = getDefaultProfile();
                JsonObject sounds = obj.getAsJsonObject("sounds");
                for (Map.Entry<String, JsonElement> e : sounds.entrySet()) {
                    SoundSettings s = GSON.fromJson(e.getValue(), SoundSettings.class);
                    if (s != null) def.sounds.put(e.getKey(), s);
                }
                saveProfile(def);
                LOGGER.info("Migrated soundcontrol.json to default profile");
            }
        } catch (Exception e) {
            LOGGER.warn("Could not migrate old config", e);
        }
        oldFile.renameTo(new File(oldFile.getParent(), "soundcontrol.json.migrated"));
    }

    private static SoundProfile getDefaultProfile() {
        for (SoundProfile p : PROFILES) { if (p.name.equals("default")) return p; }
        return createProfile("default");
    }

    public static void save() {
        if (EDIT_TARGET != null) {
            for (SoundProfile p : PROFILES) {
                if (p.sounds == EDIT_TARGET) { saveProfileToFile(p); return; }
            }
        }
        if (ACTIVE_PROFILE != null) saveProfileToFile(ACTIVE_PROFILE);
    }

    public static void saveProfile(SoundProfile profile) {
        if (profile.file == null)
            profile.file = new File(CONFIGS_DIR, sanitize(profile.name) + ".json");
        saveProfileToFile(profile);
    }

    private static void saveProfileToFile(SoundProfile profile) {
        if (profile.file == null)
            profile.file = new File(CONFIGS_DIR, sanitize(profile.name) + ".json");
        try (FileWriter w = new FileWriter(profile.file)) {
            GSON.toJson(profile, w);
        } catch (IOException e) {
            LOGGER.error("Failed to save profile: {}", profile.name, e);
        }
    }

    public static void saveSettings() {
        SC_DIR.mkdirs();
        java.nio.file.Path destination = SETTINGS_FILE.toPath();
        java.nio.file.Path temporary = null;
        try {
            temporary = java.nio.file.Files.createTempFile(SC_DIR.toPath(), "settings-", ".tmp");
            try (java.io.Writer writer = java.nio.file.Files.newBufferedWriter(temporary, java.nio.charset.StandardCharsets.UTF_8)) {
                GSON.toJson(SETTINGS, writer);
            }
            try {
                java.nio.file.Files.move(temporary, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                java.nio.file.Files.move(temporary, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            LOGGER.error("Failed to save soundcontrol settings", exception);
        } finally {
            if (temporary != null) {
                try { java.nio.file.Files.deleteIfExists(temporary); } catch (IOException ignored) {}
            }
        }
    }

    public static void resetSettings() {
        if (ACTIVE_PROFILE != null) {
            ACTIVE_PROFILE.sounds.entrySet().removeIf(e -> {
                SoundSettings s = e.getValue();
                s.volume = 1.0f;
                s.muted = false;
                s.overrideParent = false;
                return !s.favorite;
            });
            saveProfileToFile(ACTIVE_PROFILE);
        }
    }

    public static String sanitize(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private static final Set<String> HOSTILE_MOBS = Set.of(
            "zombie","creeper","skeleton","spider","enderman","witch","slime","ghast",
            "zombified_piglin","piglin","piglin_brute","hoglin","zoglin","phantom",
            "silverfish","endermite","guardian","elder_guardian","shulker","vindicator",
            "evoker","pillager","ravager","vex","illusioner","warden","wither",
            "ender_dragon","stray","husk","drowned","magma_cube","blaze","wither_skeleton",
            "bogged","breeze"
    );

    public static String getSoundGroup(String id) {
        if (id.startsWith("minecraft:entity.")) {
            String[] p = id.split("\\."); if (p.length >= 2) return "minecraft:entity." + p[1];
        }
        if (id.startsWith("minecraft:block.")) {
            String[] p = id.split("\\."); if (p.length >= 2) return "minecraft:block." + p[1];
        }
        return id;
    }

    private static float vol(SoundSettings s) { return s.muted ? 0f : s.volume; }
    private static boolean isDefault(SoundSettings s) {
        return s == null || (!s.muted && Math.abs(s.volume - 1f) < 0.01f);
    }

    public static float getVolumeModifier(String id) {
        if (ACTIVE_PROFILE != null) {
            float v = lookupIn(ACTIVE_PROFILE.sounds, id);
            if (v >= 0) return v;
        }

        if (ACTIVE_PROFILE != null && !ACTIVE_PROFILE.name.equals("default")) {
            for (SoundProfile p : PROFILES) {
                if (p.name.equals("default")) { float v = lookupIn(p.sounds, id); if (v >= 0) return v; break; }
            }
        }

        float apiVol = SoundControlAPI.resolveApiVolume(id);
        if (apiVol >= 0) return apiVol;
        return 1.0f;
    }

















    /** Only the current world's list is visible to any anchor consumer. */
    public static List<SoundAnchor> getAnchors() {
        String worldKey = AnchorWorldContext.currentKey();
        if (worldKey == null) return Collections.emptyList();
        if (SETTINGS.anchorsByWorld == null) SETTINGS.anchorsByWorld = new LinkedHashMap<>();
        List<SoundAnchor> anchors = SETTINGS.anchorsByWorld.computeIfAbsent(worldKey, key -> new ArrayList<>());
        anchors.removeIf(Objects::isNull);
        return anchors;
    }

    public static int getLegacyAnchorCount() {
        if (SETTINGS.anchors == null) return 0;
        SETTINGS.anchors.removeIf(Objects::isNull);
        return SETTINGS.anchors.size();
    }

    /** Old settings did not record a world; migrate only by an explicit user action. */
    public static void importLegacyAnchors() {
        if (AnchorWorldContext.currentKey() == null || getLegacyAnchorCount() == 0) return;
        getAnchors().addAll(SETTINGS.anchors);
        SETTINGS.anchors.clear();
        saveSettings();
    }

    public static float getAnchorSettingVolume(Map<String, SoundSettings> overrides, String id) {
        if (id == null || overrides == null) return -1f;
        SoundSettings setting = overrides.get(id);
        if (setting == null) setting = overrides.get(getSoundGroup(id));
        if (setting == null && id.contains(".break")) setting = overrides.get("#global:break");
        if (setting == null && id.contains(".place")) setting = overrides.get("#global:place");
        if (setting == null && id.contains(".step")) setting = overrides.get("#global:step");
        if (setting == null && id.contains(".hit")) setting = overrides.get("#global:hit");
        if (setting == null && id.startsWith("minecraft:entity.")) {
            String[] parts = id.split("\\.");
            if (parts.length >= 3) {
                String kind = HOSTILE_MOBS.contains(parts[1]) ? "hostile" : "passive";
                if (id.contains(".hurt")) setting = overrides.get("#global:" + kind + "_hurt");
                if (id.contains(".ambient")) setting = overrides.get("#global:" + kind + "_ambient");
            }
        }
        if (setting == null) return -1f;
        if (setting.muted) return 0f;
        return Float.isFinite(setting.volume) ? Math.max(0f, Math.min(2f, setting.volume)) : 1f;
    }

    /** Overlaps use the quietest matching override; order never changes which zone mutes. */
    public static float getAnchorVolumeModifier(String id, String dimension, double x, double y, double z) {
        float result = -1f;
        for (SoundAnchor anchor : getAnchors()) {
            if (anchor.contains(dimension, x, y, z)) {
                float volume = anchor.getVolumeModifier(id);
                if (volume >= 0) result = result < 0 ? volume : Math.min(result, volume);
            }
        }
        return result;
    }

    private static float lookupIn(Map<String, SoundSettings> m, String id) {
        SoundSettings s = m.get(id);
        if (s != null && s.overrideParent) return vol(s);
        if (!isDefault(s)) return vol(s);
        s = m.get(getSoundGroup(id));
        if (!isDefault(s)) return vol(s);
        if (id.contains(".break") && m.containsKey("#global:break"))   return vol(m.get("#global:break"));
        if (id.contains(".place") && m.containsKey("#global:place"))   return vol(m.get("#global:place"));
        if (id.contains(".step")  && m.containsKey("#global:step"))    return vol(m.get("#global:step"));
        if (id.contains(".hit")   && m.containsKey("#global:hit"))     return vol(m.get("#global:hit"));
        if (id.startsWith("minecraft:entity.")) {
            String[] parts = id.split("\\.");
            if (parts.length >= 2) {
                boolean hostile = HOSTILE_MOBS.contains(parts[1]);
                if (id.contains(".hurt")) {
                    if (hostile  && m.containsKey("#global:hostile_hurt"))  return vol(m.get("#global:hostile_hurt"));
                    if (!hostile && m.containsKey("#global:passive_hurt"))  return vol(m.get("#global:passive_hurt"));
                }
                if (id.contains(".ambient")) {
                    if (hostile  && m.containsKey("#global:hostile_ambient")) return vol(m.get("#global:hostile_ambient"));
                    if (!hostile && m.containsKey("#global:passive_ambient")) return vol(m.get("#global:passive_ambient"));
                }
            }
        }
        return -1f;
    }
}
