package soundcontrol;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class Platform {
    private Platform() {}

    public static Path configDir() { return FMLPaths.CONFIGDIR.get(); }

    public static String modName(String namespace) {
        return ModList.get().getModContainerById(namespace).map(c -> c.getModInfo().getDisplayName()).orElse(namespace);
    }
}
