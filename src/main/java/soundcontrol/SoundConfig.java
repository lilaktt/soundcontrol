package soundcontrol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SoundConfig {
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "soundcontrol.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    public static class ConfigData {
        public int radarX = 10;
        public int radarY = -1;
        public Map<String, SoundSettings> sounds = new HashMap<>();
    }

    private static ConfigData DATA = new ConfigData();
    public static Map<String, SoundSettings> SOUNDS = DATA.sounds;

    public static int getRadarX() { return DATA.radarX; }
    public static int getRadarY() { return DATA.radarY; }
    public static void setRadarPos(int x, int y) {
        DATA.radarX = x;
        DATA.radarY = y;
    }

    private static final Set<String> HOSTILE_MOBS = Set.of(
            "zombie", "creeper", "skeleton", "spider", "enderman", "witch", "slime", "ghast",
            "zombified_piglin", "piglin", "piglin_brute", "hoglin", "zoglin", "phantom",
            "silverfish", "endermite", "guardian", "elder_guardian", "shulker", "vindicator",
            "evoker", "pillager", "ravager", "vex", "illusioner", "warden", "wither",
            "ender_dragon", "stray", "husk", "drowned", "magma_cube", "blaze", "wither_skeleton",
            "bogged", "breeze"
    );

    public static class SoundSettings {
        public float volume = 1.0f;
        public boolean muted = false;
        public boolean favorite = false;
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                DATA = GSON.fromJson(reader, ConfigData.class);
                if (DATA == null) DATA = new ConfigData();
                if (DATA.sounds == null) DATA.sounds = new HashMap<>();
                SOUNDS = DATA.sounds;
            } catch (Exception e) {
                // If old format (just map), try to load as map
                try (FileReader reader = new FileReader(CONFIG_FILE)) {
                    Type type = new TypeToken<Map<String, SoundSettings>>(){}.getType();
                    Map<String, SoundSettings> oldSounds = GSON.fromJson(reader, type);
                    if (oldSounds != null) {
                        DATA = new ConfigData();
                        DATA.sounds = oldSounds;
                        SOUNDS = DATA.sounds;
                    }
                } catch (Exception e2) {
                    e2.printStackTrace();
                }
            }
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(DATA, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void resetSettings() {
        SOUNDS.entrySet().removeIf(entry -> {
            SoundSettings s = entry.getValue();
            s.volume = 1.0f;
            s.muted = false;
            return !s.favorite;
        });
        save();
    }

    public static String getSoundGroup(String soundId) {
        if (soundId.startsWith("minecraft:entity.")) {
            String[] parts = soundId.split("\\.");
            if (parts.length >= 2) {
                return "minecraft:entity." + parts[1];
            }
        }
        if (soundId.startsWith("minecraft:block.")) {
            String[] parts = soundId.split("\\.");
            if (parts.length >= 2) {
                return "minecraft:block." + parts[1];
            }
        }
        return soundId;
    }

    private static float getSettingsVolume(SoundSettings s) {
        return s.muted ? 0.0f : s.volume;
    }

    public static float getVolumeModifier(String id) {
        if (SOUNDS.containsKey(id)) {
            return getSettingsVolume(SOUNDS.get(id));
        }

        if (id.contains(".break") && SOUNDS.containsKey("#global:break")) {
            return getSettingsVolume(SOUNDS.get("#global:break"));
        }
        if (id.contains(".place") && SOUNDS.containsKey("#global:place")) {
            return getSettingsVolume(SOUNDS.get("#global:place"));
        }
        if (id.contains(".step") && SOUNDS.containsKey("#global:step")) {
            return getSettingsVolume(SOUNDS.get("#global:step"));
        }
        if (id.contains(".hit") && SOUNDS.containsKey("#global:hit")) {
            return getSettingsVolume(SOUNDS.get("#global:hit"));
        }

        if (id.startsWith("minecraft:entity.")) {
            String[] parts = id.split("\\.");
            if (parts.length >= 2) {
                String mobName = parts[1];
                boolean isHostile = HOSTILE_MOBS.contains(mobName);

                if (id.contains(".hurt")) {
                    if (isHostile && SOUNDS.containsKey("#global:hostile_hurt")) {
                        return getSettingsVolume(SOUNDS.get("#global:hostile_hurt"));
                    } else if (!isHostile && SOUNDS.containsKey("#global:passive_hurt")) {
                        return getSettingsVolume(SOUNDS.get("#global:passive_hurt"));
                    }
                }

                if (id.contains(".ambient")) {
                    if (isHostile && SOUNDS.containsKey("#global:hostile_ambient")) {
                        return getSettingsVolume(SOUNDS.get("#global:hostile_ambient"));
                    } else if (!isHostile && SOUNDS.containsKey("#global:passive_ambient")) {
                        return getSettingsVolume(SOUNDS.get("#global:passive_ambient"));
                    }
                }
            }
        }

        String group = getSoundGroup(id);
        if (SOUNDS.containsKey(group)) {
            return getSettingsVolume(SOUNDS.get(group));
        }

        return 1.0f;
    }
}