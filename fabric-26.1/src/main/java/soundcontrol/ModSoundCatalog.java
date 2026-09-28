package soundcontrol;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ModSoundCatalog {
    public record ModInfo(String id, String name, Set<String> namespaces, Set<String> soundIds) {}

    private static final Map<String, ModInfo> MODS = new LinkedHashMap<>();
    private static boolean loaded;

    private ModSoundCatalog() {}

    public static synchronized List<ModInfo> getMods() {
        load();
        List<ModInfo> result = new ArrayList<>(MODS.values());
        result.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return result;
    }

    public static synchronized String getDisplayName(String modId) {
        load();
        ModInfo info = MODS.get(modId);
        return info == null ? modId : info.name();
    }

    public static synchronized boolean ownsSound(String modId, String soundId) {
        load();
        int separator = soundId.indexOf(':');
        if (separator <= 0) return false;
        String namespace = soundId.substring(0, separator);
        ModInfo info = MODS.get(modId);
        if (info == null) return modId.equals(namespace);
        return info.soundIds().contains(soundId)
                || (info.soundIds().stream().noneMatch(id -> id.startsWith(namespace + ":"))
                && info.namespaces().contains(namespace))
                || (info.namespaces().isEmpty() && modId.equals(namespace));
    }

    public static synchronized List<String> getOwners(String soundId) {
        load();
        int separator = soundId.indexOf(':');
        if (separator <= 0) return List.of();
        String namespace = soundId.substring(0, separator);
        List<String> owners = new ArrayList<>();
        for (ModInfo info : MODS.values()) {
            if (info.soundIds().contains(soundId)
                    || (info.soundIds().stream().noneMatch(id -> id.startsWith(namespace + ":"))
                    && info.namespaces().contains(namespace))
                    || (info.namespaces().isEmpty() && info.id().equals(namespace))) {
                owners.add(info.id());
            }
        }
        return owners;
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            String id = container.getMetadata().getId();
            String name = container.getMetadata().getName();
            Set<String> namespaces = new HashSet<>();
            Set<String> soundIds = new HashSet<>();
            findSounds(container, namespaces, soundIds);
            String searchable = (id + " " + name).toLowerCase(Locale.ROOT);
            boolean audioMod = searchable.contains("sound") || searchable.contains("audio");
            if (!isPlatformModule(id) && (!namespaces.isEmpty() || audioMod)) {
                MODS.put(id, new ModInfo(id, name, Collections.unmodifiableSet(namespaces),
                        Collections.unmodifiableSet(soundIds)));
            }
        }
    }

    private static void findSounds(ModContainer container, Set<String> namespaces, Set<String> soundIds) {
        for (Path root : container.getRootPaths()) {
            Path assets = root.resolve("assets");
            if (!Files.isDirectory(assets)) continue;
            try (var directories = Files.list(assets)) {
                directories.filter(Files::isDirectory).forEach(namespaceDir -> {
                    Path manifest = namespaceDir.resolve("sounds.json");
                    if (!Files.isRegularFile(manifest) && !Files.isDirectory(namespaceDir.resolve("sounds"))) return;
                    String namespace = namespaceDir.getFileName().toString();
                    namespaces.add(namespace);
                    if (!Files.isRegularFile(manifest)) return;
                    try (Reader reader = Files.newBufferedReader(manifest)) {
                        JsonObject sounds = JsonParser.parseReader(reader).getAsJsonObject();
                        sounds.keySet().forEach(key -> soundIds.add(namespace + ":" + key));
                    } catch (Exception ignored) {
                    }
                });
            } catch (Exception ignored) {
            }
        }
    }

    private static boolean isPlatformModule(String id) {
        return id.equals("minecraft") || id.equals("java") || id.equals("fabricloader")
                || id.equals("fabric-api") || id.startsWith("fabric-") || id.equals("soundcontrol");
    }
}
