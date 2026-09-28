package soundcontrol.render;

import soundcontrol.ModSoundCatalog;
import soundcontrol.SoundConfig;
import soundcontrol.SoundControl;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class SoundWorldRenderer {
    public static boolean enabled = false;
    private static final CopyOnWriteArrayList<SoundEvent3D> activeSounds = new CopyOnWriteArrayList<>();
    private static final long DISPLAY_DURATION_MS = 3000;
    private static final long FADE_DURATION_MS = 800;
    private static final long SELECTION_MEMORY_MS = 8000;
    private static final long TOGGLE_ANIMATION_MS = 700;



    public static class SoundEvent3D {
        public final SoundInstance sound;
        public final String soundId;
        public long createdAt;
        public long lastSeenAt;
        public long toggleAnimationAt;
        public boolean toggleMuted;

        public SoundEvent3D(SoundInstance sound, String soundId) {
            this.sound = sound;
            this.soundId = soundId;
            this.createdAt = System.currentTimeMillis();
            this.lastSeenAt = this.createdAt;
        }
    }

    public static void recordSound(SoundInstance sound, String soundId) {
        Iterator<SoundEvent3D> it = activeSounds.iterator();
        while (it.hasNext()) {
            SoundEvent3D existing = it.next();
            if (existing.sound == sound || (existing.soundId.equals(soundId)
                    && Math.abs(existing.sound.getX() - sound.getX()) < 3.0
                    && Math.abs(existing.sound.getY() - sound.getY()) < 3.0
                    && Math.abs(existing.sound.getZ() - sound.getZ()) < 3.0)) {
                activeSounds.remove(existing);
                break;
            }
        }

        activeSounds.add(new SoundEvent3D(sound, soundId));
        while (activeSounds.size() > 50) {
            activeSounds.remove(0);
        }
    }

    public static boolean toggleSoundUnderCrosshair(MinecraftClient client) {
        if (client.player == null || client.world == null) return false;
        if (!(client.crosshairTarget instanceof BlockHitResult blockHit
                && blockHit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK)
                && !(client.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult entityHit
                && entityHit.getEntity() instanceof net.minecraft.entity.mob.MobEntity)) {
            client.player.sendMessage(Text.translatable("message.soundcontrol.look_mute.none"), true);
            return false;
        }
        if (toggleLookedAtBlockSounds(client)) return true;

        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        String label;
        var target = ((net.minecraft.util.hit.EntityHitResult) client.crosshairTarget).getEntity();
        var entityId = net.minecraft.registry.Registries.ENTITY_TYPE.getId(target.getType());
        // Select the entire registered family, even when the mob has not made a sound.
        // Keep the namespace and trailing dot so other mods and similarly named mobs are excluded.
        String prefix = entityId.getNamespace() + ":entity." + entityId.getPath() + ".";
        for (var id : net.minecraft.registry.Registries.SOUND_EVENT.getIds()) {
            if (id.toString().startsWith(prefix)) ids.add(id.toString());
        }
        // Resource packs may also provide sound events outside the game registry.
        for (var id : client.getSoundManager().getKeys()) {
            if (id.toString().startsWith(prefix)) ids.add(id.toString());
        }
        label = target.getName().getString();
        if (ids.isEmpty()) {
            client.player.sendMessage(Text.translatable("message.soundcontrol.look_mute.none"), true);
            return false;
        }
        boolean muted = SoundConfig.toggleSoundsMuted(ids);
        long now = System.currentTimeMillis();
        for (SoundEvent3D event : activeSounds) {
            if (ids.contains(event.soundId)) {
                event.toggleAnimationAt = now;
                event.toggleMuted = muted;
                event.createdAt = now;
                event.lastSeenAt = now;
            }
        }
        if (muted) {
            for (String id : ids) stopSoundId(client, id);
        }
        client.player.sendMessage(Text.translatable(
            muted ? "message.soundcontrol.look_mute.muted" : "message.soundcontrol.look_mute.unmuted",
            label), true);
        return true;
    }

    private static boolean toggleLookedAtBlockSounds(MinecraftClient client) {
        if (!(client.crosshairTarget instanceof BlockHitResult hit)
                || hit.getType() != net.minecraft.util.hit.HitResult.Type.BLOCK) return false;

        BlockSoundGroup sounds = client.world.getBlockState(hit.getBlockPos()).getSoundGroup();
        Set<String> ids = new LinkedHashSet<>();
        ids.add(sounds.getBreakSound().id().toString());
        ids.add(sounds.getStepSound().id().toString());
        ids.add(sounds.getPlaceSound().id().toString());
        ids.add(sounds.getHitSound().id().toString());
        ids.add(sounds.getFallSound().id().toString());

        var blockId = Registries.BLOCK.getId(client.world.getBlockState(hit.getBlockPos()).getBlock());
        String blockPrefix = "block." + blockId.getPath() + ".";
        for (var soundId : client.getSoundManager().getKeys()) {
            if (soundId.getNamespace().equals(blockId.getNamespace())
                    && soundId.getPath().startsWith(blockPrefix)) {
                ids.add(soundId.toString());
            }
        }
        for (SoundEvent3D event : activeSounds) {
            if (Math.abs(event.sound.getX() - (hit.getBlockPos().getX() + 0.5)) <= 2.0
                    && Math.abs(event.sound.getY() - (hit.getBlockPos().getY() + 0.5)) <= 2.0
                    && Math.abs(event.sound.getZ() - (hit.getBlockPos().getZ() + 0.5)) <= 2.0) {
                ids.add(event.soundId);
            }
        }

        boolean muted = SoundConfig.toggleSoundsMuted(ids);
        long now = System.currentTimeMillis();
        for (SoundEvent3D event : activeSounds) {
            if (ids.contains(event.soundId)) {
                event.toggleAnimationAt = now;
                event.toggleMuted = muted;
                event.createdAt = now;
                event.lastSeenAt = now;
            }
        }
        if (muted) {
            for (String id : ids) stopSoundId(client, id);
        }
        Text blockName = client.world.getBlockState(hit.getBlockPos()).getBlock().getName();
        client.player.sendMessage(Text.translatable(
                muted ? "message.soundcontrol.look_mute.muted" : "message.soundcontrol.look_mute.unmuted",
                blockName), true);
        return true;
    }

    public static void stopModSounds(MinecraftClient client, String modId) {
        for (var id : client.getSoundManager().getKeys()) {
            if (ModSoundCatalog.ownsSound(modId, id.toString())) {
                client.getSoundManager().stopSounds(id, null);
            }
        }
        for (SoundEvent3D event : activeSounds) {
            if (ModSoundCatalog.ownsSound(modId, event.soundId)) client.getSoundManager().stop(event.sound);
        }
    }

    private static void stopSoundId(MinecraftClient client, String soundId) {
        var id = net.minecraft.util.Identifier.tryParse(soundId);
        if (id != null) client.getSoundManager().stopSounds(id, null);
        for (SoundEvent3D event : activeSounds) {
            if (event.soundId.equals(soundId)) client.getSoundManager().stop(event.sound);
        }
    }

    public static void render(DrawContext context) {
        if (!enabled || activeSounds.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.gameRenderer == null || client.gameRenderer.getCamera() == null) return;

        long now = System.currentTimeMillis();
        TextRenderer font = client.textRenderer;
        Camera camera = client.gameRenderer.getCamera();
        Vec3d camPos = camera.getCameraPos();

        Quaternionf rotation = camera.getRotation();
        Matrix4f viewMatrix = new Matrix4f().rotation(rotation.conjugate(new Quaternionf()));
        Matrix4f projMatrix = client.gameRenderer.getBasicProjectionMatrix((float) client.options.getFov().getValue().doubleValue());
        Matrix4f viewProjMatrix = new Matrix4f(projMatrix).mul(viewMatrix);

        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();

        java.util.List<SoundEvent3D> toRemove = new java.util.ArrayList<>();
        for (SoundEvent3D event : activeSounds) {
            boolean active = client.getSoundManager().isPlaying(event.sound);
            if (!active && event.sound instanceof TickableSoundInstance tickable) {
                active = !tickable.isDone();
            }
            if (active) {
                event.createdAt = now;
                event.lastSeenAt = now;
            }
            if (now - event.lastSeenAt > SELECTION_MEMORY_MS) {
                toRemove.add(event);
            }
        }
        if (!toRemove.isEmpty()) activeSounds.removeAll(toRemove);

        java.util.List<int[]> renderedRects = new java.util.ArrayList<>();
        for (SoundEvent3D event : activeSounds) {
            long age = now - event.createdAt;
            long remaining = DISPLAY_DURATION_MS - age;
            long toggleAge = now - event.toggleAnimationAt;
            boolean toggling = event.toggleAnimationAt > 0 && toggleAge < TOGGLE_ANIMATION_MS;
            boolean muted = SoundConfig.getVolumeModifier(event.soundId) <= 0.0f;
            if (muted && !toggling) continue;
            float alpha = remaining < FADE_DURATION_MS ? (float) remaining / FADE_DURATION_MS : 1.0f;
            if (toggling && event.toggleMuted) alpha = 1.0f - (float) toggleAge / TOGGLE_ANIMATION_MS;
            if (alpha <= 0.01f) continue;

            double dx = event.sound.getX() - camPos.x;
            double dy = event.sound.getY() - camPos.y + 0.5;
            double dz = event.sound.getZ() - camPos.z;

            float volume = 1.0f;
            try {
                volume = event.sound.getVolume();
            } catch (Exception e) {}

            double distSq = dx * dx + dy * dy + dz * dz;
            double maxDist = Math.max(16.0, 16.0 * volume) + 8.0;
            if (distSq > maxDist * maxDist) continue;

            Vector4f pos = new Vector4f((float) dx, (float) dy, (float) dz, 1.0f);
            viewProjMatrix.transform(pos);

            if (pos.w() > 0.0f) {
                float ndcX = pos.x() / pos.w();
                float ndcY = pos.y() / pos.w();

                if (ndcX >= -1.2f && ndcX <= 1.2f && ndcY >= -1.2f && ndcY <= 1.2f) {
                    int screenX = (int) ((ndcX + 1.0f) * 0.5f * screenWidth);
                    int screenY = (int) ((1.0f - ndcY) * 0.5f * screenHeight);

                    String displayName = event.soundId;
                    if (displayName.contains(":")) {
                        displayName = displayName.substring(displayName.indexOf(':') + 1);
                    }

                    int textWidth = font.getWidth(displayName);
                    int renderX = screenX - textWidth / 2;
                    int renderY = screenY;

                    boolean overlap;
                    int attempts = 0;
                    do {
                        overlap = false;
                        for (int[] rect : renderedRects) {
                            int rx = rect[0], ry = rect[1], rw = rect[2], rh = rect[3];
                            if (renderX < rx + rw
                                && renderX + textWidth > rx
                                && renderY < ry + rh
                                && renderY + font.fontHeight > ry) {
                                overlap = true;
                                renderY += font.fontHeight + 2;
                                break;
                            }
                        }
                        attempts++;
                    } while (overlap && attempts < 15);

                    renderedRects.add(new int[]{renderX, renderY, textWidth, font.fontHeight});

                    int alphaInt = Math.max(0, Math.min(255, (int) (alpha * 255)));
                    int color = (alphaInt << 24) | (toggling ? (event.toggleMuted ? 0xFF3333 : 0x55FF88) : 0x55FFFF);

                    context.drawText(font, displayName, renderX, renderY, color, true);
                    if (toggling) {
                        String mark = event.toggleMuted ? "×" : "+";
                        int markColor = (alphaInt << 24) | (event.toggleMuted ? 0xFF2020 : 0x55FF55);
                        int markX = screenX - font.getWidth(mark) / 2;
                        context.drawText(font, mark, markX, renderY + font.fontHeight + 2, markColor, true);
                    }
                }
            }
        }
    }
}

