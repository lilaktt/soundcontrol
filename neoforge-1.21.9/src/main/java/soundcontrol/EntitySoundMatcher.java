package soundcontrol;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pure ID matching; never treats an entire mod namespace as one mob's sounds. */
public final class EntitySoundMatcher {
    private EntitySoundMatcher() {}

    public static Set<String> collect(String entityId, Collection<String> available,
                                      Collection<String> entityTypes, Collection<String> actualSounds) {
        Set<String> result = new LinkedHashSet<>(actualSounds);
        String namespace = namespace(entityId);
        String name = normalize(path(entityId));
        String vanillaPrefix = namespace + ":entity." + path(entityId) + ".";
        boolean vanilla = namespace.equals("minecraft");
        List<String> longerNames = entityTypes.stream()
                .filter(id -> namespace(id).equals(namespace))
                .map(id -> normalize(path(id)))
                .filter(other -> other.length() > name.length())
                .toList();
        // Actual getters/observations may identify a family named differently from the entity.
        Set<String> actualFamilies = new LinkedHashSet<>();
        for (String id : actualSounds) {
            String family = family(id);
            if (family != null && namespace(id).equals(namespace) && !vanilla) {
                actualFamilies.add(family);
            }
        }
        for (String id : available) {
            if (!namespace(id).equals(namespace)) continue;
            if (vanilla) {
                if (id.startsWith(vanillaPrefix)) result.add(id);
                continue; // Preserve vanilla's strict entity-name boundary.
            }
            String fullName = normalize(path(id));
            String soundName = normalize(stripKind(path(id)));
            boolean differentMob = longerNames.stream().anyMatch(other ->
                    startsWithName(soundName, other) || startsWithName(fullName, other));
            if (differentMob) continue;
            if (startsWithName(soundName, name) || startsWithName(fullName, name)
                    || actualFamilies.stream().anyMatch(id::startsWith)) {
                result.add(id);
            }
        }
        return result;
    }

    private static boolean startsWithName(String sound, String name) {
        return !name.isEmpty() && (sound.equals(name) || sound.startsWith(name + "_"));
    }

    private static String stripKind(String path) {
        for (String prefix : List.of("entity.", "entity/", "entity_", "mob.", "mob/", "mob_",
                                    "mobs.", "mobs/", "entities.", "entities/")) {
            if (path.startsWith(prefix)) return path.substring(prefix.length());
        }
        return path;
    }

    private static String normalize(String path) {
        return path.replace('.', '_').replace('/', '_').replace('-', '_');
    }

    /** Expand only explicit action families, not an arbitrary shared prefix such as "boss_". */
    private static String family(String id) {
        int colon = id.indexOf(':');
        int split = Math.max(id.lastIndexOf('.'), Math.max(id.lastIndexOf('/'), id.lastIndexOf('_')));
        if (colon < 0 || split <= colon + 1) return null;
        String action = id.substring(split + 1);
        if (!Set.of("ambient", "idle", "hurt", "death", "step", "attack", "roar", "growl")
                .contains(action)) return null;
        String stem = id.substring(colon + 1, split);
        // These are generic sound kinds, not an identified mob family.
        if (Set.of("entity", "mob", "mobs", "entities", "generic", "entity.generic").contains(stem)) return null;
        return id.substring(0, split + 1);
    }

    static String namespace(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? "minecraft" : id.substring(0, colon);
    }

    static String path(String id) {
        int colon = id.indexOf(':');
        return id.substring(colon + 1);
    }
}
