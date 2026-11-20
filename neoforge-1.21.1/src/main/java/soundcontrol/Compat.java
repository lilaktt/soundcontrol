package soundcontrol;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Matrix4f;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class Compat {
    private Compat() {}

    public record Frame(double camX, double camY, double camZ, Matrix4f viewProj) {}
    public record Target(String id, String label, List<String> soundIds, double x, double y, double z, boolean block) {}

    private static Minecraft client() { return Minecraft.getInstance(); }

    public static List<String> clientSoundIds() {
        List<String> ids = new ArrayList<>();
        for (ResourceLocation id : client().getSoundManager().getAvailableSounds()) ids.add(id.toString());
        return ids;
    }

    public static List<String> registrySoundIds() {
        List<String> ids = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.SOUND_EVENT.keySet()) ids.add(id.toString());
        return ids;
    }

    public static List<String> entityTypeIds() {
        List<String> ids = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.ENTITY_TYPE.keySet()) ids.add(id.toString());
        return ids;
    }

    public static String modName(String namespace) { return Platform.modName(namespace); }

    public static Object playPreview(String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        if (parsed == null) return null;
        SoundInstance sound = SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(parsed), 1.0F, 1.0F);
        client().getSoundManager().play(sound);
        return sound;
    }

    public static boolean isPlaying(Object handle) {
        if (!(handle instanceof SoundInstance sound)) return false;
        if (client().getSoundManager().isActive(sound)) return true;
        return sound instanceof TickableSoundInstance tickable && !tickable.isStopped();
    }

    public static void stop(Object handle) {
        if (handle instanceof SoundInstance sound) client().getSoundManager().stop(sound);
    }

    public static void stopSound(String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        if (parsed != null) client().getSoundManager().stop(parsed, null);
    }

    public static double[] soundPos(Object handle) {
        if (handle instanceof SoundInstance sound) return new double[]{sound.getX(), sound.getY(), sound.getZ()};
        return new double[3];
    }

    public static double soundVolume(Object handle) {
        try {
            return handle instanceof SoundInstance sound ? sound.getVolume() : 1.0;
        } catch (RuntimeException notResolvedYet) {
            return 1.0;
        }
    }

    public static boolean isRelative(Object handle) {
        return handle instanceof SoundInstance sound && sound.isRelative();
    }

    public static String soundId(Object handle) {
        if (!(handle instanceof SoundInstance sound)) return null;
        ResourceLocation id = sound.getLocation();
        return id == null ? null : id.toString();
    }

    public static void setScreen(Screen screen) { client().setScreen(screen); }
    public static boolean noScreen() { return client().screen == null; }

    public static void overlay(String key, Object... args) {
        if (client().player != null) client().gui.setOverlayMessage(Component.translatable(key, args), false);
    }

    public static void copy(String text) { client().keyboardHandler.setClipboard(text); }

    public static boolean inWorld() { return client().level != null && client().player != null; }

    public static String dimensionKey() {
        return client().level == null ? "" : soundcontrol.anchor.SoundAnchor.normalizeDimension(client().level.dimension().toString());
    }

    public static double[] playerPos() {
        Player player = client().player;
        return player == null ? null : new double[]{player.getX(), player.getY(), player.getZ()};
    }

    public static Object singleplayerServer() { return client().getSingleplayerServer(); }

    public static Path singleplayerRoot(Object server) {
        return server instanceof net.minecraft.server.MinecraftServer s ? s.getWorldPath(LevelResource.ROOT) : null;
    }

    public static String remoteAddress() {
        var entry = client().getCurrentServer();
        return entry == null ? null : entry.ip;
    }

    private static String soundEventId(SoundEvent event) {
        if (event == null) return null;
        ResourceLocation id = event.getLocation();
        return id == null ? null : id.toString();
    }

    public static Target crosshairTarget() {
        Minecraft mc = client();
        if (mc.level == null || mc.player == null) return null;
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            var pos = blockHit.getBlockPos();
            BlockState state = mc.level.getBlockState(pos);
            SoundType group = state.getSoundType(mc.level, pos, mc.player);
            Set<String> ids = new LinkedHashSet<>();
            for (SoundEvent event : new SoundEvent[]{group.getBreakSound(), group.getStepSound(), group.getPlaceSound(), group.getHitSound(), group.getFallSound()}) {
                String id = soundEventId(event);
                if (id != null) ids.add(id);
            }
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            String prefix = blockId.getNamespace() + ":block." + blockId.getPath() + ".";
            for (String id : clientSoundIds()) if (id.startsWith(prefix)) ids.add(id);
            return new Target(blockId.toString(), state.getBlock().getName().getString(), new ArrayList<>(ids),
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, true);
        }
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity living
                && !(living instanceof Player)) {
            Entity entity = entityHit.getEntity();
            String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            return new Target(entityId, entity.getName().getString(), new ArrayList<>(EntitySoundResolver.resolve(living)),
                    entity.getX(), entity.getY(), entity.getZ(), false);
        }
        return null;
    }

    public static Frame frame() {
        Minecraft mc = client();
        if (mc.gameRenderer == null || mc.gameRenderer.getMainCamera() == null) return null;
        var camera = mc.gameRenderer.getMainCamera();
        var pos = camera.getPosition();
        Matrix4f view = new Matrix4f().rotation(camera.rotation().conjugate(new org.joml.Quaternionf()));
        Matrix4f projection = mc.gameRenderer.getProjectionMatrix(client().options.fov().get());
        return new Frame(pos.x, pos.y, pos.z, new Matrix4f(projection).mul(view));
    }
}
