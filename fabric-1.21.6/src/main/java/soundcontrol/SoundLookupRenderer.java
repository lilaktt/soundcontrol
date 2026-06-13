package soundcontrol;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.ArrayList;
import java.util.List;

public class SoundLookupRenderer {
    public static boolean enabled = false;
    private static final List<String> currentSounds = new ArrayList<>();
    private static String currentTarget = "";

    public static void tick(MinecraftClient client) {
        if (!enabled || client.world == null || client.player == null) {
            currentSounds.clear();
            currentTarget = "";
            return;
        }

        HitResult hit = client.crosshairTarget;
        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            currentSounds.clear();
            currentTarget = "";
            return;
        }

        if (hit instanceof BlockHitResult blockHit) {
            BlockState state = client.world.getBlockState(blockHit.getBlockPos());
            Block block = state.getBlock();
            String blockId = Registries.BLOCK.getId(block).toString();

            if (!blockId.equals(currentTarget)) {
                currentTarget = blockId;
                currentSounds.clear();

                // Always show the sound group sounds (break, step, place, hit, fall)
                BlockSoundGroup group = state.getSoundGroup();
                addSoundGroupSounds(group);

                // Also add any block-specific sounds from registry
                String blockPath = blockId.contains(":") ? blockId.substring(blockId.indexOf(':') + 1) : blockId;
                String soundPrefix = "block." + blockPath + ".";

                for (Identifier soundId : client.getSoundManager().getKeys()) {
                    String path = soundId.getPath();
                    if (path.startsWith(soundPrefix)) {
                        String action = path.substring(soundPrefix.length());
                        String entry = capitalize(action) + ": " + soundId;
                        if (!currentSounds.contains(entry)) {
                            addIfValid(entry);
                        }
                    }
                }

                if (currentSounds.isEmpty()) {
                    currentSounds.add("\u00A77No specific sounds");
                }
            }
        } else if (hit instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            String entityId = Registries.ENTITY_TYPE.getId(entity.getType()).toString();

            if (!entityId.equals(currentTarget)) {
                currentTarget = entityId;
                currentSounds.clear();

                String entityPath = entityId.contains(":") ? entityId.substring(entityId.indexOf(':') + 1) : entityId;
                String soundPrefix = "entity." + entityPath + ".";

                for (Identifier soundId : client.getSoundManager().getKeys()) {
                    String path = soundId.getPath();
                    if (path.startsWith(soundPrefix)) {
                        String action = path.substring(soundPrefix.length());
                        addIfValid(capitalize(action) + ": " + soundId);
                    }
                }

                if (currentSounds.isEmpty()) {
                    currentSounds.add("\u00A77No registered sounds");
                }
            }
        } else {
            currentSounds.clear();
            currentTarget = "";
        }
    }

    private static void addSoundGroupSounds(BlockSoundGroup group) {
        addIfValid("Break: " + Registries.SOUND_EVENT.getId(group.getBreakSound()));
        addIfValid("Step: " + Registries.SOUND_EVENT.getId(group.getStepSound()));
        addIfValid("Place: " + Registries.SOUND_EVENT.getId(group.getPlaceSound()));
        addIfValid("Hit: " + Registries.SOUND_EVENT.getId(group.getHitSound()));
        addIfValid("Fall: " + Registries.SOUND_EVENT.getId(group.getFallSound()));
    }

    private static void addIfValid(String line) {
        if (line != null && !line.isEmpty() && !line.endsWith("null")) {
            currentSounds.add(line);
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static void render(DrawContext context) {
        if (!enabled || currentSounds.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer font = client.textRenderer;

        int centerX = context.getScaledWindowWidth() / 2;
        int centerY = context.getScaledWindowHeight() / 2;

        int startX = centerX + 12;
        int startY = centerY - (currentSounds.size() * 10) / 2;

        String header = currentTarget.contains(":") ? currentTarget.substring(currentTarget.indexOf(':') + 1) : currentTarget;
        context.drawTextWithShadow(font, "\u00A7e\u00A7l" + header, startX, startY - 12, 0xFFFFAA00);

        for (int i = 0; i < currentSounds.size(); i++) {
            String line = currentSounds.get(i);
            int y = startY + i * 10;

            int colonIdx = line.indexOf(": ");
            if (colonIdx > 0) {
                String action = line.substring(0, colonIdx + 2);
                String soundId = line.substring(colonIdx + 2);
                if (soundId.contains(":")) {
                    soundId = soundId.substring(soundId.indexOf(':') + 1);
                }
                context.drawTextWithShadow(font, action, startX, y, 0xFF88CCFF);
                context.drawTextWithShadow(font, soundId, startX + font.getWidth(action), y, 0xFFCCCCCC);
            } else {
                context.drawTextWithShadow(font, line, startX, y, 0xFFAAAAAA);
            }
        }
    }
}
