package soundcontrol;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

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

    public static void render(GuiGraphics context) {
        if (!showOverlay) return;

        Minecraft client = Minecraft.getInstance();
        var font = client.font;
        long currentTime = System.currentTimeMillis();

        int x = 5;
        int y = 5;

        synchronized (activeSounds) {
            Iterator<Map.Entry<String, Long>> iterator = activeSounds.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Long> entry = iterator.next();
                if (currentTime > entry.getValue()) {
                    iterator.remove();
                } else {
                    context.drawString(font, "» " + entry.getKey().substring(entry.getKey().indexOf(':') + 1), x, y, 0xFF00FF00, true);
                    y += 10;
                }
            }
        }
    }
}
