package soundcontrol.render;

import soundcontrol.SoundConfig;
import soundcontrol.SoundControl;
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
  private static final long DISPLAY_DURATION_MS = 2500;
  private static final long FADE_DURATION_MS = 500;

  public static class SoundEvent3D {
    public final SoundInstance sound;
    public final String soundId;
    public long createdAt;
    public long toggleAnimationAt;
    public boolean toggleMuted;

    public SoundEvent3D(SoundInstance sound, String soundId) {
      this.sound = sound;
      this.soundId = soundId;
      this.createdAt = System.currentTimeMillis();
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

  public static boolean toggleSoundUnderCrosshair(Minecraft client) {
    if (client.player == null || client.level == null) return false;
    if (!(client.hitResult instanceof net.minecraft.world.phys.BlockHitResult blockHit
        && blockHit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK)
        && !(client.hitResult instanceof net.minecraft.world.phys.EntityHitResult entityHit
        && entityHit.getEntity() instanceof net.minecraft.world.entity.Mob)) {
      client.player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.soundcontrol.look_mute.none"));
      return false;
    }
    java.util.Set<String> ids = new java.util.LinkedHashSet<>();
    String label = "";
    long now = System.currentTimeMillis();
    if (client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
        && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
      var state = client.level.getBlockState(hit.getBlockPos());
      var sounds = state.getSoundType();
      ids.add(sounds.getBreakSound().location().toString());
      ids.add(sounds.getStepSound().location().toString());
      ids.add(sounds.getPlaceSound().location().toString());
      ids.add(sounds.getHitSound().location().toString());
      ids.add(sounds.getFallSound().location().toString());
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
      var entityId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
      // Select the entire registered family, even when the mob has not made a sound.
      // Keep the namespace and trailing dot so other mods and similarly named mobs are excluded.
      String prefix = entityId.getNamespace() + ":entity." + entityId.getPath() + ".";
      for (var id : net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.keySet()) {
          if (id.toString().startsWith(prefix)) ids.add(id.toString());
      }
      // Resource packs may also provide sound events outside the game registry.
      for (var id : client.getSoundManager().getAvailableSounds()) {
          if (id.toString().startsWith(prefix)) ids.add(id.toString());
      }
      label = target.getName().getString();
    }
    if (ids.isEmpty()) {
      client.player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.soundcontrol.look_mute.none"));
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
        var parsed = net.minecraft.resources.Identifier.tryParse(id);
        if (parsed != null) client.getSoundManager().stop(parsed, null);
      }
    }
        client.player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(
        muted ? "message.soundcontrol.look_mute.muted" : "message.soundcontrol.look_mute.unmuted", label));
    return true;
  }

  public static void render(GuiGraphicsExtractor context) {
    if (!enabled || activeSounds.isEmpty()) return;
    Minecraft client = Minecraft.getInstance();
    if (client.gameRenderer == null || client.gameRenderer.mainCamera() == null) return;

    long now = System.currentTimeMillis();
    Font font = client.font;

    Camera camera = client.gameRenderer.mainCamera();
    Vec3 camPos = camera.position();
    Matrix4f projectionMatrix = camera.getViewRotationProjectionMatrix(new Matrix4f());

    int screenWidth = context.guiWidth();
    int screenHeight = context.guiHeight();

    java.util.List<SoundEvent3D> toRemove = new java.util.ArrayList<>();
    for (SoundEvent3D sound : activeSounds) {
      boolean active = client.getSoundManager().isActive(sound.sound);
      if (!active && sound.sound instanceof net.minecraft.client.resources.sounds.TickableSoundInstance tickable) {
        active = !tickable.isStopped();
      }
      boolean isShortSound = sound.soundId.contains("step") || sound.soundId.contains("fall");
      if (isShortSound) active = false;
      if (active) sound.createdAt = now;
      long age = now - sound.createdAt;
      long duration = isShortSound ? 800 : DISPLAY_DURATION_MS;
      if (age > 8000) toRemove.add(sound);
    }
    if (!toRemove.isEmpty()) activeSounds.removeAll(toRemove);

    java.util.List<int[]> renderedRects = new java.util.ArrayList<>();
    for (SoundEvent3D sound : activeSounds) {
      boolean isShortSound = sound.soundId.contains("step") || sound.soundId.contains("fall");
      long age = now - sound.createdAt;
      long duration = isShortSound ? 800 : DISPLAY_DURATION_MS;
      long fade = isShortSound ? 300 : FADE_DURATION_MS;
      long remaining = duration - age;

      long toggleAge = now - sound.toggleAnimationAt;
      boolean toggling = sound.toggleAnimationAt > 0 && toggleAge < 700;
      if (SoundConfig.getVolumeModifier(sound.soundId) <= 0 && !toggling) continue;
      float alpha = toggling ? 1f - (float) toggleAge / 700
          : remaining < fade ? (float) remaining / fade : 1f;
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
          int color = (alphaInt << 24) | (toggling ? (sound.toggleMuted ? 0xFF3333 : 0x55FF88) : 0x55FFFF);

          context.text(font, displayName, renderX, renderY, color, true);
          if (toggling) context.text(font, sound.toggleMuted ? "×" : "+", screenX,
              renderY + font.lineHeight + 2, color, true);
        }
      }
    }
  }
}

