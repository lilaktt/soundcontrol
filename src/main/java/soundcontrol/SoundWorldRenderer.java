package soundcontrol;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

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
        public long createdAt;

        public SoundEvent3D(SoundInstance sound, String soundId) {
            this.sound = sound;
            this.soundId = soundId;
            this.createdAt = System.currentTimeMillis();
        }
    }

    public static void recordSound(SoundInstance sound, String soundId) {
        if (SoundConfig.getVolumeModifier(soundId) <= 0.0f) return;

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

    public static void render(DrawContext context) {
        if (!enabled || activeSounds.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.gameRenderer == null || client.gameRenderer.getCamera() == null) return;

        long now = System.currentTimeMillis();
        TextRenderer font = client.textRenderer;
        Camera camera = client.gameRenderer.getCamera();
        Vec3d camPos = camera.getPos();

        Quaternionf rotation = camera.getRotation();
        Matrix4f viewMatrix = new Matrix4f().rotation(rotation.conjugate(new Quaternionf()));
        Matrix4f projMatrix = client.gameRenderer.getBasicProjectionMatrix(client.options.getFov().getValue().doubleValue());
        Matrix4f viewProjMatrix = new Matrix4f(projMatrix).mul(viewMatrix);

        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();

        java.util.List<int[]> renderedRects = new java.util.ArrayList<>();

        for (SoundEvent3D event : activeSounds) {
            boolean active = client.getSoundManager().isPlaying(event.sound);
            if (!active && event.sound instanceof TickableSoundInstance tickable) {
                active = !tickable.isDone();
            }

            if (active) {
                event.createdAt = now;
            }

            if (now - event.createdAt > DISPLAY_DURATION_MS) {
                activeSounds.remove(event);
                continue;
            }

            long age = now - event.createdAt;
            long remaining = DISPLAY_DURATION_MS - age;
            float alpha = remaining < FADE_DURATION_MS ? (float) remaining / FADE_DURATION_MS : 1.0f;
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

                    int alphaInt = (int) (alpha * 255);
                    int color = (alphaInt << 24) | 0x55FFFF;

                    context.drawText(font, displayName, renderX, renderY, color, true);
                }
            }
        }
    }
}
