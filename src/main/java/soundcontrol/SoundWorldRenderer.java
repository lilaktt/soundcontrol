package soundcontrol;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

public class SoundWorldRenderer {
    public static boolean enabled = false;

    private static final CopyOnWriteArrayList<SoundEvent3D> activeSounds = new CopyOnWriteArrayList<>();
    private static final long DISPLAY_DURATION_MS = 3000;
    private static final long FADE_DURATION_MS = 800;

    public static class SoundEvent3D {
        public final String soundId;
        public final double x, y, z;
        public final long createdAt;

        public SoundEvent3D(String soundId, double x, double y, double z) {
            this.soundId = soundId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.createdAt = System.currentTimeMillis();
        }
    }

    public static void recordSound(String soundId, double x, double y, double z) {
        if (!enabled) return;
        if (SoundConfig.getVolumeModifier(soundId) <= 0.0f) return;

        Iterator<SoundEvent3D> it = activeSounds.iterator();
        while (it.hasNext()) {
            SoundEvent3D existing = it.next();
            if (existing.soundId.equals(soundId)
                    && Math.abs(existing.x - x) < 0.5
                    && Math.abs(existing.y - y) < 0.5
                    && Math.abs(existing.z - z) < 0.5) {
                activeSounds.remove(existing);
                break;
            }
        }

        activeSounds.add(new SoundEvent3D(soundId, x, y, z));
        while (activeSounds.size() > 50) {
            activeSounds.remove(0);
        }
    }

    public static void render(GuiGraphicsExtractor context) {
        if (!enabled || activeSounds.isEmpty()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.gameRenderer == null || client.gameRenderer.getMainCamera() == null) return;

        long now = System.currentTimeMillis();
        Font font = client.font;

        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 camPos = camera.position();
        Matrix4f projectionMatrix = camera.getViewRotationProjectionMatrix(new Matrix4f());

        int screenWidth = context.guiWidth();
        int screenHeight = context.guiHeight();

        java.util.List<int[]> renderedRects = new java.util.ArrayList<>();

        activeSounds.removeIf(s -> now - s.createdAt > DISPLAY_DURATION_MS);

        for (SoundEvent3D sound : activeSounds) {
            long age = now - sound.createdAt;
            long remaining = DISPLAY_DURATION_MS - age;

            float alpha = remaining < FADE_DURATION_MS ? (float) remaining / FADE_DURATION_MS : 1.0f;
            if (alpha <= 0.01f) continue;

            double dx = sound.x - camPos.x;
            double dy = sound.y - camPos.y + 0.5; // Slightly above source
            double dz = sound.z - camPos.z;

            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > 64 * 64) continue; // Don't render too far away

            Vector4f pos = new Vector4f((float) dx, (float) dy, (float) dz, 1.0f);
            projectionMatrix.transform(pos);

            // w > 0 means the point is in front of the camera
            if (pos.w() > 0.0f) {
                float ndcX = pos.x() / pos.w();
                float ndcY = pos.y() / pos.w();

                // Check if point is on screen
                if (ndcX >= -1.2f && ndcX <= 1.2f && ndcY >= -1.2f && ndcY <= 1.2f) {
                    int screenX = (int) ((ndcX + 1.0f) * 0.5f * screenWidth);
                    int screenY = (int) ((1.0f - ndcY) * 0.5f * screenHeight);

                    String displayName = sound.soundId;
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
                    int color = (alphaInt << 24) | 0x55FFFF;

                    context.text(font, displayName, renderX, renderY, color, true);
                }
            }
        }
    }
}
