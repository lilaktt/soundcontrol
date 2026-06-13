package soundcontrol;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class SoundTracker {
    public static int overlayMode = 0;
    private static final Map<String, Long> activeSounds = new LinkedHashMap<>();

    public static void cycleOverlayMode() {
        overlayMode = (overlayMode + 1) % 3;
        if (overlayMode == 1) {
            SoundWorldRenderer.enabled = true;
        } else if (overlayMode == 2) {
            SoundWorldRenderer.enabled = false;
        } else {
            SoundWorldRenderer.enabled = false;
        }
    }

    public static void recordSound(String soundId) {
        if (overlayMode != 2) return;
        if (SoundConfig.getVolumeModifier(soundId) <= 0.0f) return;

        synchronized (activeSounds) {
            activeSounds.remove(soundId);
            activeSounds.put(soundId, System.currentTimeMillis() + 3000);
        }
    }

    public static void render(DrawContext context) {
        if (overlayMode != 2) return;

        MinecraftClient client = MinecraftClient.getInstance();
        long currentTime = System.currentTimeMillis();

        int x = SoundConfig.getRadarX();
        int y = SoundConfig.getRadarY();
        if (y == -1) {
            y = context.getScaledWindowHeight() / 2 - 50;
        }

        synchronized (activeSounds) {
            Iterator<Map.Entry<String, Long>> iterator = activeSounds.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Long> entry = iterator.next();
                long endTime = entry.getValue();
                if (currentTime > endTime) {
                    iterator.remove();
                    continue;
                }

                long remaining = endTime - currentTime;
                float alpha = Math.max(0.0f, Math.min(1.0f, remaining / 500.0f));
                
                int alphaInt = (int) (alpha * 255);
                if (alphaInt < 10) alphaInt = 10;
                
                int color = (alphaInt << 24) | 0xFFFFFF;
                
                String soundId = entry.getKey();
                String displayName = soundId.substring(soundId.indexOf(':') + 1);
                
                context.drawTextWithShadow(client.textRenderer, "» " + displayName, x, y, color);
                y += 10;
                
                if (y > context.getScaledWindowHeight() - 20) break;
            }
        }
    }
}
