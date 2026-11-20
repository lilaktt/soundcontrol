package soundcontrol;

import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.Locale;

public final class AnchorWorldContext {
    private static WeakReference<Object> cachedServer = new WeakReference<>(null);
    private static String cachedLocalKey;
    private static String cachedRemoteAddress;
    private static String cachedRemoteKey;

    private AnchorWorldContext() {}

    public static String currentKey() {
        if (!Compat.inWorld()) {
            cachedServer.clear();
            cachedLocalKey = null;
            return null;
        }
        Object server = Compat.singleplayerServer();
        if (server != null) {
            if (cachedServer.get() != server) {
                cachedServer = new WeakReference<>(server);
                cachedLocalKey = localKey(Compat.singleplayerRoot(server));
            }
            return cachedLocalKey;
        }
        cachedServer.clear();
        cachedLocalKey = null;
        String address = Compat.remoteAddress();
        if (address == null) return null;
        if (!address.equals(cachedRemoteAddress)) {
            cachedRemoteAddress = address;
            cachedRemoteKey = serverKey(address);
        }
        return cachedRemoteKey;
    }

    static String localKey(Path root) {
        if (root == null) return null;
        Path normalized = root.toAbsolutePath().normalize();
        try { normalized = normalized.toRealPath(); } catch (java.io.IOException ignored) {}
        return "singleplayer:" + normalized.toUri().toASCIIString();
    }

    static String serverKey(String address) {
        if (address == null || address.isBlank()) return null;
        String normalized = address.trim().toLowerCase(Locale.ROOT);
        int colon = normalized.lastIndexOf(':');
        if (colon >= 0 && normalized.substring(colon + 1).equals("25565")
                && (normalized.indexOf(':') == colon || normalized.startsWith("["))) {
            normalized = normalized.substring(0, colon);
        }
        if (normalized.isEmpty()) return null;
        return "multiplayer:" + normalized;
    }
}
