package soundcontrol;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;

public class SoundLookupRenderer {
    public static boolean enabled = false;
    private static final List<String> currentSounds = new ArrayList<>();
    private static String currentTarget = "";

    public static void tick(Minecraft client) {
        if (!enabled || client.level == null || client.player == null) {
            currentSounds.clear();
            currentTarget = "";
            return;
        }

        HitResult hit = client.hitResult;
        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            currentSounds.clear();
            currentTarget = "";
            return;
        }

        if (hit instanceof BlockHitResult blockHit) {
            BlockState state = client.level.getBlockState(blockHit.getBlockPos());
            Block block = state.getBlock();
            String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();

            if (!blockId.equals(currentTarget)) {
                currentTarget = blockId;
                currentSounds.clear();

                SoundType group = state.getSoundType();
                addSoundGroupSounds(group);

                String blockPath = blockId.contains(":") ? blockId.substring(blockId.indexOf(':') + 1) : blockId;
                String soundPrefix = "block." + blockPath + ".";

                for (Identifier soundId : client.getSoundManager().getAvailableSounds()) {
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
            String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();

            if (!entityId.equals(currentTarget)) {
                currentTarget = entityId;
                currentSounds.clear();

                String entityPath = entityId.contains(":") ? entityId.substring(entityId.indexOf(':') + 1) : entityId;
                String soundPrefix = "entity." + entityPath + ".";

                for (Identifier soundId : client.getSoundManager().getAvailableSounds()) {
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

    private static void addSoundGroupSounds(SoundType group) {
        addIfValid("Break: " + BuiltInRegistries.SOUND_EVENT.getKey(group.getBreakSound()));
        addIfValid("Step: " + BuiltInRegistries.SOUND_EVENT.getKey(group.getStepSound()));
        addIfValid("Place: " + BuiltInRegistries.SOUND_EVENT.getKey(group.getPlaceSound()));
        addIfValid("Hit: " + BuiltInRegistries.SOUND_EVENT.getKey(group.getHitSound()));
        addIfValid("Fall: " + BuiltInRegistries.SOUND_EVENT.getKey(group.getFallSound()));
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

    public static void render(GuiGraphicsExtractor context) {
        if (!enabled || currentSounds.isEmpty()) return;

        Minecraft client = Minecraft.getInstance();
        Font font = client.font;

        int centerX = context.guiWidth() / 2;
        int centerY = context.guiHeight() / 2;

        int startX = centerX + 12;
        int startY = centerY - (currentSounds.size() * 10) / 2;

        String header = currentTarget.contains(":") ? currentTarget.substring(currentTarget.indexOf(':') + 1) : currentTarget;
        context.text(font, "\u00A7e\u00A7l" + header, startX, startY - 12, 0xFFFFAA00);

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
                context.text(font, action, startX, y, 0xFF88CCFF);
                context.text(font, soundId, startX + font.width(action), y, 0xFFCCCCCC);
            } else {
                context.text(font, line, startX, y, 0xFFAAAAAA);
            }
        }
    }
}
