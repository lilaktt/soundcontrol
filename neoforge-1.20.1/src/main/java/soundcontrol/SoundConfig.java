package soundcontrol;

import soundcontrol.anchor.SoundAnchor;
import soundcontrol.api.SoundControlAPI;
import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;

public class SoundConfig {
    static final Path SC_DIR = Platform.configDir().resolve("soundcontrol");
    public static final Path CONFIGS_DIR = SC_DIR.resolve("configs");
    static final Path SETTINGS_FILE = SC_DIR.resolve("settings.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger("soundcontrol");
    public static final String DEFAULT_PROFILE = "default";
    private static final long SAVE_DELAY_MS = 750;

    public static class AppSettings {
        public String activeProfile = DEFAULT_PROFILE;
        public int radarX = 10;
        public int radarY = -1;
        public List<SoundAnchor> anchors = new ArrayList<>();
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
        public String name = DEFAULT_PROFILE;
        public Map<String, SoundSettings> sounds = new LinkedHashMap<>();
        public transient Path file;
    }

    private static final List<SoundProfile> PROFILES = new ArrayList<>();
    private static SoundProfile ACTIVE_PROFILE = null;
    private static long lastSwitchTime = 0;
    private static final long SWITCH_DELAY_MS = 500;

    private static final Set<SoundProfile> DIRTY_PROFILES = new LinkedHashSet<>();
    private static boolean settingsDirty;
    private static long lastChange;
    private static boolean shutdownHookInstalled;

    private static Map<String, SoundSettings> sounds() {
        return ACTIVE_PROFILE != null ? ACTIVE_PROFILE.sounds : Collections.emptyMap();
    }

    public static SoundSettings getSound(String id)   { return sounds().get(id); }
    public static void putSound(String id, SoundSettings s) { sounds().put(id, s); }
    public static void removeSound(String id)          { sounds().remove(id); }
    public static boolean containsSound(String id)     { return sounds().get(id) != null; }
    public static Map<String, SoundSettings> getSounds() { return Collections.unmodifiableMap(sounds()); }
    public static SoundSettings computeSound(String id, Function<String, SoundSettings> fn) {
        SoundSettings existing = sounds().get(id);
        if (existing != null) return existing;
        SoundSettings created = fn.apply(id);
        sounds().put(id, created);
        return created;
    }

    public static boolean isEdited(SoundSettings s) {
        return s != null && (s.muted || s.overrideParent || Math.abs(s.volume - 1.0f) >= 0.01f);
    }

    public static void removeIfDefault(String id) {
        SoundSettings s = sounds().get(id);
        if (s != null && !isEdited(s) && !s.favorite) sounds().remove(id);
    }

    public static boolean toggleSoundsMuted(Collection<String> ids) {
        Set<String> unique = new LinkedHashSet<>(ids);
        if (unique.isEmpty()) return false;
        boolean mute = !unique.stream().allMatch(id -> getVolumeModifier(id) <= 0f);
        for (String id : unique) {
            if (mute) {
                SoundSettings setting = computeSound(id, key -> new SoundSettings());
                setting.muted = true;
                setting.overrideParent = true;
                continue;
            }
            SoundSettings setting = sounds().get(id);
            if (setting != null) {
                setting.muted = false;
                setting.overrideParent = false;
                if (setting.volume <= 0f) setting.volume = 1f;
                removeIfDefault(id);
            }
            if (getVolumeModifier(id) <= 0f) {
                SoundSettings override = computeSound(id, key -> new SoundSettings());
                override.muted = false;
                override.overrideParent = true;
                if (override.volume <= 0f) override.volume = 1f;
            }
        }
        save();
        return mute;
    }

    public static boolean isModMuted(String namespace) {
        SoundSettings setting = sounds().get("#mod:" + namespace);
        return setting != null && setting.muted;
    }

    public static boolean toggleModMuted(String namespace) {
        String key = "#mod:" + namespace;
        SoundSettings setting = computeSound(key, ignored -> new SoundSettings());
        setting.muted = !setting.muted;
        boolean muted = setting.muted;
        removeIfDefault(key);
        save();
        return muted;
    }

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
        SoundProfile target = findProfile(name);
        if (target == null) return false;
        lastSwitchTime = System.currentTimeMillis();
        activate(target);
        return true;
    }

    private static void activate(SoundProfile profile) {
        flush();
        ACTIVE_PROFILE = profile;
        SETTINGS.activeProfile = profile.name;
        saveSettingsNow();
    }

    public static SoundProfile findProfile(String name) {
        for (SoundProfile p : PROFILES) if (p.name.equals(name)) return p;
        return null;
    }

    public static boolean isNameTaken(String name, SoundProfile except) {
        String trimmed = name.trim();
        for (SoundProfile p : PROFILES) {
            if (p != except && p.name.equalsIgnoreCase(trimmed)) return true;
        }
        return false;
    }

    public static SoundProfile createProfile(String name) {
        SoundProfile p = new SoundProfile();
        p.name = name.trim();
        p.file = uniqueFile(p.name, null);
        PROFILES.add(p);
        saveProfileNow(p);
        activate(p);
        return p;
    }

    public static void renameProfile(SoundProfile profile, String newName) {
        String name = newName.trim();
        if (name.isEmpty() || DEFAULT_PROFILE.equals(profile.name) || isNameTaken(name, profile)) return;
        Path oldFile = profile.file;
        profile.name = name;
        profile.file = uniqueFile(name, profile);
        if (!saveProfileNow(profile)) {
            profile.file = oldFile;
            return;
        }
        if (oldFile != null && !oldFile.equals(profile.file)) {
            try { Files.deleteIfExists(oldFile); } catch (IOException e) { LOGGER.warn("Could not delete old profile file {}", oldFile, e); }
        }
        if (ACTIVE_PROFILE == profile) {
            SETTINGS.activeProfile = name;
            saveSettingsNow();
        }
    }

    public static void deleteProfile(SoundProfile profile) {
        if (DEFAULT_PROFILE.equals(profile.name)) {
            profile.sounds.clear();
            saveProfileNow(profile);
            return;
        }
        DIRTY_PROFILES.remove(profile);
        try {
            if (profile.file != null) Files.deleteIfExists(profile.file);
        } catch (IOException e) {
            LOGGER.warn("Could not delete profile file {}", profile.file, e);
        }
        PROFILES.remove(profile);
        if (ACTIVE_PROFILE == profile) activate(getDefaultProfile());
    }

    public static void refreshProfiles() {
        ensureDirectories();
        PROFILES.removeIf(p -> p.file != null && !Files.exists(p.file) && !DEFAULT_PROFILE.equals(p.name)
                && !DIRTY_PROFILES.contains(p));
        if (findProfile(DEFAULT_PROFILE) == null) PROFILES.add(0, loadOrCreateDefault());
        for (Path f : profileFiles()) {
            if (PROFILES.stream().noneMatch(p -> f.equals(p.file))) {
                SoundProfile p = readProfile(f);
                if (p != null && findProfile(p.name) == null) PROFILES.add(p);
            }
        }
        if (ACTIVE_PROFILE == null || !PROFILES.contains(ACTIVE_PROFILE)) {
            ACTIVE_PROFILE = getDefaultProfile();
            SETTINGS.activeProfile = ACTIVE_PROFILE.name;
            saveSettingsNow();
        }
    }

    public static void load() {
        ensureDirectories();
        installShutdownHook();
        if (Files.exists(SETTINGS_FILE)) {
            try (Reader r = Files.newBufferedReader(SETTINGS_FILE, StandardCharsets.UTF_8)) {
                AppSettings s = GSON.fromJson(r, AppSettings.class);
                if (s != null) SETTINGS = s;
            } catch (Exception e) {
                LOGGER.warn("Failed to load soundcontrol settings, keeping a copy and starting fresh", e);
                quarantine(SETTINGS_FILE);
            }
        }
        if (SETTINGS.activeProfile == null) SETTINGS.activeProfile = DEFAULT_PROFILE;
        if (SETTINGS.anchors == null) SETTINGS.anchors = new ArrayList<>();
        if (SETTINGS.anchorsByWorld == null) SETTINGS.anchorsByWorld = new LinkedHashMap<>();

        PROFILES.clear();
        PROFILES.add(loadOrCreateDefault());
        for (Path f : profileFiles()) {
            if (f.getFileName().toString().equals("default.json")) continue;
            SoundProfile p = readProfile(f);
            if (p == null) continue;
            if (findProfile(p.name) != null) {
                LOGGER.warn("Skipping profile file {}: the name '{}' is already used", f.getFileName(), p.name);
                continue;
            }
            PROFILES.add(p);
        }

        ACTIVE_PROFILE = findProfile(SETTINGS.activeProfile);
        if (ACTIVE_PROFILE == null) {
            ACTIVE_PROFILE = getDefaultProfile();
            SETTINGS.activeProfile = ACTIVE_PROFILE.name;
        }

        Path oldFile = Platform.configDir().resolve("soundcontrol.json");
        if (Files.exists(oldFile)) migrateOldConfig(oldFile);
    }

    private static void ensureDirectories() {
        try { Files.createDirectories(CONFIGS_DIR); } catch (IOException e) { LOGGER.error("Cannot create {}", CONFIGS_DIR, e); }
    }

    private static List<Path> profileFiles() {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(CONFIGS_DIR, "*.json")) {
            for (Path p : stream) if (Files.isRegularFile(p)) files.add(p);
        } catch (IOException e) {
            LOGGER.warn("Cannot list profiles in {}", CONFIGS_DIR, e);
        }
        files.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)));
        return files;
    }

    private static SoundProfile loadOrCreateDefault() {
        Path defFile = CONFIGS_DIR.resolve("default.json");
        SoundProfile def = Files.exists(defFile) ? readProfile(defFile) : null;
        if (def == null) {
            def = new SoundProfile();
            def.file = defFile;
            saveProfileNow(def);
        }
        def.name = DEFAULT_PROFILE;
        def.file = defFile;
        return def;
    }

    private static SoundProfile readProfile(Path f) {
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            SoundProfile p = GSON.fromJson(r, SoundProfile.class);
            if (p == null) p = new SoundProfile();
            if (p.sounds == null) p.sounds = new LinkedHashMap<>();
            p.sounds.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null);
            String fileName = f.getFileName().toString();
            if (p.name == null || p.name.isBlank()) p.name = fileName.substring(0, fileName.length() - 5);
            p.name = p.name.trim();
            if (DEFAULT_PROFILE.equalsIgnoreCase(p.name) && !fileName.equals("default.json")) p.name = p.name + " (2)";
            p.file = f;
            return p;
        } catch (Exception e) {
            LOGGER.warn("Failed to load profile {}, it was renamed so it is not overwritten", f.getFileName(), e);
            quarantine(f);
            return null;
        }
    }

    private static void quarantine(Path file) {
        try {
            Files.move(file, file.resolveSibling(file.getFileName() + ".broken-" + System.currentTimeMillis()),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {}
    }

    private static void migrateOldConfig(Path oldFile) {
        try (Reader r = Files.newBufferedReader(oldFile, StandardCharsets.UTF_8)) {
            JsonObject obj = GSON.fromJson(r, JsonObject.class);
            JsonObject sounds = null;
            if (obj != null && obj.has("sounds") && obj.get("sounds").isJsonObject()) {
                sounds = obj.getAsJsonObject("sounds");
            } else if (obj != null && obj.entrySet().stream().allMatch(e -> e.getValue().isJsonObject())) {
                sounds = obj;
            }
            if (obj != null && obj.has("radarX") && obj.has("radarY")) {
                SETTINGS.radarX = obj.get("radarX").getAsInt();
                SETTINGS.radarY = obj.get("radarY").getAsInt();
                saveSettingsNow();
            }
            if (sounds != null) {
                SoundProfile def = getDefaultProfile();
                for (Map.Entry<String, JsonElement> e : sounds.entrySet()) {
                    SoundSettings s = GSON.fromJson(e.getValue(), SoundSettings.class);
                    if (s != null) def.sounds.putIfAbsent(e.getKey(), s);
                }
                saveProfileNow(def);
                LOGGER.info("Migrated soundcontrol.json to the default profile");
            }
            r.close();
            Files.move(oldFile, oldFile.resolveSibling("soundcontrol.json.migrated"), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            LOGGER.warn("Could not migrate old config", e);
        }
    }

    private static SoundProfile getDefaultProfile() {
        SoundProfile def = findProfile(DEFAULT_PROFILE);
        if (def == null) {
            def = loadOrCreateDefault();
            PROFILES.add(0, def);
        }
        return def;
    }

    public static void save() {
        if (ACTIVE_PROFILE != null) saveProfile(ACTIVE_PROFILE);
    }

    public static void saveProfile(SoundProfile profile) {
        DIRTY_PROFILES.add(profile);
        lastChange = System.currentTimeMillis();
    }

    public static void saveSettings() {
        settingsDirty = true;
        lastChange = System.currentTimeMillis();
    }

    public static void tick() {
        if ((settingsDirty || !DIRTY_PROFILES.isEmpty()) && System.currentTimeMillis() - lastChange >= SAVE_DELAY_MS) flush();
    }

    public static synchronized void flush() {
        for (SoundProfile p : new ArrayList<>(DIRTY_PROFILES)) {
            if (PROFILES.contains(p)) saveProfileNow(p);
        }
        DIRTY_PROFILES.clear();
        if (settingsDirty) saveSettingsNow();
    }

    private static void installShutdownHook() {
        if (shutdownHookInstalled) return;
        shutdownHookInstalled = true;
        Runtime.getRuntime().addShutdownHook(new Thread(SoundConfig::flush, "soundcontrol-save"));
    }

    private static boolean saveProfileNow(SoundProfile profile) {
        if (profile.file == null) profile.file = uniqueFile(profile.name, profile);
        DIRTY_PROFILES.remove(profile);
        return writeAtomically(profile.file, profile);
    }

    private static void saveSettingsNow() {
        settingsDirty = false;
        writeAtomically(SETTINGS_FILE, SETTINGS);
    }

    private static boolean writeAtomically(Path destination, Object value) {
        Path temporary = null;
        try {
            Files.createDirectories(destination.getParent());
            temporary = Files.createTempFile(destination.getParent(), "soundcontrol-", ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(value, writer);
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            LOGGER.error("Failed to save {}", destination, exception);
            return false;
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) {}
            }
        }
    }

    public static void resetSettings() {
        if (ACTIVE_PROFILE == null) return;
        ACTIVE_PROFILE.sounds.entrySet().removeIf(e -> {
            SoundSettings s = e.getValue();
            s.volume = 1.0f;
            s.muted = false;
            s.overrideParent = false;
            return !s.favorite;
        });
        saveProfileNow(ACTIVE_PROFILE);
    }

    private static final Set<String> WINDOWS_RESERVED = Set.of("con", "prn", "aux", "nul",
            "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

    public static String sanitize(String name) {
        String clean = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        clean = clean.replaceAll("[. ]+$", "");
        if (clean.isEmpty()) clean = "profile";
        if (WINDOWS_RESERVED.contains(clean.toLowerCase(Locale.ROOT))) clean = "_" + clean;
        return clean.length() > 64 ? clean.substring(0, 64) : clean;
    }

    private static Path uniqueFile(String name, SoundProfile owner) {
        String base = sanitize(name);
        for (int i = 1; ; i++) {
            String candidate = i == 1 ? base : base + "-" + i;
            if (candidate.equalsIgnoreCase(DEFAULT_PROFILE) && !(owner != null && DEFAULT_PROFILE.equals(owner.name))) continue;
            Path file = CONFIGS_DIR.resolve(candidate + ".json");
            if (owner != null && file.equals(owner.file)) return file;
            boolean usedByProfile = PROFILES.stream().anyMatch(p -> p != owner && p.file != null
                    && p.file.getFileName().toString().equalsIgnoreCase(file.getFileName().toString()));
            boolean usedOnDisk = profileFiles().stream().anyMatch(f -> f.getFileName().toString()
                    .equalsIgnoreCase(file.getFileName().toString()) && (owner == null || !f.equals(owner.file)));
            if (!usedByProfile && !usedOnDisk) return file;
        }
    }

    private static final Set<String> HOSTILE_MOBS = Set.of(
            "zombie","creeper","skeleton","spider","enderman","witch","slime","ghast",
            "zombified_piglin","piglin","piglin_brute","hoglin","zoglin","phantom",
            "silverfish","endermite","guardian","elder_guardian","shulker","vindicator",
            "evoker","pillager","ravager","vex","illusioner","warden","wither",
            "ender_dragon","stray","husk","drowned","magma_cube","blaze","wither_skeleton",
            "bogged","breeze","cave_spider","creaking","parched"
    );

    public static String getSoundGroup(String id) {
        if (id.startsWith("minecraft:entity.") || id.startsWith("minecraft:block.")) {
            String[] p = id.split("\\.");
            if (p.length >= 2) return p[0] + "." + p[1];
        }
        return id;
    }

    private static float vol(SoundSettings s) {
        if (s.muted) return 0f;
        return Float.isFinite(s.volume) ? Math.max(0f, Math.min(2f, s.volume)) : 1f;
    }
    private static boolean isDefault(SoundSettings s) {
        return s == null || (!s.muted && Math.abs(s.volume - 1f) < 0.01f);
    }

    private static List<String> globalKeys(String id) {
        List<String> keys = new ArrayList<>(2);
        if (id.contains(".break")) keys.add("#global:break");
        if (id.contains(".place")) keys.add("#global:place");
        if (id.contains(".step"))  keys.add("#global:step");
        if (id.contains(".hit"))   keys.add("#global:hit");
        if (id.startsWith("minecraft:entity.")) {
            String[] parts = id.split("\\.");
            if (parts.length >= 3) {
                String kind = HOSTILE_MOBS.contains(parts[1]) ? "hostile" : "passive";
                if (id.contains(".hurt")) keys.add("#global:" + kind + "_hurt");
                if (id.contains(".ambient")) keys.add("#global:" + kind + "_ambient");
            }
        }
        return keys;
    }

    public static float getVolumeModifier(String id) {
        if (id == null) return 1.0f;
        if (ACTIVE_PROFILE != null) {
            float v = lookupIn(ACTIVE_PROFILE.sounds, id);
            if (v >= 0) return v;
            if (!DEFAULT_PROFILE.equals(ACTIVE_PROFILE.name)) {
                SoundProfile def = findProfile(DEFAULT_PROFILE);
                if (def != null) {
                    v = lookupIn(def.sounds, id);
                    if (v >= 0) return v;
                }
            }
        }
        float apiVol = SoundControlAPI.resolveApiVolume(id);
        if (apiVol < 0) apiVol = SoundControlAPI.resolveApiVolume(getSoundGroup(id));
        if (apiVol < 0) {
            for (String key : globalKeys(id)) {
                apiVol = SoundControlAPI.resolveApiVolume(key);
                if (apiVol >= 0) break;
            }
        }
        return apiVol >= 0 ? apiVol : 1.0f;
    }

    public static float getSoundModifier(String id, boolean relative, double x, double y, double z) {
        if (id == null) return 1.0f;
        float profile = getVolumeModifier(id);
        if (profile <= 0f || relative || !Compat.inWorld()) return profile;
        float anchor = getAnchorVolumeModifier(id, Compat.dimensionKey(), x, y, z);
        return anchor >= 0f ? anchor : profile;
    }

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

    public static void importLegacyAnchors() {
        if (AnchorWorldContext.currentKey() == null || getLegacyAnchorCount() == 0) return;
        getAnchors().addAll(SETTINGS.anchors);
        SETTINGS.anchors.clear();
        saveSettingsNow();
    }

    public static float getAnchorSettingVolume(Map<String, SoundSettings> overrides, String id) {
        if (id == null || overrides == null) return -1f;
        SoundSettings setting = overrides.get(id);
        if (setting == null) setting = overrides.get(getSoundGroup(id));
        if (setting == null) {
            for (String key : globalKeys(id)) {
                setting = overrides.get(key);
                if (setting != null) break;
            }
        }
        if (setting == null) return -1f;
        return vol(setting);
    }

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
        int namespaceEnd = id.indexOf(':');
        if (namespaceEnd > 0) {
            SoundSettings modSetting = m.get("#mod:" + id.substring(0, namespaceEnd));
            if (!isDefault(modSetting)) return vol(modSetting);
        }
        if (!isDefault(s)) return vol(s);
        s = m.get(getSoundGroup(id));
        if (!isDefault(s)) return vol(s);
        for (String key : globalKeys(id)) {
            SoundSettings global = m.get(key);
            if (global != null) return vol(global);
        }
        return -1f;
    }
}
