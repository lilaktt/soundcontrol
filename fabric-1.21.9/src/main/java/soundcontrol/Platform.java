package soundcontrol;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public final class Platform {
    private Platform() {}

    public static Path configDir() { return FabricLoader.getInstance().getConfigDir(); }

    public static String modName(String namespace) {
        return FabricLoader.getInstance().getModContainer(namespace).map(c -> c.getMetadata().getName()).orElse(namespace);
    }
}
