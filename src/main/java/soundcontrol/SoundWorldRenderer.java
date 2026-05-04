package soundcontrol;

import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import net.minecraft.client.resources.sounds.SoundInstance;

public class SoundWorldRenderer {
  public static boolean enabled = false;

  private static final CopyOnWriteArrayList<SoundEvent3D> activeSounds =
      new CopyOnWriteArrayList<>();
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

    for (SoundEvent3D sound : activeSounds) {
      boolean active = client.getSoundManager().isActive(sound.sound);
      if (!active && sound.sound instanceof net.minecraft.client.resources.sounds.TickableSoundInstance tickable) {
        active = !tickable.isStopped();
      }
      if (active) {
        sound.createdAt = now;
      }

      if (now - sound.createdAt > DISPLAY_DURATION_MS) {
        activeSounds.remove(sound);
        continue;
      }
      
      long age = now - sound.createdAt;
      long remaining = DISPLAY_DURATION_MS - age;

      float alpha = remaining < FADE_DURATION_MS ? (float) remaining / FADE_DURATION_MS : 1.0f;
      if (alpha <= 0.01f) continue;

      double dx = sound.sound.getX() - camPos.x;
      double dy = sound.sound.getY() - camPos.y + 0.5; 
      double dz = sound.sound.getZ() - camPos.z;

      double distSq = dx * dx + dy * dy + dz * dz;
      float volume = 1.0f;
      try {
          volume = sound.sound.getVolume();
      } catch (Exception e) {}
      double maxDist = Math.max(16.0, 16.0 * volume) + 8.0;
      if (distSq > maxDist * maxDist) continue;

      Vector4f pos = new Vector4f((float) dx, (float) dy, (float) dz, 1.0f);
      projectionMatrix.transform(pos);

      if (pos.w() > 0.0f) {
        float ndcX = pos.x() / pos.w();
        float ndcY = pos.y() / pos.w();

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
              if (renderX < rx + rw
                  && renderX + textWidth > rx
                  && renderY < ry + rh
                  && renderY + font.lineHeight > ry) {
                overlap = true;
                renderY += font.lineHeight + 2;
                break;
              }
            }
            attempts++;
          } while (overlap && attempts < 15);

          renderedRects.add(new int[] {renderX, renderY, textWidth, font.lineHeight});

          int alphaInt = (int) (alpha * 255);
          int color = (alphaInt << 24) | 0x55FFFF;

          context.text(font, displayName, renderX, renderY, color, true);
        }
      }
    }
  }
}
