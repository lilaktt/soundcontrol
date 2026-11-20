package soundcontrol;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.joml.Matrix4f;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class Compat {
    private Compat() {}

    public record Frame(double camX, double camY, double camZ, Matrix4f viewProj) {}
    public record Target(String id, String label, List<String> soundIds, double x, double y, double z, boolean block) {}

    private static MinecraftClient client() { return MinecraftClient.getInstance(); }

    public static List<String> clientSoundIds() {
        List<String> ids = new ArrayList<>();
        for (Identifier id : client().getSoundManager().getKeys()) ids.add(id.toString());
        return ids;
    }

    public static List<String> registrySoundIds() {
        List<String> ids = new ArrayList<>();
        for (Identifier id : Registries.SOUND_EVENT.getIds()) ids.add(id.toString());
        return ids;
    }

    public static List<String> entityTypeIds() {
        List<String> ids = new ArrayList<>();
        for (Identifier id : Registries.ENTITY_TYPE.getIds()) ids.add(id.toString());
        return ids;
    }

    public static String modName(String namespace) { return Platform.modName(namespace); }

    public static Object playPreview(String id) {
        Identifier parsed = Identifier.tryParse(id);
        if (parsed == null) return null;
        SoundInstance sound = PositionedSoundInstance.master(SoundEvent.of(parsed), 1.0F, 1.0F);
        client().getSoundManager().play(sound);
        return sound;
    }

    public static boolean isPlaying(Object handle) {
        if (!(handle instanceof SoundInstance sound)) return false;
        if (client().getSoundManager().isPlaying(sound)) return true;
        return sound instanceof TickableSoundInstance tickable && !tickable.isDone();
    }

    public static void stop(Object handle) {
        if (handle instanceof SoundInstance sound) client().getSoundManager().stop(sound);
    }

    public static void stopSound(String id) {
        Identifier parsed = Identifier.tryParse(id);
        if (parsed != null) client().getSoundManager().stopSounds(parsed, null);
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
        Identifier id = sound.getId();
        return id == null ? null : id.toString();
    }

    public static void setScreen(Screen screen) { client().setScreen(screen); }
    public static boolean noScreen() { return client().currentScreen == null; }

    public static void overlay(String key, Object... args) {
        if (client().player != null) client().player.sendMessage(Text.translatable(key, args), true);
    }

    public static void copy(String text) { client().keyboard.setClipboard(text); }

    public static boolean inWorld() { return client().world != null && client().player != null; }

    public static String dimensionKey() {
        return client().world == null ? "" : client().world.getRegistryKey().getValue().toString();
    }

    public static double[] playerPos() {
        PlayerEntity player = client().player;
        return player == null ? null : new double[]{player.getX(), player.getY(), player.getZ()};
    }

    public static Object singleplayerServer() { return client().getServer(); }

    public static Path singleplayerRoot(Object server) {
        return server instanceof net.minecraft.server.MinecraftServer s ? s.getSavePath(WorldSavePath.ROOT) : null;
    }

    public static String remoteAddress() {
        var entry = client().getCurrentServerEntry();
        return entry == null ? null : entry.address;
    }

    private static String soundEventId(SoundEvent event) {
        if (event == null) return null;
        Identifier id = event.id();
        return id == null ? null : id.toString();
    }

    public static Target crosshairTarget() {
        MinecraftClient mc = client();
        if (mc.world == null || mc.player == null) return null;
        HitResult hit = mc.crosshairTarget;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            BlockState state = mc.world.getBlockState(blockHit.getBlockPos());
            BlockSoundGroup group = state.getSoundGroup();
            Set<String> ids = new LinkedHashSet<>();
            for (SoundEvent event : new SoundEvent[]{group.getBreakSound(), group.getStepSound(), group.getPlaceSound(), group.getHitSound(), group.getFallSound()}) {
                String id = soundEventId(event);
                if (id != null) ids.add(id);
            }
            Identifier blockId = Registries.BLOCK.getId(state.getBlock());
            String prefix = blockId.getNamespace() + ":block." + blockId.getPath() + ".";
            for (String id : clientSoundIds()) if (id.startsWith(prefix)) ids.add(id);
            var pos = blockHit.getBlockPos();
            return new Target(blockId.toString(), state.getBlock().getName().getString(), new ArrayList<>(ids),
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, true);
        }
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity living
                && !(living instanceof PlayerEntity)) {
            Entity entity = entityHit.getEntity();
            String entityId = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
            return new Target(entityId, entity.getName().getString(), new ArrayList<>(EntitySoundResolver.resolve(living)),
                    entity.getX(), entity.getY(), entity.getZ(), false);
        }
        return null;
    }

    public static Frame frame() {
        MinecraftClient mc = client();
        if (mc.gameRenderer == null || mc.gameRenderer.getCamera() == null) return null;
        var camera = mc.gameRenderer.getCamera();
        var pos = camera.getPos();
        Matrix4f view = new Matrix4f().rotation(camera.getRotation().conjugate(new org.joml.Quaternionf()));
        Matrix4f projection = mc.gameRenderer.getBasicProjectionMatrix((float) mc.options.getFov().getValue().doubleValue());
        return new Frame(pos.x, pos.y, pos.z, new Matrix4f(projection).mul(view));
    }
}
