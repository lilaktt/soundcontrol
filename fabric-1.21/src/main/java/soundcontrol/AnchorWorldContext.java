package soundcontrol;

import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.WorldSavePath;

/** Client-side identity shared by anchor UI, rendering and audio. Never keyed by dimension or display name. */
public final class AnchorWorldContext {
    private static WeakReference<Object> cachedServer = new WeakReference<>(null);
    private static String cachedLocalKey;

    private AnchorWorldContext() {}

    public static String currentKey() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            cachedServer.clear();
            cachedLocalKey = null;
            return null;
        }
        var server = client.getServer();
        if (server != null) {
            if (cachedServer.get() != server) {
                cachedServer = new WeakReference<>(server);
                cachedLocalKey = localKey(server.getSavePath(WorldSavePath.ROOT));
            }
            return cachedLocalKey;
        }
        cachedServer.clear();
        cachedLocalKey = null;
        var remote = client.getCurrentServerEntry();
        return remote == null ? null : serverKey(remote.address);
    }

    static String localKey(Path root) {
        if (root == null) return null;
        // Canonicalize existing paths (including symlinks) but don't write into users' world saves.
        Path normalized = root.toAbsolutePath().normalize();
        try { normalized = normalized.toRealPath(); } catch (java.io.IOException ignored) {}
        return "singleplayer:" + normalized.toUri().toASCIIString();
    }

    static String serverKey(String address) {
        if (address == null || address.isBlank()) return null;
        String normalized = address.trim().toLowerCase(Locale.ROOT);
        // Default-port spelling should not create a second list; preserve non-default ports.
        int colon = normalized.lastIndexOf(':');
        if (colon >= 0 && normalized.substring(colon + 1).equals("25565")
                && (normalized.indexOf(':') == colon || normalized.startsWith("["))) {
            normalized = normalized.substring(0, colon);
        }
        if (normalized.isEmpty()) return null;
        return "multiplayer:" + normalized;
    }
}
