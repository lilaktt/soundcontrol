package soundcontrol;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class SoundTracker {
    public static boolean showOverlay = false;
    private static final Map<String, Long> activeSounds = new LinkedHashMap<>();

    public static void recordSound(String soundId) {
        if (!showOverlay) return;
        if (SoundConfig.getVolumeModifier(soundId) <= 0.0f) return;

        synchronized (activeSounds) {
            activeSounds.remove(soundId);
            activeSounds.put(soundId, System.currentTimeMillis() + 3000);
        }
    }

    public static void render(DrawContext context) {
        if (!showOverlay) return;

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
