package soundcontrol.render;

import soundcontrol.Compat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

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
        if (LINES.isEmpty()) LINES.add("§7" + net.minecraft.text.Text.translatable("text.soundcontrol.lookup.none").getString());
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

    public static void render(DrawContext context) {
        if (!enabled || LINES.isEmpty()) return;
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        int startX = context.getScaledWindowWidth() / 2 + 12;
        int startY = context.getScaledWindowHeight() / 2 - (LINES.size() * 10) / 2;
        String header = currentTarget.substring(currentTarget.indexOf(':') + 1);
        context.drawText(font, "§l" + header, startX, startY - 12, 0xFFFFAA00, true);
        for (int i = 0; i < LINES.size(); i++) {
            String line = LINES.get(i);
            int y = startY + i * 10;
            int colon = line.indexOf(": ");
            if (colon > 0) {
                String action = line.substring(0, colon + 2);
                String soundId = line.substring(colon + 2);
                soundId = soundId.substring(soundId.indexOf(':') + 1);
                context.drawText(font, action, startX, y, 0xFF88CCFF, true);
                context.drawText(font, soundId, startX + font.getWidth(action), y, 0xFFCCCCCC, true);
            } else {
                context.drawText(font, line, startX, y, 0xFFAAAAAA, true);
            }
        }
    }
}
