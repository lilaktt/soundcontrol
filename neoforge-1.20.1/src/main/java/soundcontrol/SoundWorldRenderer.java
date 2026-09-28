package soundcontrol;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.world.phys.Vec3;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

public class SoundWorldRenderer {
    public static boolean enabled = false;
    private static final CopyOnWriteArrayList<SoundEvent3D> activeSounds = new CopyOnWriteArrayList<>();
    private static final long DISPLAY_DURATION_MS = 3000;
    private static final long FADE_DURATION_MS = 800;

    public static class SoundEvent3D {
        public final SoundInstance sound;
        public final String soundId;
        public final double x;
        public final double y;
        public final double z;
        public long createdAt;
        public long toggleAnimationAt;
        public boolean toggleMuted;

        public SoundEvent3D(SoundInstance sound, String soundId) {
            this.sound = sound;
            this.soundId = soundId;
            this.x = sound.getX();
            this.y = sound.getY();
            this.z = sound.getZ();
            this.createdAt = System.currentTimeMillis();
        }
    }

    public static void recordSound(SoundInstance sound, String soundId) {
        EntitySoundResolver.record(sound, soundId);


        Iterator<SoundEvent3D> it = activeSounds.iterator();
        while (it.hasNext()) {
            SoundEvent3D existing = it.next();
            if (existing.sound == sound || (existing.soundId.equals(soundId)
                    && Math.abs(existing.x - sound.getX()) < 3.0
                    && Math.abs(existing.y - sound.getY()) < 3.0
                    && Math.abs(existing.z - sound.getZ()) < 3.0)) {
                activeSounds.remove(existing);
                break;
            }
        }

        activeSounds.add(new SoundEvent3D(sound, soundId));
        while (activeSounds.size() > 50) {
            activeSounds.remove(0);
        }
    }

    public static boolean toggleSoundUnderCrosshair(Minecraft client) {
        if (client.player == null || client.level == null) return false;
        if (!(client.hitResult instanceof net.minecraft.world.phys.BlockHitResult blockHit
                && blockHit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK)
                && !(client.hitResult instanceof net.minecraft.world.phys.EntityHitResult entityHit
                && entityHit.getEntity() instanceof net.minecraft.world.entity.LivingEntity
                && !(entityHit.getEntity() instanceof net.minecraft.world.entity.player.Player))) {
            client.gui.setOverlayMessage(net.minecraft.network.chat.Component.translatable("message.soundcontrol.look_mute.none"), false);
            return false;
        }
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        String label = "";
        long now = System.currentTimeMillis();
        if (client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
                && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            var state = client.level.getBlockState(hit.getBlockPos());
            var sounds = state.getSoundType(client.level, hit.getBlockPos(), client.player);
            ids.add(sounds.getBreakSound().getLocation().toString());
            ids.add(sounds.getStepSound().getLocation().toString());
            ids.add(sounds.getPlaceSound().getLocation().toString());
            ids.add(sounds.getHitSound().getLocation().toString());
            ids.add(sounds.getFallSound().getLocation().toString());
            var blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
            String prefix = blockId.getNamespace() + ":block." + blockId.getPath() + ".";
            for (var id : client.getSoundManager().getAvailableSounds()) {
                if (id.toString().startsWith(prefix)) ids.add(id.toString());
            }
            for (SoundEvent3D event : activeSounds) {
                if (now - event.createdAt <= 8000
                        && Math.abs(event.sound.getX() - hit.getBlockPos().getX() - 0.5) <= 2
                        && Math.abs(event.sound.getY() - hit.getBlockPos().getY() - 0.5) <= 2
                        && Math.abs(event.sound.getZ() - hit.getBlockPos().getZ() - 0.5) <= 2) ids.add(event.soundId);
            }
            label = state.getBlock().getName().getString();
        } else {
            var target = ((net.minecraft.world.phys.EntityHitResult) client.hitResult).getEntity();
            ids.addAll(EntitySoundResolver.resolve(client, target));
            label = target.getName().getString();
        }
        if (ids.isEmpty()) {
            client.gui.setOverlayMessage(net.minecraft.network.chat.Component.translatable("message.soundcontrol.look_mute.none"), false);
            return false;
        }
        boolean muted = SoundConfig.toggleSoundsMuted(ids);
        for (SoundEvent3D event : activeSounds) {
            if (ids.contains(event.soundId)) {
                event.toggleAnimationAt = now;
                event.toggleMuted = muted;
                event.createdAt = now;
            }
        }
        if (muted) {
            for (String id : ids) {
                var parsed = net.minecraft.resources.ResourceLocation.tryParse(id);
                if (parsed != null) client.getSoundManager().stop(parsed, null);
            }
        }
        client.gui.setOverlayMessage(net.minecraft.network.chat.Component.translatable(
                muted ? "message.soundcontrol.look_mute.muted" : "message.soundcontrol.look_mute.unmuted", label), false);
        return true;
    }

    public static void render(GuiGraphics context) {
        if (!enabled || activeSounds.isEmpty()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.gameRenderer == null || client.gameRenderer.getMainCamera() == null) return;

        long now = System.currentTimeMillis();
        Font font = client.font;
        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();

        int screenWidth = context.guiWidth();
        int screenHeight = context.guiHeight();
        double yaw = Math.toRadians(camera.getYRot());
        double pitch = Math.toRadians(camera.getXRot());
        double sinYaw = Math.sin(yaw);
        double cosYaw = Math.cos(yaw);
        double sinPitch = Math.sin(pitch);
        double cosPitch = Math.cos(pitch);
        double tanHalfVerticalFov = Math.tan(Math.toRadians(client.options.fov().get()) * 0.5);
        double tanHalfHorizontalFov = tanHalfVerticalFov * ((double) screenWidth / screenHeight);

        java.util.List<int[]> renderedRects = new java.util.ArrayList<>();

        for (SoundEvent3D event : activeSounds) {
            boolean active = client.getSoundManager().isActive(event.sound);
            if (!active && event.sound instanceof TickableSoundInstance tickable) {
                active = !tickable.isStopped();
            }

            boolean shortSound = isShortSound(event.soundId);
            if (shortSound) active = false;
            if (active) event.createdAt = now;

            long duration = shortSound ? 900L : DISPLAY_DURATION_MS;
            if (now - event.createdAt > 8000) {
                activeSounds.remove(event);
                continue;
            }

            long age = now - event.createdAt;
            long fadeDuration = shortSound ? 300L : FADE_DURATION_MS;
            long remaining = duration - age;
            long toggleAge = now - event.toggleAnimationAt;
            boolean toggling = event.toggleAnimationAt > 0 && toggleAge < 700;
            if (SoundConfig.getVolumeModifier(event.soundId) <= 0 && !toggling) continue;
            float alpha = toggling ? 1f - (float) toggleAge / 700
                    : remaining < fadeDuration ? (float) remaining / fadeDuration : 1f;
            if (alpha <= 0.01f) continue;

            boolean moving = event.sound instanceof TickableSoundInstance;
            double soundX = moving ? event.sound.getX() : event.x;
            double soundY = moving ? event.sound.getY() : event.y;
            double soundZ = moving ? event.sound.getZ() : event.z;
            double dx = soundX - camPos.x;
            double dy = soundY - camPos.y + 0.5;
            double dz = soundZ - camPos.z;

            double distSq = dx * dx + dy * dy + dz * dz;
            double maxDist = Math.max(16.0, 16.0 * safeVolume(event.sound)) + 8.0;
            if (distSq > maxDist * maxDist) continue;

            int[] screenPos;
            if (distSq < 2.25 || event.sound.isRelative()) {
                screenPos = new int[]{screenWidth / 2, (int) (screenHeight * 0.72)};
            } else {
                screenPos = project(dx, dy, dz, sinYaw, cosYaw, sinPitch, cosPitch,
                    tanHalfHorizontalFov, tanHalfVerticalFov, screenWidth, screenHeight);
            }

            if (screenPos != null) {
                    int screenX = screenPos[0];
                    int screenY = screenPos[1];

                    String displayName = event.soundId;
                    if (displayName.contains(":")) {
                        displayName = displayName.substring(displayName.indexOf(':') + 1);
                    }

                    int textWidth = font.width(displayName);
                    int renderX = screenX - textWidth / 2;
                    int renderY = screenY;

                    boolean overlap;
                    int attempts = 0;
                    do {
                        overlap = false;
                        for (int[] rect : renderedRects) {
                            int rx = rect[0], ry = rect[1], rw = rect[2], rh = rect[3];
                            if (renderX < rx + rw && renderX + textWidth > rx && renderY < ry + rh && renderY + font.lineHeight > ry) {
                                overlap = true;
                                renderY += font.lineHeight + 2;
                                break;
                            }
                        }
                        attempts++;
                    } while (overlap && attempts < 15);

                    renderedRects.add(new int[]{renderX, renderY, textWidth, font.lineHeight});

                    int alphaInt = (int) (alpha * 255);
                    int color = (alphaInt << 24) | (toggling ? (event.toggleMuted ? 0xFF3333 : 0x55FF88) : 0x55FFFF);

                    context.drawString(font, displayName, renderX, renderY, color, true);
                    if (toggling) context.drawString(font, event.toggleMuted ? "×" : "+", screenX,
                            renderY + font.lineHeight + 2, color, true);
            }
        }
    }

    private static float safeVolume(SoundInstance sound) {
        try {
            return Math.max(1.0f, sound.getVolume());
        } catch (RuntimeException ignored) {
            return 1.0f;
        }
    }

    private static boolean isShortSound(String id) {
        return id.contains("step") || id.contains("fall");
    }

    private static int[] project(double dx, double dy, double dz,
                                 double sinYaw, double cosYaw,
                                 double sinPitch, double cosPitch,
                                 double tanHalfHorizontalFov, double tanHalfVerticalFov,
                                 int screenWidth, int screenHeight) {
        double cameraX = dx * cosYaw + dz * sinYaw;
        double horizontalDepth = dz * cosYaw - dx * sinYaw;
        double cameraY = dy * cosPitch + horizontalDepth * sinPitch;
        double cameraDepth = horizontalDepth * cosPitch - dy * sinPitch;
        if (cameraDepth <= 0.05) return null;

        double ndcX = -cameraX / (cameraDepth * tanHalfHorizontalFov);
        double ndcY = cameraY / (cameraDepth * tanHalfVerticalFov);
        if (ndcX < -1.2 || ndcX > 1.2 || ndcY < -1.2 || ndcY > 1.2) return null;
        return new int[]{
            (int) ((ndcX + 1.0) * 0.5 * screenWidth),
            (int) ((1.0 - ndcY) * 0.5 * screenHeight)
        };
    }
}
