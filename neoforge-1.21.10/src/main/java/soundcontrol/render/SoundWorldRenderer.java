package soundcontrol.render;

import soundcontrol.Compat;
import soundcontrol.SoundConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class SoundWorldRenderer {
    public static boolean enabled = false;

    private static final CopyOnWriteArrayList<SoundEvent3D> ACTIVE = new CopyOnWriteArrayList<>();
    private static final long DISPLAY_DURATION_MS = 2500;
    private static final long FADE_DURATION_MS = 500;
    private static final long FORGET_AFTER_MS = 8000;

    static final class SoundEvent3D {
        final Object sound;
        final String soundId;
        long createdAt;
        long toggleAnimationAt;
        boolean toggleMuted;

        SoundEvent3D(Object sound, String soundId) {
            this.sound = sound;
            this.soundId = soundId;
            this.createdAt = System.currentTimeMillis();
        }
    }

    public static void recordSound(Object sound, String soundId) {
        double[] pos = Compat.soundPos(sound);
        for (SoundEvent3D existing : ACTIVE) {
            if (existing.sound == sound) { existing.createdAt = System.currentTimeMillis(); return; }
            if (existing.soundId.equals(soundId)) {
                double[] other = Compat.soundPos(existing.sound);
                if (Math.abs(other[0] - pos[0]) < 3.0 && Math.abs(other[1] - pos[1]) < 3.0 && Math.abs(other[2] - pos[2]) < 3.0) {
                    ACTIVE.remove(existing);
                    break;
                }
            }
        }
        ACTIVE.add(new SoundEvent3D(sound, soundId));
        while (ACTIVE.size() > 50) ACTIVE.remove(0);
    }

    public static boolean toggleSoundUnderCrosshair() {
        Compat.Target target = Compat.crosshairTarget();
        if (target == null || target.soundIds().isEmpty()) {
            Compat.overlay("message.soundcontrol.look_mute.none");
            return false;
        }
        long now = System.currentTimeMillis();
        Set<String> ids = new LinkedHashSet<>(target.soundIds());
        if (target.block()) {
            for (SoundEvent3D event : ACTIVE) {
                double[] pos = Compat.soundPos(event.sound);
                if (now - event.createdAt <= FORGET_AFTER_MS
                        && Math.abs(pos[0] - target.x()) <= 2 && Math.abs(pos[1] - target.y()) <= 2 && Math.abs(pos[2] - target.z()) <= 2) {
                    ids.add(event.soundId);
                }
            }
        }
        boolean muted = SoundConfig.toggleSoundsMuted(ids);
        for (SoundEvent3D event : ACTIVE) {
            if (ids.contains(event.soundId)) {
                event.toggleAnimationAt = now;
                event.toggleMuted = muted;
                event.createdAt = now;
                if (muted) Compat.stop(event.sound);
            }
        }
        if (muted) for (String id : ids) Compat.stopSound(id);
        Compat.overlay(muted ? "message.soundcontrol.look_mute.muted" : "message.soundcontrol.look_mute.unmuted", target.label());
        return true;
    }

    public static void stopNamespace(String namespace) {
        String prefix = namespace + ":";
        for (SoundEvent3D event : ACTIVE) if (event.soundId.startsWith(prefix)) Compat.stop(event.sound);
        for (String id : Compat.clientSoundIds()) if (id.startsWith(prefix)) Compat.stopSound(id);
    }

    private static boolean isShort(String soundId) {
        return soundId.contains("step") || soundId.contains("fall");
    }

    public static void render(GuiGraphics context) {
        if (!enabled || ACTIVE.isEmpty() || !Compat.inWorld()) return;
        Compat.Frame frame = Compat.frame();
        if (frame == null) return;

        long now = System.currentTimeMillis();
        Font font = Minecraft.getInstance().font;
        int screenWidth = context.guiWidth();
        int screenHeight = context.guiHeight();

        List<SoundEvent3D> expired = new ArrayList<>();
        for (SoundEvent3D sound : ACTIVE) {
            boolean active = !isShort(sound.soundId) && Compat.isPlaying(sound.sound);
            if (active) sound.createdAt = now;
            if (now - sound.createdAt > FORGET_AFTER_MS) expired.add(sound);
        }
        if (!expired.isEmpty()) ACTIVE.removeAll(expired);

        List<int[]> renderedRects = new ArrayList<>();
        for (SoundEvent3D sound : ACTIVE) {
            boolean shortSound = isShort(sound.soundId);
            long age = now - sound.createdAt;
            long remaining = (shortSound ? 800 : DISPLAY_DURATION_MS) - age;
            long fade = shortSound ? 300 : FADE_DURATION_MS;
            long toggleAge = now - sound.toggleAnimationAt;
            boolean toggling = sound.toggleAnimationAt > 0 && toggleAge < 700;
            if (!toggling && SoundConfig.getVolumeModifier(sound.soundId) <= 0) continue;
            float alpha = toggling ? 1f - (float) toggleAge / 700 : remaining < fade ? (float) remaining / fade : 1f;
            if (alpha <= 0.01f) continue;

            double[] pos = Compat.soundPos(sound.sound);
            double dx = pos[0] - frame.camX();
            double dy = pos[1] - frame.camY() + 0.5;
            double dz = pos[2] - frame.camZ();
            double maxDist = Math.max(16.0, 16.0 * Compat.soundVolume(sound.sound)) + 8.0;
            if (dx * dx + dy * dy + dz * dz > maxDist * maxDist) continue;

            Vector4f clip = frame.viewProj().transform(new Vector4f((float) dx, (float) dy, (float) dz, 1.0f));
            if (clip.w() <= 0.0f) continue;
            float ndcX = clip.x() / clip.w();
            float ndcY = clip.y() / clip.w();
            if (ndcX < -1.2f || ndcX > 1.2f || ndcY < -1.2f || ndcY > 1.2f) continue;

            int screenX = (int) ((ndcX + 1.0f) * 0.5f * screenWidth);
            int screenY = (int) ((1.0f - ndcY) * 0.5f * screenHeight);
            String displayName = sound.soundId.substring(sound.soundId.indexOf(':') + 1);
            int textWidth = font.width(displayName);
            int renderX = screenX - textWidth / 2;
            int renderY = screenY;

            for (int attempts = 0; attempts < 15; attempts++) {
                boolean overlap = false;
                for (int[] rect : renderedRects) {
                    if (renderX < rect[0] + rect[2] && renderX + textWidth > rect[0]
                            && renderY < rect[1] + rect[3] && renderY + font.lineHeight > rect[1]) {
                        overlap = true;
                        renderY += font.lineHeight + 2;
                        break;
                    }
                }
                if (!overlap) break;
            }
            renderedRects.add(new int[]{renderX, renderY, textWidth, font.lineHeight});

            int alphaInt = Math.max(5, (int) (alpha * 255));
            int color = (alphaInt << 24) | (toggling ? (sound.toggleMuted ? 0xFF3333 : 0x55FF88) : 0x55FFFF);
            context.drawString(font, displayName, renderX, renderY, color);
            if (toggling) context.drawString(font, sound.toggleMuted ? "×" : "+", screenX, renderY + font.lineHeight + 2, color);
        }
    }
}
