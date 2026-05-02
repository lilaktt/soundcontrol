package soundcontrol;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

public class SoundWorldRenderer {
    public static boolean enabled = false;

    private static final CopyOnWriteArrayList<SoundEvent3D> activeSounds = new CopyOnWriteArrayList<>();
    private static final long DISPLAY_DURATION_MS = 3000;
    private static final long FADE_DURATION_MS = 800;

    private static CameraRenderState lastCameraState;
    private static Matrix4f lastProjectionMatrix;

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

    public static void updateMatrices(CameraRenderState state, Matrix4fc projection) {
        lastCameraState = state;
        if (lastProjectionMatrix == null) {
            lastProjectionMatrix = new Matrix4f();
        }
        lastProjectionMatrix.set(projection);
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
        if (!enabled || activeSounds.isEmpty() || lastCameraState == null || lastProjectionMatrix == null) return;

        long now = System.currentTimeMillis();
        Minecraft client = Minecraft.getInstance();
        Font font = client.font;

        double camX = lastCameraState.pos.x;
        double camY = lastCameraState.pos.y;
        double camZ = lastCameraState.pos.z;

        Matrix4f viewMatrix = lastCameraState.viewRotationMatrix;
        Matrix4f projectionMatrix = lastProjectionMatrix;

        int screenWidth = context.guiWidth();
        int screenHeight = context.guiHeight();

        activeSounds.removeIf(s -> now - s.createdAt > DISPLAY_DURATION_MS);

        for (SoundEvent3D sound : activeSounds) {
            long age = now - sound.createdAt;
            long remaining = DISPLAY_DURATION_MS - age;

            float alpha = remaining < FADE_DURATION_MS ? (float) remaining / FADE_DURATION_MS : 1.0f;
            if (alpha <= 0.01f) continue;

            double dx = sound.x - camX;
            double dy = sound.y - camY + 0.5;
            double dz = sound.z - camZ;

            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > 64 * 64) continue;

            Vector4f pos = new Vector4f((float) dx, (float) dy, (float) dz, 1.0f);
            viewMatrix.transform(pos);
            
            // Only draw if it is in front of the camera
            if (pos.z() > 0) continue; 
            
            projectionMatrix.transform(pos);

            if (pos.w() > 0.0f) {
                float ndcX = pos.x() / pos.w();
                float ndcY = pos.y() / pos.w();

                if (ndcX >= -1.0f && ndcX <= 1.0f && ndcY >= -1.0f && ndcY <= 1.0f) {
                    int screenX = (int) ((ndcX + 1.0f) * 0.5f * screenWidth);
                    int screenY = (int) ((1.0f - ndcY) * 0.5f * screenHeight);

                    String displayName = sound.soundId;
                    if (displayName.contains(":")) {
                        displayName = displayName.substring(displayName.indexOf(':') + 1);
                    }

                    int textWidth = font.width(displayName);
                    int renderX = screenX - textWidth / 2;
                    int renderY = screenY;

                    int alphaInt = (int) (alpha * 255);
                    int color = (alphaInt << 24) | 0x55FFFF;

                    context.text(font, displayName, renderX, renderY, color, true);
                }
            }
        }
    }
}
