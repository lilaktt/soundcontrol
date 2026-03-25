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
        int yOffset = 5;

        synchronized (activeSounds) {
            Iterator<Map.Entry<String, Long>> iterator = activeSounds.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Long> entry = iterator.next();
                if (currentTime > entry.getValue()) {
                    iterator.remove();
                } else {
                    context.drawTextWithShadow(client.textRenderer, entry.getKey(), 5, yOffset, 0xFF00FF00);
                    yOffset += 10;
                }
            }
        }
    }
}