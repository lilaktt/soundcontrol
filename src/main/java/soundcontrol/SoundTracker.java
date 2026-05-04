package soundcontrol;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

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

    public static void render(GuiGraphicsExtractor context) {
        if (!showOverlay) return;

        long currentTime = System.currentTimeMillis();
        Minecraft client = Minecraft.getInstance();
        var font = client.font;

        int x = SoundConfig.getRadarX();
        int y = SoundConfig.getRadarY();
        if (y == -1) {
            y = context.guiHeight() / 2 - 50;
        }

        synchronized (activeSounds) {
            Iterator<Map.Entry<String, Long>> it = activeSounds.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Long> entry = it.next();
                long endTime = entry.getValue();
                if (currentTime > endTime) {
                    it.remove();
                    continue;
                }

                long remaining = endTime - currentTime;
                float alpha = Math.clamp(remaining / 500.0f, 0.0f, 1.0f);
                
                int alphaInt = (int) (alpha * 255);
                if (alphaInt < 10) alphaInt = 10;
                
                int color = (alphaInt << 24) | 0xFFFFFF;
                
                String soundId = entry.getKey();
                String displayName = soundId.substring(soundId.indexOf(':') + 1);
                
                context.text(font, "» " + displayName, x, y, color, true);
                y += 10;
                
                if (y > context.guiHeight() - 20) break;
            }
        }
    }
}
