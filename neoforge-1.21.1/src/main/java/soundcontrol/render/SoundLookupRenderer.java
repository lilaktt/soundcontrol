package soundcontrol.render;

import soundcontrol.Compat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

public class SoundLookupRenderer {
    public static boolean enabled = false;
    private static final List<String> LINES = new ArrayList<>();
    private static String currentTarget = "";
    private static long nextRefresh;

    public static void tick() {
        if (!enabled || !Compat.inWorld()) { clear(); return; }
        long now = System.currentTimeMillis();
        Compat.Target target = Compat.crosshairTarget();
        if (target == null) { clear(); return; }
        if (target.id().equals(currentTarget) && now < nextRefresh) return;
        currentTarget = target.id();
        nextRefresh = now + 500;
        LINES.clear();
        for (String soundId : target.soundIds()) LINES.add(action(soundId) + ": " + soundId);
        if (LINES.isEmpty()) LINES.add("§7" + net.minecraft.network.chat.Component.translatable("text.soundcontrol.lookup.none").getString());
    }

    private static void clear() {
        LINES.clear();
        currentTarget = "";
        nextRefresh = 0;
    }

    private static String action(String soundId) {
        String path = soundId.substring(soundId.indexOf(':') + 1);
        int separator = Math.max(path.lastIndexOf('.'), path.lastIndexOf('/'));
        String action = separator >= 0 ? path.substring(separator + 1) : path;
        return action.isEmpty() ? "Sound" : Character.toUpperCase(action.charAt(0)) + action.substring(1);
    }

    public static void render(GuiGraphics context) {
        if (!enabled || LINES.isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        int startX = context.guiWidth() / 2 + 12;
        int startY = context.guiHeight() / 2 - (LINES.size() * 10) / 2;
        String header = currentTarget.substring(currentTarget.indexOf(':') + 1);
        context.drawString(font, "§l" + header, startX, startY - 12, 0xFFFFAA00);
        for (int i = 0; i < LINES.size(); i++) {
            String line = LINES.get(i);
            int y = startY + i * 10;
            int colon = line.indexOf(": ");
            if (colon > 0) {
                String action = line.substring(0, colon + 2);
                String soundId = line.substring(colon + 2);
                soundId = soundId.substring(soundId.indexOf(':') + 1);
                context.drawString(font, action, startX, y, 0xFF88CCFF);
                context.drawString(font, soundId, startX + font.width(action), y, 0xFFCCCCCC);
            } else {
                context.drawString(font, line, startX, y, 0xFFAAAAAA);
            }
        }
    }
}
